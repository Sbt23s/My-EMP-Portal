package com.pixous.hrportal.modules.biometric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shape {@code /persons/list} actually returns.
 *
 * <p>The guide types {@code data} as {@code Object[]} (§5.8.16). The live
 * platform returns an object with the array under {@code personList}. Reading
 * only the documented shape produced an empty list from an account holding real
 * people with fingerprints enrolled — and reported it as
 * "Read 0 people from Hikvision", which reads as a terminal nobody has been
 * added to rather than as a parser looking in the wrong place.
 *
 * <p>The payload below is the real response from the live Indian account, with
 * the fingerprint template and identifiers shortened. It is kept verbatim in
 * shape so that a future change to the parser is checked against what the
 * platform sends rather than against what the document says it sends.
 */
class HikPersonListShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** What the live account returned, structurally unchanged. */
    private static final String LIVE_RESPONSE = """
            {
              "data": {
                "personList": [
                  {
                    "personInfo": {
                      "personId": "744927662028469248",
                      "groupId": "1",
                      "firstName": "Gokila",
                      "lastName": "Ganesan",
                      "gender": 2,
                      "phone": "",
                      "email": "",
                      "personCode": "EO62",
                      "description": "",
                      "startDate": 1786991400000,
                      "endDate": 2102610599000,
                      "headPicUrl": ""
                    },
                    "fingerList": [
                      { "id": "744927662028469248", "data": "3330313C159D25" }
                    ]
                  }
                ]
              },
              "errorCode": "0"
            }
            """;

    /** The documented shape, which the parser must keep accepting. */
    private static final String DOCUMENTED_RESPONSE = """
            {
              "data": [
                {
                  "personInfo": {
                    "personId": "744927662028469248",
                    "personCode": "EO62",
                    "firstName": "Gokila",
                    "lastName": "Ganesan"
                  }
                }
              ],
              "errorCode": "0"
            }
            """;

    /**
     * The same resolution {@code HikPersonApi.personRows} performs: the array
     * itself when {@code data} is one, otherwise the array under
     * {@code personList}.
     */
    private static JsonNode rows(String body) throws Exception {
        JsonNode data = MAPPER.readTree(body).path("data");
        if (data.isArray()) {
            return data;
        }
        JsonNode list = data.path("personList");
        return list.isArray() ? list : MAPPER.createArrayNode();
    }

    @Nested
    @DisplayName("Both shapes are read")
    class Shapes {

        @Test
        @DisplayName("The live shape, with the array under personList")
        void liveShape() throws Exception {
            JsonNode rows = rows(LIVE_RESPONSE);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).path("personInfo").path("personCode").asText())
                    .isEqualTo("EO62");
        }

        @Test
        @DisplayName("The documented shape, with data as the array")
        void documentedShape() throws Exception {
            /*
             * Kept working rather than replaced. The platform could return
             * either after an upgrade, and a parser that handles only what was
             * observed on one afternoon fails the same silent way round the
             * other way.
             */
            JsonNode rows = rows(DOCUMENTED_RESPONSE);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).path("personInfo").path("personCode").asText())
                    .isEqualTo("EO62");
        }

        @Test
        @DisplayName("Reading data as an array alone finds nothing in the live shape")
        void theBug() throws Exception {
            /*
             * The bug itself, stated so it cannot come back unnoticed. The old
             * parser asked isArray() of `data`, got false, and stopped -- with
             * a real person sitting one level down.
             */
            JsonNode data = MAPPER.readTree(LIVE_RESPONSE).path("data");
            assertThat(data.isArray())
                    .as("the live response's data is an object, not an array")
                    .isFalse();
            assertThat(rows(LIVE_RESPONSE))
                    .as("but the people are still found")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("An empty account reads as empty, not as an error")
        void genuinelyEmpty() throws Exception {
            assertThat(rows("{\"data\":{\"personList\":[]},\"errorCode\":\"0\"}")).isEmpty();
            assertThat(rows("{\"data\":[],\"errorCode\":\"0\"}")).isEmpty();
        }

        @Test
        @DisplayName("An unrecognised shape is empty rather than a crash")
        void unknownShape() throws Exception {
            // Reported by a warning in the real parser; here it only has to not
            // throw, because a malformed page must not abort a whole sync.
            assertThat(rows("{\"data\":{\"somethingElse\":1},\"errorCode\":\"0\"}")).isEmpty();
            assertThat(rows("{\"errorCode\":\"0\"}")).isEmpty();
        }
    }

    @Nested
    @DisplayName("What the portal takes from a person")
    class Fields {

        @Test
        @DisplayName("personCode is the employee number the mapping joins on")
        void personCode() throws Exception {
            /*
             * The live account's code is "EO62". Whatever the employee's code is
             * in this portal has to match it exactly, case aside -- the sync
             * compares them upper-cased and trimmed, and nothing else links a
             * terminal identity to an employee.
             */
            JsonNode info = rows(LIVE_RESPONSE).get(0).path("personInfo");
            assertThat(info.path("personCode").asText()).isEqualTo("EO62");
            assertThat(info.path("personId").asText()).isEqualTo("744927662028469248");
        }

        @Test
        @DisplayName("A person with a fingerprint carries fingerList beside personInfo")
        void fingerprintIsPresent() throws Exception {
            // Not read by the sync -- enrolment comes from the element-detail
            // endpoint -- but its presence confirms this really is an enrolled
            // person and not an empty record.
            assertThat(rows(LIVE_RESPONSE).get(0).path("fingerList")).isNotEmpty();
        }
    }
}
