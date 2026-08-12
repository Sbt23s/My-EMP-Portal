package com.pixous.hrportal.modules.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "attendance")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "punch_in_at")
    private LocalDateTime punchInAt;

    @Column(name = "punch_out_at")
    private LocalDateTime punchOutAt;

    @Column(length = 20)
    private String mode = "OFFICE";

    @Column(name = "in_latitude")
    private BigDecimal inLatitude;
    @Column(name = "in_longitude")
    private BigDecimal inLongitude;
    @Column(name = "out_latitude")
    private BigDecimal outLatitude;
    @Column(name = "out_longitude")
    private BigDecimal outLongitude;

    @Column(name = "site_id")
    private Long siteId;
    @Column(name = "shift_id")
    private Long shiftId;

    @Column(name = "within_geofence")
    private Boolean withinGeofence;

    @Column(length = 20)
    private String status = "PRESENT";

    @Column(name = "is_late", nullable = false)
    private boolean late = false;

    /** Minutes past the office start time, so a month can report time lost. */
    @Column(name = "late_minutes", nullable = false)
    private int lateMinutes = 0;

    @Column(name = "worked_minutes")
    private Integer workedMinutes;

    @Column(name = "overtime_minutes")
    private Integer overtimeMinutes = 0;

    @Column(name = "geofence_exception", nullable = false)
    private boolean geofenceException = false;

    /**
     * Whether the face matched when this punch was made, and the selfie it was
     * matched from. Without these a verified punch and an unverified one look
     * identical afterwards, which is most of what verification is for.
     */
    @Column(name = "face_verified", nullable = false)
    private boolean faceVerified = false;

    @Column(name = "face_photo_path", length = 255)
    private String facePhotoPath;

    /** How close the match was. Lower is closer. */
    @Column(name = "face_score", precision = 5, scale = 4)
    private BigDecimal faceScore;

    @Column(name = "out_face_verified", nullable = false)
    private boolean outFaceVerified = false;

    @Column(name = "out_face_photo_path", length = 255)
    private String outFacePhotoPath;

    @Column(name = "out_face_score", precision = 5, scale = 4)
    private BigDecimal outFaceScore;

    /**
     * Everything the face check measured, as the analytics service reported it:
     * confidence, lighting, sharpness, faces in frame, eyes open, head turn,
     * liveness. The verdict alone cannot answer a disputed punch a month later.
     */
    @Column(name = "face_detail", columnDefinition = "TEXT")
    private String faceDetail;

    @Column(name = "out_face_detail", columnDefinition = "TEXT")
    private String outFaceDetail;

    /** How accurate the device said its own location was, in metres. */
    @Column(name = "in_accuracy_m")
    private Integer inAccuracyM;

    @Column(name = "out_accuracy_m")
    private Integer outAccuracyM;

    @Column(name = "in_device", length = 255)
    private String inDevice;

    @Column(name = "out_device", length = 255)
    private String outDevice;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
