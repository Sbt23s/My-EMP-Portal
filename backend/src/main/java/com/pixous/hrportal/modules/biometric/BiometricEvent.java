package com.pixous.hrportal.modules.biometric;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One authentication event, exactly as the Hikvision terminal reported it.
 *
 * <p>Written before anything is interpreted and never edited afterwards except
 * to mark it processed. The event is evidence; the attendance row is a
 * conclusion. Storing only the conclusion leaves a disputed punch six weeks
 * later with nothing behind it — which device, which area, face or fingerprint,
 * whether the terminal accepted the authentication, whether it was live or
 * replayed from the device's offline buffer.
 *
 * <p>Keeping the raw row also means the processing rules can change without the
 * history being lost: events can be reprocessed, whereas an attendance row that
 * was derived wrongly cannot be un-derived.
 */
@Getter
@Setter
@Entity
@Table(name = "biometric_events")
public class BiometricEvent {

    /** How the person authenticated, as this portal reads {@link #eventType}. */
    public static final String FACE = "FACE";
    public static final String FINGERPRINT = "FINGERPRINT";
    public static final String FACE_FINGERPRINT = "FACE_FINGERPRINT";
    public static final String OTHER = "OTHER";

    /** What the punch was taken to mean. Not a Hikvision field — see {@link #direction}. */
    public static final String IN = "IN";
    public static final String OUT = "OUT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    /**
     * The employee this punch belongs to, once resolved.
     *
     * <p>Nullable, and this is the important nullable column here. Somebody who
     * is on the terminal but not mapped to an employee still has to be
     * recorded: dropping the event would lose a real punch, and refusing the
     * webhook would make Hikvision retry a message that can never succeed. An
     * unmatched row is visible to HR as "this person punched and we do not know
     * who they are", which is the actionable form of the problem.
     */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "hik_person_id", length = 64)
    private String hikPersonId;

    /**
     * {@code basicInfo.eventType} — the message type with the {@code Msg}
     * prefix removed (§A.3.43). The three that matter here are 110013 (face),
     * 110005 (fingerprint) and 110008 (face and fingerprint).
     *
     * <p>Stored as the number Hikvision sent rather than as an enum of ours, so
     * an event type not yet handled is still recorded faithfully.
     */
    @Column(name = "event_type", nullable = false)
    private Integer eventType;

    /** Our reading of {@link #eventType}: one of the constants above. */
    @Column(name = "auth_method", length = 24)
    private String authMethod;

    /**
     * {@code IntelliInfo.authResult}: 1 succeeded, 0 failed.
     *
     * <p>A failed authentication is kept — it is how a broken enrolment or a
     * stranger at the door shows up — which is why processing has to check this
     * rather than assume every row is a valid punch.
     */
    @Column(name = "auth_result")
    private Integer authResult;

    /**
     * {@code basicInfo.occurTime} — when the terminal says it happened.
     *
     * <p>Distinct from {@link #receivedAt}, and the two differ by days when a
     * device has been offline. Attendance is keyed off this one.
     */
    @Column(name = "occur_time", nullable = false)
    private LocalDateTime occurTime;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "device_serial", length = 64)
    private String deviceSerial;

    @Column(name = "device_name", length = 128)
    private String deviceName;

    @Column(name = "area_id", length = 64)
    private String areaId;

    @Column(name = "area_name", length = 128)
    private String areaName;

    /** {@code basicInfo.serialNo} — the device's own counter for the event. */
    @Column(name = "hik_serial_no")
    private Long hikSerialNo;

    /**
     * {@code basicInfo.currentEvent}: 0 the device buffered this while offline
     * and is replaying it, 1 it is live. The guide notes the field is absent on
     * a current event, so absent is read as 1.
     *
     * <p>Kept because a backfilled punch and a live one must not look
     * identical: one of them arrived hours late and should not, for instance,
     * announce that somebody has just arrived.
     */
    @Column(name = "current_event")
    private Integer currentEvent;

    /**
     * {@code IntelliInfo.attendanceStatus}: 0 undefined, 1 on work, 2 off work,
     * 3 break starts, 4 break ends, 5 overtime starts, 6 overtime ends.
     *
     * <p>Hikvision's own opinion about the punch, and frequently 0 — the worked
     * example in the guide itself shows 0 — so it informs the in/out decision
     * without deciding it.
     */
    @Column(name = "attendance_status")
    private Integer attendanceStatus;

    /** The webhook batch this arrived in, for tracing a delivery end to end. */
    @Column(name = "batch_id", length = 64)
    private String batchId;

    /**
     * {@link #IN} or {@link #OUT}, as this portal concluded.
     *
     * <p>Not a Hikvision field. The guide's {@code direction} (1 entrance,
     * 2 exit) belongs to the record-search response and is not on the pushed
     * event, and {@code attendanceStatus} is often undefined. Recorded so a
     * punch can be re-read later as what it was taken to mean at the time, even
     * if the rule changes afterwards.
     */
    @Column(name = "direction", length = 10)
    private String direction;

    /**
     * Whether this event has been folded into an attendance row.
     *
     * <p>The processor claims rows by this flag, so a crash mid-batch resumes
     * rather than double-counting or dropping.
     */
    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /**
     * Why an event could not be processed, if it could not. On the row rather
     * than only in the log, because the row is what somebody looks at when a
     * punch did not appear on the register.
     */
    @Column(name = "process_error", length = 500)
    private String processError;

    /**
     * The whole push, verbatim. Costs little and settles arguments: the fields
     * above are what we chose to read, this is what actually arrived.
     */
    @Column(name = "raw_payload", columnDefinition = "JSON")
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();

    /**
     * Whether this event is a real, usable punch.
     *
     * <p>Two things disqualify one: the terminal rejected the authentication,
     * or nobody here owns it. Both are stored rather than discarded, so the
     * check has to happen at the point of use.
     */
    public boolean isUsablePunch() {
        return userId != null && authResult != null && authResult == 1;
    }

    /** Whether the device replayed this from its offline buffer. */
    public boolean isBuffered() {
        // Absent means current, per §A.3.43.
        return currentEvent != null && currentEvent == 0;
    }
}
