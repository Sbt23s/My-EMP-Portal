package com.pixous.hrportal.modules.biometric.hik;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Punches that happened before the webhook existed.
 *
 * <p>The webhook only pushes what happens next. A terminal that has been in
 * service for months already holds the punches everybody remembers making, and
 * on the live account that was 297 records in the first week alone — none of
 * which would ever arrive, so the register would start empty on the day the
 * integration was switched on and every earlier day would read as absent.
 *
 * <p>{@code /acs/v1/event/certificaterecords/search} (§5.7.3) is the documented
 * way to read them. It returns the same facts a pushed event carries — who,
 * when, which device, which authentication method, whether it succeeded — plus
 * a {@code recordGuid} that a push does not have.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HikRecordApi {

    private static final String SEARCH_PATH =
            "/api/hccgw/acs/v1/event/certificaterecords/search";

    /** The endpoint's own maximum. */
    private static final int PAGE_SIZE = 100;

    /**
     * A ceiling on one import.
     *
     * <p>Two hundred pages is twenty thousand punches, which is more than a
     * year for a company this size. It exists so that a mistaken date range
     * cannot walk the platform's entire history at five requests a second.
     */
    private static final int MAX_PAGES = 200;

    private final HikClient client;
    private final ObjectMapper mapper;

    /**
     * Every authentication record in a date range.
     *
     * @param from first day to read, inclusive
     * @param to   last day to read, inclusive
     */
    public List<HikRecord> search(LocalDate from, LocalDate to) {
        List<HikRecord> out = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            ObjectNode criteria = mapper.createObjectNode();
            criteria.put("beginTime", isoStart(from));
            criteria.put("endTime", isoEnd(to));
            /*
             * type 1 -- the device's own clock rather than the platform's
             * receiving time. A device that was offline reports the moment
             * somebody actually presented their face, which is the time the
             * register needs; the platform's clock would record the catch-up.
             */
            criteria.put("type", 1);
            // 1: succeeded only. A refused authentication is worth keeping when
            // it arrives live -- it is how a broken enrolment shows up -- but
            // importing months of them would bury the punches in noise.
            criteria.put("swipeAuthResult", 1);

            ObjectNode body = mapper.createObjectNode();
            body.put("pageIndex", page);
            body.put("pageSize", PAGE_SIZE);
            body.set("searchCriteria", criteria);

            JsonNode data = client.call(SEARCH_PATH, body);
            JsonNode list = data.path("recordList");
            if (!list.isArray() || list.isEmpty()) {
                break;
            }

            for (JsonNode r : list) {
                HikRecord rec = read(r);
                if (rec != null) {
                    out.add(rec);
                }
            }

            int total = data.path("totalNum").asInt(0);
            if (out.size() >= total || list.size() < PAGE_SIZE) {
                break;
            }
            if (page == MAX_PAGES) {
                log.warn("Stopped importing Hikvision records at {} pages ({} of {}); "
                        + "narrow the date range to read the rest", MAX_PAGES, out.size(), total);
            }
        }

        log.info("Read {} past punches from Hikvision between {} and {}", out.size(), from, to);
        return out;
    }

    private HikRecord read(JsonNode r) {
        JsonNode base = r.path("personInfo").path("baseInfo");
        String personId = r.path("personInfo").path("id").asText(null);
        if (personId == null || personId.isBlank()) {
            // Without it the punch cannot be attributed to anybody, and a
            // record with no person is not a punch.
            return null;
        }

        /*
         * deviceTime carries the offset the device reported; occurTime is UTC.
         * Preferring deviceTime keeps the punch on the day it happened in local
         * terms -- the same reason the webhook path honours the offset rather
         * than reading the timestamp as a wall clock.
         */
        LocalDateTime when = parse(r.path("deviceTime").asText(null));
        if (when == null) {
            when = parse(r.path("occurTime").asText(null));
        }
        if (when == null) {
            return null;
        }

        return new HikRecord(
                text(r, "recordGuid"),
                personId,
                text(base, "personCode"),
                (text(base, "firstName") + " " + text(base, "lastName")).trim(),
                r.path("eventType").isNumber() ? r.path("eventType").asInt() : null,
                r.path("swipeAuthResult").isNumber() ? r.path("swipeAuthResult").asInt() : null,
                when,
                text(r, "deviceName"),
                text(r, "devSerialNo"),
                text(r, "areaName"),
                r.path("attendanceStatus").isNumber() ? r.path("attendanceStatus").asInt() : null);
    }

    /** Into the portal's own zone, as the webhook path does. */
    private static LocalDateTime parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.trim())
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Not offset-bearing; fall through.
        }
        try {
            return LocalDateTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String isoStart(LocalDate d) {
        return d.atStartOfDay(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
    }

    private static String isoEnd(LocalDate d) {
        return d.atTime(23, 59, 59).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
    }

    private static String text(JsonNode n, String field) {
        String v = n.path(field).asText(null);
        return v == null ? "" : v.trim();
    }

    /**
     * One past punch, reduced to what the register needs.
     *
     * @param recordGuid Hikvision's own identifier for the record, unique and
     *                   stable — the idempotency key a pushed event lacks
     */
    public record HikRecord(
            String recordGuid,
            String personId,
            String personCode,
            String personName,
            Integer eventType,
            Integer authResult,
            LocalDateTime occurTime,
            String deviceName,
            String deviceSerial,
            String areaName,
            Integer attendanceStatus
    ) {}
}
