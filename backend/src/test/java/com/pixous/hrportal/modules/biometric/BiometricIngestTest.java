package com.pixous.hrportal.modules.biometric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading the body Hik-Connect actually posts.
 *
 * <p>The payload here is the worked "Event Message Example" from §4.17,
 * unchanged — same nesting, same field names, same {@code +08:00} offset, same
 * {@code attendanceStatus} of 0. Written against the document rather than
 * against a body invented to match the parser, because a parser tested on its
 * own idea of the shape passes right up until a real punch arrives.
 */
class BiometricIngestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** §4.17 "Event Message Example", with the long picture URL abbreviated. */
    private static final String REAL_PAYLOAD = """
            {
                "list": [
                    {
                        "data": {
                            "openDoorInfo": {
                                "event": {
                                    "basicInfo": {
                                        "areaId": "9411b6ec380447d7aa016d2256e9939a",
                                        "areaName": "TM Int POC",
                                        "category": "2002",
                                        "deviceId": "ac56cc2674d645d6b91313aeaa7c07da",
                                        "serialNo": 484,
                                        "systemId": "593fbd35224641bb8acc3305cd9cfd9a",
                                        "channelNo": 1,
                                        "elementId": "c3720af986524bc3bca234f12ce7a66e",
                                        "eventType": 110013,
                                        "occurTime": "2026-01-30T15:40:07+08:00",
                                        "deviceName": "Face terminal API test",
                                        "elementName": "Face terminal API test",
                                        "elementType": 1002,
                                        "cardReaderId": "01880a28e24945d0a20c3c3a556e30d3",
                                        "currentEvent": 1,
                                        "deviceSerial": "FZ8954256"
                                    },
                                    "intelliInfo": {
                                        "groupId": "590856019023211520",
                                        "fullPath": "SNS & HIK",
                                        "lastName": "",
                                        "personId": "549648292066532352",
                                        "phoneNum": "",
                                        "firstName": "Leong",
                                        "authResult": 1,
                                        "cardNumber": "3305386219",
                                        "personPicUrl": "https://example.invalid/picture.data",
                                        "attendanceStatus": 0
                                    }
                                }
                            }
                        },
                        "type": "event",
                        "basicInfo": {
                            "device": {
                                "id": "ac56cc2674d645d6b91313aeaa7c07da",
                                "name": "Face terminal API test",
                                "category": "accessControllerDevice",
                                "deviceSerial": "FZ8954256"
                            },
                            "systemId": "593fbd35224641bb8acc3305cd9cfd9a",
                            "eventType": "110013",
                            "occurrenceTime": "2026-01-30T15:40:07+08:00"
                        }
                    }
                ],
                "batchId": "406c44ec5ac34d72842f8c724b5c6684"
            }
            """;

    /** The nested path the service reads. */
    private static JsonNode innerEvent(String body) throws Exception {
        return MAPPER.readTree(body).path("list").get(0)
                .path("data").path("openDoorInfo").path("event");
    }

    @Nested
    @DisplayName("The real payload has the shape the parser expects")
    class Shape {

        @Test
        @DisplayName("The event is under data.openDoorInfo.event, not at the top")
        void nesting() throws Exception {
            /*
             * The trap this pins: each list entry ALSO has a top-level
             * "basicInfo", a thinner object carrying device, systemId and
             * eventType-as-a-string. Reading that one instead parses without
             * error and silently loses the person, the occur time and the
             * authentication result -- every punch would arrive unattributed.
             */
            JsonNode event = innerEvent(REAL_PAYLOAD);
            assertThat(event.path("basicInfo").path("personId").isMissingNode()).isTrue();
            assertThat(event.path("intelliInfo").path("personId").asText())
                    .isEqualTo("549648292066532352");

            JsonNode outer = MAPPER.readTree(REAL_PAYLOAD).path("list").get(0).path("basicInfo");
            assertThat(outer.path("eventType").asText()).isEqualTo("110013");
            assertThat(outer.path("intelliInfo").isMissingNode()).isTrue();
        }

        @Test
        @DisplayName("eventType is a number on the inner event and a string on the outer")
        void eventTypeIsANumberWhereItIsRead() throws Exception {
            // asInt() on the outer string would work by coercion, but isNumber()
            // is what the service tests, so the distinction is real.
            assertThat(innerEvent(REAL_PAYLOAD).path("basicInfo").path("eventType").isNumber())
                    .isTrue();
            assertThat(innerEvent(REAL_PAYLOAD).path("basicInfo").path("eventType").asInt())
                    .isEqualTo(HikEventTypes.FACE);
        }

        @Test
        @DisplayName("A face punch reads as a face punch")
        void authMethod() throws Exception {
            int type = innerEvent(REAL_PAYLOAD).path("basicInfo").path("eventType").asInt();
            assertThat(HikEventTypes.authMethod(type)).isEqualTo(BiometricEvent.FACE);
            assertThat(HikEventTypes.isBiometricPunch(type)).isTrue();
        }

        @Test
        @DisplayName("attendanceStatus is 0 even in Hikvision's own example")
        void attendanceStatusIsUndefined() throws Exception {
            /*
             * Why the in/out decision cannot be delegated to Hikvision. The
             * documented worked example -- a successful face punch on a real
             * terminal -- carries attendanceStatus 0, "undefined". Building the
             * check-in/check-out rule on this field would leave most punches
             * with no direction at all.
             */
            assertThat(innerEvent(REAL_PAYLOAD).path("intelliInfo")
                    .path("attendanceStatus").asInt()).isZero();
        }

        @Test
        @DisplayName("The delivery carries a batch id, which is what the signature covers")
        void batchId() throws Exception {
            assertThat(MAPPER.readTree(REAL_PAYLOAD).path("batchId").asText())
                    .isEqualTo("406c44ec5ac34d72842f8c724b5c6684");
        }

        @Test
        @DisplayName("serialNo and deviceSerial are both present, which is what deduplicates")
        void idempotencyKeyIsAvailable() throws Exception {
            /*
             * uk_bio_event is (device_serial, hik_serial_no, occur_time).
             * Hikvision sends no idempotency key of its own, so if either of
             * these were absent from a real push the duplicate defence would
             * fall back to nothing and a retry would become a second punch.
             */
            JsonNode basic = innerEvent(REAL_PAYLOAD).path("basicInfo");
            assertThat(basic.path("deviceSerial").asText()).isEqualTo("FZ8954256");
            assertThat(basic.path("serialNo").asLong()).isEqualTo(484L);
        }
    }

    @Nested
    @DisplayName("The terminal's clock is not this office's clock")
    class Timezone {

        /** The conversion the service performs. */
        private static LocalDateTime toPortalTime(String raw) {
            return OffsetDateTime.parse(raw)
                    .atZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                    .toLocalDateTime();
        }

        @Test
        @DisplayName("A +08:00 punch is converted, not read as a wall clock")
        void offsetIsHonoured() throws Exception {
            /*
             * The guide's example device is in Singapore. 15:40 there is 13:10
             * in Kolkata. Dropping the offset and storing 15:40 would put the
             * punch two and a half hours late -- enough to turn an on-time
             * arrival into a late one, and to move an evening punch past
             * midnight onto the wrong day.
             */
            String raw = innerEvent(REAL_PAYLOAD).path("basicInfo").path("occurTime").asText();
            assertThat(raw).isEqualTo("2026-01-30T15:40:07+08:00");
            assertThat(toPortalTime(raw))
                    .isEqualTo(LocalDateTime.of(2026, 1, 30, 13, 10, 7));
        }

        @Test
        @DisplayName("A punch from a local terminal is unchanged")
        void localOffsetIsIdentity() {
            // A device on +05:30 needs no shifting, and must not get one.
            assertThat(toPortalTime("2026-09-08T09:04:11+05:30"))
                    .isEqualTo(LocalDateTime.of(2026, 9, 8, 9, 4, 11));
        }

        @Test
        @DisplayName("A late-evening punch does not slip onto the wrong day")
        void dayBoundary() {
            /*
             * The consequence that costs somebody a day's pay: 01:00 on the
             * 9th in Singapore is 22:30 on the 8th here. Stored on the wrong
             * date, that punch-out never pairs with its punch-in, and the
             * attendance row for the 8th is left open.
             */
            assertThat(toPortalTime("2026-09-09T01:00:00+08:00"))
                    .isEqualTo(LocalDateTime.of(2026, 9, 8, 22, 30, 0));
        }
    }
}
