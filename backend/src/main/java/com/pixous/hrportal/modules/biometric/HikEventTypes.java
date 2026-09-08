package com.pixous.hrportal.modules.biometric;

/**
 * The Hikvision authentication event types this portal understands.
 *
 * <p>From §A.1.6 "Message Type" of the Hik-Connect for Teams OpenAPI V2.15.0
 * guide. The wire value is the message type with the {@code Msg} prefix
 * removed, which is what {@code basicInfo.eventType} carries (§A.3.43) — so
 * {@code Msg110013} arrives as the integer {@code 110013}.
 *
 * <p>Only the granted-access types are named. The denied ones (Msg110501
 * upwards) are recorded as {@link BiometricEvent#OTHER} with their number
 * intact rather than translated, because a list of every failure mode would go
 * stale the moment Hikvision adds one, and the number is enough to look it up.
 */
public final class HikEventTypes {

    /** Access Granted by Face. */
    public static final int FACE = 110013;

    /** Access Granted by Fingerprint. */
    public static final int FINGERPRINT = 110005;

    /** Access Granted by Face and Fingerprint. */
    public static final int FACE_AND_FINGERPRINT = 110008;

    private HikEventTypes() {
    }

    /**
     * How the person authenticated, as one of the {@code BiometricEvent}
     * constants.
     *
     * <p>Anything unrecognised is {@code OTHER} rather than an error: an event
     * type we do not handle still gets stored, and refusing it here would only
     * make Hikvision retry a push that can never succeed.
     */
    public static String authMethod(Integer eventType) {
        if (eventType == null) {
            return BiometricEvent.OTHER;
        }
        return switch (eventType) {
            case FACE -> BiometricEvent.FACE;
            case FINGERPRINT -> BiometricEvent.FINGERPRINT;
            case FACE_AND_FINGERPRINT -> BiometricEvent.FACE_FINGERPRINT;
            default -> BiometricEvent.OTHER;
        };
    }

    /**
     * Whether this type is one of the biometric punches attendance acts on.
     *
     * <p>A card swipe or a PIN is a real access event and is still stored, but
     * it is not what this integration was asked for, and treating it as a punch
     * would put somebody on the register who only opened a door.
     */
    public static boolean isBiometricPunch(Integer eventType) {
        return eventType != null
                && (eventType == FACE
                || eventType == FINGERPRINT
                || eventType == FACE_AND_FINGERPRINT);
    }
}
