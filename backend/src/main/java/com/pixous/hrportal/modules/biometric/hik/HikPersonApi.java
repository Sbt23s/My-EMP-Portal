package com.pixous.hrportal.modules.biometric.hik;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Reading people out of Hik-Connect.
 *
 * <p>Exists because a pushed authentication event names a {@code personId} and
 * nothing else this portal recognises. The employee number lives here, on the
 * person APIs, so the whole list has to be walked and remembered before any
 * event can be attributed to anybody.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HikPersonApi {

    private static final String LIST_PATH = "/api/hccgw/person/v1/persons/list";
    private static final String ELEMENT_DETAIL_PATH_FMT =
            "/api/hccgw/acspm/v1/maintain/overview/person/%s/elementdetail";

    /** The documented maximum for this endpoint (§5.8.16): between 1 and 100. */
    private static final int PAGE_SIZE = 100;

    /**
     * A ceiling on how many pages one sync will walk.
     *
     * <p>Not a limit on the workforce — 500 pages is fifty thousand people — but
     * a guard against looping forever. The endpoint reports neither a total nor
     * a "more data" flag, so the only way to know the list has ended is to
     * receive a short page; if a future version of the platform were to keep
     * returning full pages, an unbounded loop would spend the request budget
     * indefinitely and never finish.
     */
    private static final int MAX_PAGES = 500;

    /** Returned when a response carries no usable person array. */
    private static final JsonNode EMPTY =
            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();

    private final HikClient client;
    private final ObjectMapper mapper;

    /**
     * Every person on the account.
     *
     * <p>Walked page by page because {@code /persons/list} cannot be filtered
     * by employee number — §5.8.16 allows {@code name}, {@code email} and
     * {@code phone} only — so there is no way to ask for one person by the
     * identifier this portal actually holds.
     *
     * <p>Termination is by short page. The response carries no total and no
     * more-data flag, unlike the attendance report which has {@code moreData};
     * a page smaller than the requested size is therefore the last one, and an
     * empty page ends it too.
     */
    public List<HikPerson> listAll() {
        List<HikPerson> people = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            ObjectNode body = mapper.createObjectNode();
            body.put("pageIndex", page);
            body.put("pageSize", PAGE_SIZE);

            JsonNode rows = personRows(client.call(LIST_PATH, body));
            if (rows.isEmpty()) {
                break;
            }

            for (JsonNode row : rows) {
                HikPerson person = readPerson(row.path("personInfo"));
                if (person != null) {
                    people.add(person);
                }
            }

            if (rows.size() < PAGE_SIZE) {
                // A short page is the last page.
                break;
            }
            if (page == MAX_PAGES) {
                log.warn("Stopped reading Hikvision people at {} pages ({} so far); "
                        + "the list may be incomplete", MAX_PAGES, people.size());
            }
        }

        log.info("Read {} people from Hikvision", people.size());
        return people;
    }

    /**
     * Whether a face and a fingerprint are enrolled for one person.
     *
     * <p>From {@code certificateStatusList} on the element-detail response,
     * where {@code type} is 0 physical card, 1 fingerprint, 2 face, 3 person,
     * and {@code status} 0 means applied (§A.3.53). Anything other than applied
     * is treated as not enrolled: "applying failed" and "to be applied" both
     * mean the terminal cannot recognise the person yet, and showing either as
     * enrolled would tell an employee they can punch when they cannot.
     *
     * <p>Returns nulls rather than false when the call fails, so a lookup error
     * is distinguishable from a genuine "not enrolled" and does not overwrite a
     * previously good answer with a wrong one.
     */
    public Enrolment enrolment(String personId) {
        try {
            JsonNode data = client.call(
                    String.format(ELEMENT_DETAIL_PATH_FMT, personId), mapper.createObjectNode());

            boolean face = false;
            boolean finger = false;
            for (JsonNode element : data.path("elementDetailList")) {
                for (JsonNode cert : element.path("certificateStatusList")) {
                    // status is a String in the guide, so compare textually
                    // rather than by asInt, which would read "0" and a missing
                    // field identically.
                    boolean applied = "0".equals(cert.path("status").asText(""));
                    if (!applied) {
                        continue;
                    }
                    int type = cert.path("type").asInt(-1);
                    if (type == 2) {
                        face = true;
                    } else if (type == 1) {
                        finger = true;
                    }
                }
            }
            return new Enrolment(face, finger);
        } catch (HikApiException e) {
            log.warn("Could not read enrolment for Hikvision person {}: {}",
                    personId, e.getMessage());
            return new Enrolment(null, null);
        }
    }

    /**
     * The array of people inside a {@code /persons/list} response.
     *
     * <p>The guide types {@code data} as {@code Object[]} and the live platform
     * returns an object with the array under {@code personList}. Both are
     * handled, and the reason to handle both rather than pick the observed one
     * is that a mismatch here is invisible: the caller sees an empty list and
     * reports "0 people on the terminal", which reads as a terminal nobody has
     * been added to.
     *
     * <p>That is exactly how this presented -- a live account holding real
     * people, with fingerprints enrolled, syncing as empty.
     */
    private static JsonNode personRows(JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull()) {
            return EMPTY;
        }
        if (data.isArray()) {
            return data;
        }
        JsonNode list = data.path("personList");
        if (list.isArray()) {
            return list;
        }
        // Neither shape. Returning an empty array keeps the caller's loop
        // simple; the warning is what stops it being silent.
        log.warn("Hikvision returned a person list in an unrecognised shape: {}",
                data.fieldNames().hasNext() ? data.fieldNames().next() : "no fields");
        return EMPTY;
    }

    private HikPerson readPerson(JsonNode info) {
        if (info == null || info.isMissingNode()) {
            return null;
        }
        String personId = text(info, "personId");
        if (personId == null) {
            // Without it the row cannot be matched to an event, which is the
            // only thing the mapping is for.
            return null;
        }
        return new HikPerson(
                personId,
                text(info, "personCode"),
                text(info, "firstName"),
                text(info, "lastName"));
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * What the terminal can recognise a person by.
     *
     * <p>Both nullable, meaning "the question could not be answered" rather
     * than "no". A failed lookup must not be stored as an absent enrolment.
     */
    public record Enrolment(Boolean face, Boolean fingerprint) {
        public boolean known() {
            return face != null && fingerprint != null;
        }
    }
}
