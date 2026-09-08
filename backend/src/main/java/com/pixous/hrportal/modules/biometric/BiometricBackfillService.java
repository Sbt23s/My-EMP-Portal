package com.pixous.hrportal.modules.biometric;

import com.pixous.hrportal.modules.biometric.hik.HikClient;
import com.pixous.hrportal.modules.biometric.hik.HikRecordApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Bringing in the punches that happened before the webhook existed.
 *
 * <p>The webhook pushes what happens next and nothing else. A terminal in
 * service for months already holds every punch people remember making — 297 in
 * the first week of the live account — and without this the register would
 * start empty on the day the integration was switched on, with every earlier
 * day reading as absent.
 *
 * <p>Deliberately manual and dated. Not a startup job and not a schedule: a
 * backfill re-reads history, and history is the one thing that should only
 * change when somebody asks it to.
 *
 * <p>Everything after the read reuses the live path. Records become
 * {@code biometric_events} rows through the same unique key, the same
 * direction rule and the same processor, so an imported punch and a pushed one
 * produce the same attendance row. Writing a second, quicker path straight into
 * {@code attendance} would have been easier and would have meant two ways for
 * a day to be computed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BiometricBackfillService {

    private final HikClient client;
    private final HikRecordApi recordApi;
    private final BiometricEventRepository eventRepository;
    private final HikPersonMapRepository personMapRepository;
    private final BiometricEventIngestService ingestService;
    private final BiometricAttendanceProcessor processor;

    /**
     * Imports every punch in a date range and folds it into attendance.
     *
     * @param from first day, inclusive
     * @param to   last day, inclusive
     */
    public Result run(LocalDate from, LocalDate to) {
        if (!client.isEnabled()) {
            return new Result(false, 0, 0, 0, 0, 0);
        }
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("A date range is required, ending on or after it starts");
        }

        List<HikRecordApi.HikRecord> records = recordApi.search(from, to);

        int stored = 0;
        int duplicates = 0;
        int unmatched = 0;
        int ignored = 0;

        for (HikRecordApi.HikRecord r : records) {
            /*
             * Only the biometric grants. A card swipe is a real access event
             * and is not somebody starting work; importing months of them
             * would put people on the register for opening a door.
             */
            if (!HikEventTypes.isBiometricPunch(r.eventType())) {
                ignored++;
                continue;
            }
            if (r.authResult() == null || r.authResult() != 1) {
                ignored++;
                continue;
            }

            BiometricEvent row = new BiometricEvent();
            row.setEventType(r.eventType());
            row.setAuthMethod(HikEventTypes.authMethod(r.eventType()));
            row.setAuthResult(r.authResult());
            row.setOccurTime(r.occurTime());
            row.setHikPersonId(r.personId());
            row.setDeviceName(r.deviceName());
            row.setDeviceSerial(r.deviceSerial());
            row.setAreaName(r.areaName());
            row.setAttendanceStatus(r.attendanceStatus());
            /*
             * A record read from history is not a live arrival, and the
             * difference has to survive: currentEvent 0 is what tells the rest
             * of the system this is a replay, so a five-week-old punch does not
             * announce that somebody has just walked in.
             */
            row.setCurrentEvent(0);
            /*
             * recordGuid stands in for the batch id a push would carry. It is
             * Hikvision's own identifier for the record and is what makes a
             * second import of the same range visible as duplicates rather than
             * as a second set of punches.
             */
            row.setBatchId(truncate(r.recordGuid(), 64));

            /*
             * The unique key is (device_serial, hik_serial_no, occur_time), and
             * a searched record has no serial number -- that field belongs to
             * the pushed event. Deriving one from the recordGuid gives the key
             * something stable to work with, so re-running the same range is
             * refused by the database rather than doubling the register.
             */
            row.setHikSerialNo(syntheticSerial(r.recordGuid()));

            Optional<HikPersonMap> mapped = personMapRepository.findByHikPersonId(r.personId());
            if (mapped.isPresent()) {
                row.setUserId(mapped.get().getUserId());
                row.setCompanyId(mapped.get().getCompanyId());
            } else {
                // Stored anyway. A punch by somebody not yet matched is exactly
                // what the mapping screen exists to resolve, and discarding it
                // would hide the problem rather than the punch.
                unmatched++;
            }

            switch (ingestService.storeOne(row)) {
                case STORED -> stored++;
                case DUPLICATE -> duplicates++;
            }
        }

        /*
         * Turn them into attendance rows through the ordinary processor, in
         * batches, because a month of history is more than one pass handles.
         * It stops when a pass applies nothing, which is how a queue that is
         * empty -- or entirely unmatched -- ends the loop rather than spinning.
         */
        int applied = 0;
        for (int pass = 0; pass < 200; pass++) {
            BiometricAttendanceProcessor.ProcessResult p = processor.processPending();
            applied += p.applied();
            if (p.applied() == 0 && p.skipped() == 0) {
                break;
            }
        }

        Result result = new Result(true, records.size(), stored, duplicates, unmatched, applied);
        log.info("Biometric backfill {} to {}: {}", from, to, result.summary());
        return result;
    }

    /**
     * A stable number derived from Hikvision's record identifier.
     *
     * <p>The unique key wants a serial number and a searched record has none.
     * A hash of the recordGuid is stable across imports, which is the only
     * property that matters here: the same record must produce the same number
     * so the second import of a range is rejected.
     *
     * <p>Collisions are conceivable and harmless in the direction that matters:
     * the key is (device, serial, time), so two records would have to collide
     * on the hash AND share a device AND share a second. The cost of that would
     * be one punch skipped, against the cost of no key at all, which is a
     * duplicated register.
     */
    private static Long syntheticSerial(String recordGuid) {
        if (recordGuid == null || recordGuid.isBlank()) {
            return null;
        }
        long h = 1125899906842597L;
        for (int i = 0; i < recordGuid.length(); i++) {
            h = 31 * h + recordGuid.charAt(i);
        }
        return Math.abs(h % 1_000_000_000_000L);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * What one import did.
     *
     * @param ran        whether it reached Hikvision at all
     * @param read       records returned for the range
     * @param stored     new punches written
     * @param duplicates records already held, from an earlier import or a push
     * @param unmatched  punches by somebody not yet matched to an employee
     * @param applied    attendance rows created or extended
     */
    public record Result(boolean ran, int read, int stored, int duplicates,
                         int unmatched, int applied) {
        public String summary() {
            if (!ran) {
                return "Hikvision is not configured, so nothing was imported";
            }
            return read + " read, " + stored + " stored, " + duplicates + " already held, "
                    + unmatched + " unmatched, " + applied + " attendance rows updated";
        }
    }
}
