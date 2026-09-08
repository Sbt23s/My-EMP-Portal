package com.pixous.hrportal.modules.biometric;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ErrorCode;
import com.pixous.hrportal.modules.biometric.dto.PersonMappingRow;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Who on the terminal is who in the portal, as a person decides it.
 *
 * <p>The automatic sync joins the two sides on their shared employee number,
 * and on this account it cannot: Hikvision will not let an employee number be
 * changed once a person exists ({@code /persons/update} answers OPEN000010;
 * §5.8.6 says "The employee No. cannot be edited"). The terminal was set up
 * with "001" and this portal uses "PIX-E001", and no rule joins those safely —
 * "001" also matches "ADM0001", who is somebody else.
 *
 * <p>So the decision is a person's, made once per employee, and recorded with
 * {@code manual} so the hourly sync does not undo it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonMappingService {

    private final HikClient client;
    private final HikPersonApi personApi;
    private final HikPersonMapRepository mapRepository;
    private final BiometricEventRepository eventRepository;
    private final UserRepository userRepository;

    /**
     * Every person on the terminal, with whoever they are mapped to.
     *
     * <p>Read live from Hikvision rather than from the mapping table, because
     * the question the screen answers is "who is on the terminal and who are
     * they here" — and somebody added to the terminal this morning has no
     * mapping row at all yet. Reading the table would hide exactly the people
     * who need attention.
     */
    @Transactional(readOnly = true)
    public List<PersonMappingRow> list() {
        if (!client.isEnabled()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Hikvision is not configured. Set HIKVISION_ENABLED and supply "
                            + "the app key and secret key on the server.");
        }

        List<HikPerson> people = personApi.listAll();

        Map<String, HikPersonMap> byPersonId = new HashMap<>();
        for (HikPersonMap m : mapRepository.findAllByOrderByIdDesc()) {
            byPersonId.put(m.getHikPersonId(), m);
        }

        Map<Long, User> users = new HashMap<>();
        for (User u : userRepository.findAll()) {
            users.put(u.getId(), u);
        }

        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<PersonMappingRow> out = new ArrayList<>();
        for (HikPerson p : people) {
            HikPersonMap m = byPersonId.get(p.personId());
            User u = m == null ? null : users.get(m.getUserId());
            long punches = m == null ? 0
                    : eventRepository.countByUserIdAndOccurTimeBetween(
                            m.getUserId(), since, LocalDateTime.now());
            out.add(new PersonMappingRow(
                    p.personId(),
                    p.personCode(),
                    p.displayName(),
                    m == null ? null : m.getUserId(),
                    u == null ? null : u.getEmployeeCode(),
                    u == null ? null : u.getName(),
                    m != null && m.isManual(),
                    m != null && m.isFaceEnrolled(),
                    m != null && m.isFingerprintEnrolled(),
                    punches));
        }
        return out;
    }

    /**
     * Matches one terminal identity to one employee, by hand.
     *
     * <p>Marked {@code manual}, which is what stops the sync revisiting it. The
     * terminal's own code is left exactly as Hikvision holds it — overwriting it
     * with the portal's code would make the mapping self-consistent and the
     * terminal unrecognisable to the next person who compares the two.
     *
     * @param hikPersonId the identity, from the list above
     * @param userId      the employee, or null to unmap
     * @param actor       who is making the decision, for the record
     */
    @Transactional
    public void map(String hikPersonId, Long userId, String actor) {
        if (hikPersonId == null || hikPersonId.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A terminal person is required");
        }

        Optional<HikPersonMap> existing = mapRepository.findByHikPersonId(hikPersonId);

        if (userId == null) {
            // Unmapping. The row goes rather than being blanked: user_id is NOT
            // NULL, and a mapping that points at nobody is not a mapping.
            existing.ifPresent(mapRepository::delete);
            log.info("{} unmapped Hikvision person {}", actor, hikPersonId);
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Employee"));

        /*
         * One employee, one terminal identity -- uk_hik_user enforces it, and
         * it is checked here so the answer is a sentence rather than a
         * constraint violation. Two identities for one person would interleave
         * their punches into one attendance row with no way to tell them apart.
         */
        Optional<HikPersonMap> heldByUser = mapRepository.findByUserId(userId);
        if (heldByUser.isPresent()
                && !heldByUser.get().getHikPersonId().equals(hikPersonId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    user.getName() + " is already matched to another person on the terminal. "
                            + "Unmatch that one first.");
        }

        HikPersonMap row = existing.orElseGet(HikPersonMap::new);
        row.setHikPersonId(hikPersonId);
        row.setUserId(user.getId());
        row.setCompanyId(user.getCompanyId());
        row.setManual(true);
        row.setMappedBy(actor);
        row.setMappedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());

        /*
         * person_code is filled from the terminal only when it is not already
         * known, and never overwritten with the portal's code. It records what
         * Hikvision holds, which is how a later reader sees that "001" and
         * "PIX-E001" are the same person rather than a contradiction.
         */
        if (row.getPersonCode() == null) {
            personApi.listAll().stream()
                    .filter(p -> p.personId().equals(hikPersonId))
                    .findFirst()
                    .ifPresent(p -> row.setPersonCode(p.personCode()));
        }

        mapRepository.save(row);
        log.info("{} matched Hikvision person {} to employee {} ({})",
                actor, hikPersonId, user.getId(), user.getEmployeeCode());
    }
}
