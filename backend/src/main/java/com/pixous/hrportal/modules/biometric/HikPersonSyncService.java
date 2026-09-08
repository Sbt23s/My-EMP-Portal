package com.pixous.hrportal.modules.biometric;

import com.pixous.hrportal.modules.biometric.hik.HikClient;
import com.pixous.hrportal.modules.biometric.hik.HikPerson;
import com.pixous.hrportal.modules.biometric.hik.HikPersonApi;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps {@code hik_person_map} in step with the people on the terminal.
 *
 * <p>The mapping is the whole reason a punch can be attributed to anybody: the
 * pushed event names a {@code personId}, this table says which employee that
 * is. Without a current mapping every punch by a newly added employee lands
 * unmatched, so this runs on a schedule rather than only on demand.
 *
 * <p>The join is the employee number — {@code users.employee_code} on this side
 * and {@code personCode} on Hikvision's. Nothing else is reliable: names are
 * duplicated and edited, and Hikvision's own identifier means nothing here
 * until this table gives it a meaning.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HikPersonSyncService {

    private final HikClient client;
    private final HikPersonApi personApi;
    private final HikPersonMapRepository mapRepository;
    private final UserRepository userRepository;

    /**
     * Reconciles every person on the Hikvision account against the employee
     * list, and reports what happened.
     *
     * <p>Runs without a signed-in user, so the tenant filter is not active and
     * {@code findByEmployeeCode} sees every company. That is what this needs —
     * one Hikvision account can carry several companies' terminals — and it is
     * also why the company is taken from the matched employee rather than
     * assumed: writing a fixed company onto a mapping would let one tenant's
     * punches be read by another, which is the isolation rule this portal is
     * built on.
     */
    @Transactional
    public SyncResult sync() {
        if (!client.isEnabled()) {
            log.debug("Hikvision person sync skipped: the integration is not configured");
            return SyncResult.skipped();
        }

        List<HikPerson> people = personApi.listAll();
        if (people.isEmpty()) {
            /*
             * Nothing came back. This is reported rather than acted on: an
             * empty list is indistinguishable from a platform that returned
             * nothing because of a fault, and deleting every mapping on the
             * strength of it would unattribute the entire workforce's punches.
             */
            log.warn("Hikvision returned no people; leaving {} existing mappings alone",
                    mapRepository.count());
            return new SyncResult(true, 0, 0, 0, 0, 0);
        }

        // Employee numbers are compared case-insensitively and trimmed: they
        // are typed into Hik-Connect by hand, and "emp001" against "EMP001"
        // would otherwise silently fail to match.
        Map<String, User> byCode = new HashMap<>();
        for (User u : userRepository.findAll()) {
            String code = normalise(u.getEmployeeCode());
            if (code != null) {
                byCode.put(code, u);
            }
        }

        int matched = 0;
        int created = 0;
        int updated = 0;
        int unmatched = 0;

        for (HikPerson person : people) {
            String code = normalise(person.personCode());
            User user = code == null ? null : byCode.get(code);

            if (user == null) {
                /*
                 * On the terminal but not an employee here, or an employee
                 * number that was never set on the Hikvision side. Counted and
                 * logged, not created: a mapping without an employee has
                 * nothing to point at, and inventing one would attribute
                 * somebody's punches to the wrong person.
                 */
                unmatched++;
                log.debug("Hikvision person {} ({}) matches no employee number",
                        person.displayName(), person.personCode());
                continue;
            }

            matched++;
            Optional<HikPersonMap> existing = mapRepository.findByHikPersonId(person.personId());

            if (existing.isPresent()) {
                HikPersonMap row = existing.get();
                boolean changed = false;

                /*
                 * The employee behind a terminal identity changed. Rare and
                 * worth a warning rather than a silent overwrite -- it means
                 * an employee number was reassigned, and every punch recorded
                 * before now was attributed to the previous holder.
                 */
                if (!row.getUserId().equals(user.getId())) {
                    log.warn("Hikvision person {} moved from employee {} to {}; "
                                    + "punches already recorded stay with the previous employee",
                            person.personId(), row.getUserId(), user.getId());
                    row.setUserId(user.getId());
                    changed = true;
                }
                if (!java.util.Objects.equals(row.getPersonCode(), person.personCode())) {
                    row.setPersonCode(person.personCode());
                    changed = true;
                }
                if (!java.util.Objects.equals(row.getCompanyId(), user.getCompanyId())) {
                    row.setCompanyId(user.getCompanyId());
                    changed = true;
                }
                if (changed) {
                    row.setUpdatedAt(LocalDateTime.now());
                    mapRepository.save(row);
                    updated++;
                }
            } else {
                /*
                 * A new terminal identity for an employee who already has one
                 * cannot be stored -- uk_hik_user allows exactly one. Checked
                 * here so it reports as a named problem rather than as a
                 * constraint violation that aborts the whole sync.
                 */
                if (mapRepository.findByUserId(user.getId()).isPresent()) {
                    log.warn("Employee {} already has a Hikvision identity; "
                                    + "ignoring the second one ({})",
                            user.getId(), person.personId());
                    unmatched++;
                    matched--;
                    continue;
                }
                HikPersonMap row = new HikPersonMap();
                row.setUserId(user.getId());
                row.setCompanyId(user.getCompanyId());
                row.setHikPersonId(person.personId());
                row.setPersonCode(person.personCode());
                mapRepository.save(row);
                created++;
            }
        }

        SyncResult result = new SyncResult(true, people.size(), matched, created, updated, unmatched);
        log.info("Hikvision person sync: {}", result.summary());
        return result;
    }

    /**
     * Refreshes the cached face and fingerprint flags for the mapped people.
     *
     * <p>Separate from {@link #sync()} and deliberately not folded into it:
     * this costs one request per person against a budget of five per second, so
     * a hundred employees is half a minute of calls. It answers a question
     * ("can this person actually punch?") that changes rarely, so it runs on
     * its own, slower schedule.
     */
    @Transactional
    public int refreshEnrolment() {
        if (!client.isEnabled()) {
            return 0;
        }
        int updated = 0;
        for (HikPersonMap row : mapRepository.findAll()) {
            HikPersonApi.Enrolment enrolment = personApi.enrolment(row.getHikPersonId());
            if (!enrolment.known()) {
                // The lookup failed. Leaving the previous answer in place is
                // better than replacing it with a false "not enrolled", which
                // would tell an employee they cannot punch when they can.
                continue;
            }
            row.setFaceEnrolled(enrolment.face());
            row.setFingerprintEnrolled(enrolment.fingerprint());
            row.setSyncedAt(LocalDateTime.now());
            row.setUpdatedAt(LocalDateTime.now());
            mapRepository.save(row);
            updated++;
        }
        log.info("Refreshed Hikvision enrolment for {} people", updated);
        return updated;
    }

    private static String normalise(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    /**
     * What one sync did.
     *
     * <p>{@code ran} exists because every other field is zero in two entirely
     * different situations: the integration is not configured, and the terminal
     * genuinely has nobody on it. Reported identically, an administrator who
     * had not yet supplied the credentials would read "0 on the terminal" as an
     * empty device and go looking at the hardware.
     *
     * @param ran              whether the sync actually reached Hikvision
     * @param peopleOnTerminal how many Hikvision returned
     * @param matched          how many resolved to an employee
     * @param created          new mappings written
     * @param updated          existing mappings corrected
     * @param unmatched        people with no employee number this portal knows
     */
    public record SyncResult(boolean ran, int peopleOnTerminal, int matched,
                             int created, int updated, int unmatched) {

        static SyncResult skipped() {
            return new SyncResult(false, 0, 0, 0, 0, 0);
        }

        public String summary() {
            if (!ran) {
                return "Hikvision is not configured, so nothing was synced";
            }
            return peopleOnTerminal + " on the terminal, " + matched + " matched ("
                    + created + " new, " + updated + " changed), "
                    + unmatched + " unmatched";
        }
    }
}
