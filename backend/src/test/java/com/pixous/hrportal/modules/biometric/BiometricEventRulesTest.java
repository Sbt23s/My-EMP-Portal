package com.pixous.hrportal.modules.biometric;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What counts as a punch, and what only looks like one.
 *
 * <p>Every one of these decisions is made on a row that has already been
 * stored. That is the point of storing the raw event: a failed authentication,
 * a stranger at the door and a device replaying yesterday all arrive down the
 * same webhook and all have to be kept, so the question "is this a punch" is
 * asked at the point of use rather than at the door.
 *
 * <p>Getting it wrong is silent in both directions. Treating a rejected
 * authentication as a punch puts somebody on the register who never got in;
 * discarding an unmatched one loses a real event that HR needed to see.
 */
class BiometricEventRulesTest {

    private static BiometricEvent event(Long userId, Integer authResult) {
        BiometricEvent e = new BiometricEvent();
        e.setUserId(userId);
        e.setAuthResult(authResult);
        e.setEventType(HikEventTypes.FACE);
        return e;
    }

    @Nested
    @DisplayName("Whether an event is a usable punch")
    class UsablePunch {

        @Test
        @DisplayName("A successful authentication by a known employee is a punch")
        void granted() {
            assertThat(event(7L, 1).isUsablePunch()).isTrue();
        }

        @Test
        @DisplayName("A rejected authentication is not a punch, though it is still stored")
        void denied() {
            // IntelliInfo.authResult 0. The terminal refused entry, so nobody
            // arrived -- but the row stays, because a run of these is how a
            // broken face enrolment shows up.
            assertThat(event(7L, 0).isUsablePunch()).isFalse();
        }

        @Test
        @DisplayName("A punch by somebody no employee owns is not a punch")
        void unmatched() {
            /*
             * Deliberately kept rather than dropped. Refusing the webhook would
             * make Hikvision retry a message that can never succeed, and
             * discarding it would hide the actual problem: usually an employee
             * number that was never set on the Hikvision side.
             */
            assertThat(event(null, 1).isUsablePunch()).isFalse();
        }

        @Test
        @DisplayName("A missing authResult is not assumed to be success")
        void missingAuthResult() {
            // The field is optional in the guide. Absent is not "granted": a
            // default of success would turn every malformed push into a punch.
            assertThat(event(7L, null).isUsablePunch()).isFalse();
        }
    }

    @Nested
    @DisplayName("Live versus replayed from the device's buffer")
    class Buffered {

        @Test
        @DisplayName("currentEvent 0 means the device was offline and is catching up")
        void offline() {
            BiometricEvent e = event(7L, 1);
            e.setCurrentEvent(0);
            assertThat(e.isBuffered()).isTrue();
        }

        @Test
        @DisplayName("currentEvent 1 is live")
        void live() {
            BiometricEvent e = event(7L, 1);
            e.setCurrentEvent(1);
            assertThat(e.isBuffered()).isFalse();
        }

        @Test
        @DisplayName("An absent currentEvent is live, as the guide specifies")
        void absentMeansLive() {
            /*
             * A.3.43: "It is current event without this field." Reading absent
             * as buffered would mark every normal punch as a late replay and
             * suppress the notification that somebody has arrived.
             */
            assertThat(event(7L, 1).isBuffered()).isFalse();
        }
    }

    @Nested
    @DisplayName("Which Hikvision event type is which authentication method")
    class EventTypes {

        @Test
        @DisplayName("The three biometric grants map to their methods")
        void mapped() {
            // Msg110013, Msg110005 and Msg110008 with the prefix removed, which
            // is the form basicInfo.eventType arrives in.
            assertThat(HikEventTypes.authMethod(110013)).isEqualTo(BiometricEvent.FACE);
            assertThat(HikEventTypes.authMethod(110005)).isEqualTo(BiometricEvent.FINGERPRINT);
            assertThat(HikEventTypes.authMethod(110008))
                    .isEqualTo(BiometricEvent.FACE_FINGERPRINT);
        }

        @Test
        @DisplayName("A denied event keeps its number and is not translated")
        void deniedIsOther() {
            // Msg110531, Access Denied by Face. Naming every failure mode would
            // go stale the moment Hikvision adds one, and the number is enough
            // to look it up.
            assertThat(HikEventTypes.authMethod(110531)).isEqualTo(BiometricEvent.OTHER);
        }

        @Test
        @DisplayName("An unknown or missing type is OTHER rather than an error")
        void unknown() {
            assertThat(HikEventTypes.authMethod(999999)).isEqualTo(BiometricEvent.OTHER);
            assertThat(HikEventTypes.authMethod(null)).isEqualTo(BiometricEvent.OTHER);
        }

        @Test
        @DisplayName("Only the three biometric grants count as punches")
        void onlyBiometricIsAPunch() {
            assertThat(HikEventTypes.isBiometricPunch(110013)).isTrue();
            assertThat(HikEventTypes.isBiometricPunch(110005)).isTrue();
            assertThat(HikEventTypes.isBiometricPunch(110008)).isTrue();

            /*
             * Msg110003 is Access Granted by Card. A real access event, stored
             * like any other -- but somebody who swiped a card only opened a
             * door, and putting them on the attendance register would record a
             * working day that was never worked.
             */
            assertThat(HikEventTypes.isBiometricPunch(110003)).isFalse();
            // Msg110020, Password Authenticated. Same reasoning.
            assertThat(HikEventTypes.isBiometricPunch(110020)).isFalse();
            assertThat(HikEventTypes.isBiometricPunch(110531)).isFalse();
            assertThat(HikEventTypes.isBiometricPunch(null)).isFalse();
        }
    }
}
