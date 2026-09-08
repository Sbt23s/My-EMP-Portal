package com.pixous.hrportal.modules.biometric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Turning a webhook body into rows.
 *
 * <p>Deliberately does no interpreting. It reads what arrived, resolves who it
 * belongs to, and stores it — nothing here decides whether a punch is an
 * arrival or a departure, or touches the attendance register. That separation
 * is what makes the raw table worth having: the rules can change and the events
 * can be read again, whereas an attendance row derived wrongly cannot be
 * un-derived.
 */
@Slf4j
@Service
public class BiometricEventIngestService {

    /**
     * Where the terminal's clock lives, for a punch that arrives without one.
     *
     * <p>Hikvision sends {@code occurTime} with an offset — the guide's example
     * is {@code 2026-01-30T15:40:07+08:00} from a Singapore device — and the
     * portal stores local times. So every timestamp is converted into this
     * zone rather than having its offset dropped: read as a wall clock, a
     * Singapore punch at 15:40 would land at 15:40 here, two and a half hours
     * late, and an on-time arrival would be recorded as an afternoon one.
     */
    private static final ZoneId PORTAL_ZONE = ZoneId.of("Asia/Kolkata");

    private final ObjectMapper mapper;
    private final BiometricEventRepository eventRepository;
    private final HikPersonMapRepository personMapRepository;

    /**
     * This service, through Spring's proxy.
     *
     * <p>{@code storeOne} is {@code REQUIRES_NEW}, and a plain {@code this.}
     * call does not go through the proxy -- the annotation is simply ignored,
     * the new transaction never starts, and the first duplicate in a batch
     * poisons the surrounding transaction and takes every punch after it with
     * it. That failure is invisible: the webhook still answers 200.
     *
     * <p>{@code @Lazy} because a bean cannot be handed itself while it is being
     * constructed; without it the context fails to start with a circular
     * reference.
     */
    private final BiometricEventIngestService self;

    public BiometricEventIngestService(ObjectMapper mapper,
                                       BiometricEventRepository eventRepository,
                                       HikPersonMapRepository personMapRepository,
                                       @Lazy BiometricEventIngestService self) {
        this.mapper = mapper;
        this.eventRepository = eventRepository;
        this.personMapRepository = personMapRepository;
        this.self = self;
    }

    /**
     * Stores every punch in one delivery.
     *
     * <p>No transaction of its own, on purpose. Each event is written in its
     * own (see {@link #storeOne}), so one malformed or duplicate entry in a
     * batch cannot roll back the ones beside it — which matters because
     * Hikvision batches several punches together and a whole shift's arrivals
     * can share a delivery.
     */
    public IngestResult ingest(String rawBody, String batchId) {
        if (rawBody == null || rawBody.isBlank()) {
            return new IngestResult(0, 0, 0, 0);
        }

        JsonNode root;
        try {
            root = mapper.readTree(rawBody);
        } catch (Exception e) {
            /*
             * Not JSON. Counted as unusable rather than thrown: throwing would
             * answer 5xx and have Hikvision redeliver a body that will never
             * parse, three times, before discarding it anyway.
             */
            log.warn("Webhook batch {} was not JSON: {}", batchId, e.getMessage());
            return new IngestResult(0, 0, 0, 1);
        }

        JsonNode list = root.path("list");
        if (!list.isArray() || list.isEmpty()) {
            return new IngestResult(0, 0, 0, 0);
        }

        int stored = 0;
        int duplicates = 0;
        int unmatched = 0;
        int ignored = 0;

        for (JsonNode entry : list) {
            /*
             * The event lives several levels down: list[].data.openDoorInfo
             * .event, with basicInfo and intelliInfo under that. The outer
             * basicInfo is a different, thinner object -- device, systemId,
             * eventType as a string -- and reading that one instead would lose
             * the person entirely.
             */
            JsonNode event = entry.path("data").path("openDoorInfo").path("event");
            JsonNode basic = event.path("basicInfo");
            JsonNode intelli = event.path("intelliInfo");

            if (basic.isMissingNode() || basic.isEmpty()) {
                // An alarm or some other message shape. Not ours.
                ignored++;
                continue;
            }

            Integer eventType = basic.path("eventType").isNumber()
                    ? basic.path("eventType").asInt() : null;
            if (eventType == null) {
                ignored++;
                continue;
            }

            LocalDateTime occurTime = parseOccurTime(basic.path("occurTime").asText(null));
            if (occurTime == null) {
                log.warn("Webhook batch {} carried an event with no usable occurTime", batchId);
                ignored++;
                continue;
            }

            BiometricEvent row = new BiometricEvent();
            row.setEventType(eventType);
            row.setAuthMethod(HikEventTypes.authMethod(eventType));
            row.setOccurTime(occurTime);
            row.setBatchId(batchId);
            row.setRawPayload(entry.toString());

            row.setDeviceId(text(basic, "deviceId"));
            row.setDeviceSerial(text(basic, "deviceSerial"));
            row.setDeviceName(text(basic, "deviceName"));
            row.setAreaId(text(basic, "areaId"));
            row.setAreaName(text(basic, "areaName"));
            row.setHikSerialNo(basic.path("serialNo").isNumber()
                    ? basic.path("serialNo").asLong() : null);
            /*
             * "It is current event without this field" (§A.3.43), so an absent
             * currentEvent means live. Defaulting it to 0 instead would mark
             * every ordinary punch as a late replay from the device's buffer.
             */
            row.setCurrentEvent(basic.path("currentEvent").isNumber()
                    ? basic.path("currentEvent").asInt() : 1);

            String personId = text(intelli, "personId");
            row.setHikPersonId(personId);
            row.setAuthResult(intelli.path("authResult").isNumber()
                    ? intelli.path("authResult").asInt() : null);
            row.setAttendanceStatus(intelli.path("attendanceStatus").isNumber()
                    ? intelli.path("attendanceStatus").asInt() : null);

            if (personId != null) {
                Optional<HikPersonMap> mapped = personMapRepository.findByHikPersonId(personId);
                if (mapped.isPresent()) {
                    row.setUserId(mapped.get().getUserId());
                    row.setCompanyId(mapped.get().getCompanyId());
                } else {
                    /*
                     * Stored anyway, with no employee. Dropping it would lose a
                     * real punch, and refusing the delivery would make
                     * Hikvision retry a message that can never succeed. HR sees
                     * it as "somebody punched and we do not know who", which is
                     * the actionable form -- usually an employee number that
                     * was never set on the Hikvision side.
                     */
                    unmatched++;
                }
            } else {
                unmatched++;
            }

            switch (self.storeOne(row)) {
                case STORED -> stored++;
                case DUPLICATE -> duplicates++;
            }
        }

        return new IngestResult(stored, duplicates, unmatched, ignored);
    }

    /**
     * Writes one event, treating a repeat delivery as success.
     *
     * <p>{@code REQUIRES_NEW} so a rejected duplicate rolls back only itself.
     * Without it the constraint violation would poison the surrounding
     * transaction and every punch after it in the same batch would be lost.
     *
     * <p>The check-then-insert is not a race guard — two concurrent deliveries
     * of the same punch can both pass the check — which is why the unique key
     * {@code uk_bio_event} exists and why the violation is caught rather than
     * prevented. The lookup is there to keep the ordinary retry cheap: Hikvision
     * retries three times by default, and letting each one raise an exception
     * would fill the log with stack traces for entirely normal behaviour.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StoreOutcome storeOne(BiometricEvent row) {
        if (row.getDeviceSerial() != null && row.getHikSerialNo() != null) {
            boolean seen = eventRepository
                    .findByDeviceSerialAndHikSerialNoAndOccurTime(
                            row.getDeviceSerial(), row.getHikSerialNo(), row.getOccurTime())
                    .isPresent();
            if (seen) {
                return StoreOutcome.DUPLICATE;
            }
        }
        try {
            eventRepository.save(row);
            return StoreOutcome.STORED;
        } catch (DataIntegrityViolationException e) {
            // The unique key caught what the lookup could not: the same punch
            // delivered twice at once. Already stored, so nothing is lost.
            return StoreOutcome.DUPLICATE;
        }
    }

    /**
     * Reads Hikvision's ISO timestamp into the portal's own zone.
     *
     * <p>The offset is honoured rather than discarded. A terminal in Singapore
     * reports {@code +08:00}; parsed as a local time that punch would be stored
     * two and a half hours later than it happened, and somebody who arrived at
     * nine would be recorded as arriving after eleven.
     *
     * <p>A value without an offset is taken at face value, which is the best
     * available reading: it is what the device believes the wall clock said.
     */
    private static LocalDateTime parseOccurTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.trim())
                    .atZoneSameInstant(PORTAL_ZONE)
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // No offset on it.
        }
        try {
            return LocalDateTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** What happened to one event. */
    public enum StoreOutcome { STORED, DUPLICATE }

    /**
     * What happened to one delivery.
     *
     * @param stored     new punches written
     * @param duplicates repeat deliveries, already held
     * @param unmatched  punches by somebody no employee owns
     * @param ignored    entries that were not authentication events at all
     */
    public record IngestResult(int stored, int duplicates, int unmatched, int ignored) {
        public String summary() {
            return stored + " stored, " + duplicates + " duplicate, "
                    + unmatched + " unmatched, " + ignored + " ignored";
        }
    }
}
