package com.pixous.hrportal.modules.attendance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceResponse(
        Long id,
        Long userId,
        LocalDate workDate,
        LocalDateTime punchInAt,
        LocalDateTime punchOutAt,
        String mode,
        String status,
        boolean late,
        /** How many minutes past the office start this punch was. */
        int lateMinutes,
        Boolean withinGeofence,
        boolean geofenceException,
        Integer workedMinutes,
        Integer overtimeMinutes,
        BigDecimal inLatitude,
        BigDecimal inLongitude,
        BigDecimal outLatitude,
        BigDecimal outLongitude,

        /**
         * Where the punch was made, named.
         *
         * <p>Coordinates are true and unreadable: nobody looking at a timesheet can
         * tell whether 12.97610, 80.22140 is the office. These are the same numbers
         * matched against the offices and sites on record — so a punch inside one
         * carries its name, and a punch outside every one of them says so, with how
         * far from the nearest it was.
         */
        String inLocationName,
        String outLocationName,
        /** Metres from the nearest known office or site, when outside all of them. */
        Integer inDistanceMetres,
        /** How accurate the device said its own fix was. */
        Integer inAccuracyMetres,

        /**
         * The face check. The selfie is what makes a punch answerable for months
         * later, so it travels with the row rather than needing a second call.
         */
        boolean faceVerified,
        String facePhotoPath,
        BigDecimal faceScore,
        boolean outFaceVerified,
        String outFacePhotoPath,
        String inDevice,
        /** The browser or terminal the punch-out was made from. */
        String outDevice,

        /**
         * How the punch was made: FACE, FINGERPRINT, FACE_FINGERPRINT, or null
         * for a punch that did not come from a biometric terminal.
         *
         * <p>The distinction people actually ask about. A punch from the app is
         * somebody saying they arrived; a punch at the terminal is the terminal
         * recognising their face, and a timesheet that shows them identically
         * cannot answer "did they really come in".
         */
        String inAuthMethod,
        String outAuthMethod,

        /**
         * Where the terminal stands, as Hikvision names it -- "Main Gate",
         * "Second Floor Entry".
         *
         * <p>Separate from {@code inLocationName}, which is derived from GPS by
         * matching coordinates against the offices on record. A wall-mounted
         * terminal sends no coordinates, so that field is empty for these
         * punches however real the location is; the area is the location, and
         * it is a better one -- a named door rather than a point that happens
         * to fall inside a radius.
         */
        String inAreaName,
        String outAreaName
) {}
