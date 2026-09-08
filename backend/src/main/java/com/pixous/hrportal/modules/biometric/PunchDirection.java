package com.pixous.hrportal.modules.biometric;

import java.time.LocalDateTime;

/**
 * Whether a punch is somebody arriving or somebody leaving.
 *
 * <p>Hikvision does not reliably say, and this class used to believe it when it
 * did. Two fields look as though they might answer:
 *
 * <ul>
 *   <li>{@code IntelliInfo.attendanceStatus} — 1 on work, 2 off work. Real, but
 *       it depends on attendance rules configured on the terminal that this
 *       portal does not control, and it is frequently 0 ("undefined"); the
 *       guide's own worked example of a successful face punch carries 0.
 *   <li>{@code direction} — 1 entrance, 2 exit. This one belongs to the
 *       card-swiping <em>record search</em> response (§A.3.134), not to the
 *       pushed event, so it is simply not present when a punch arrives.
 * </ul>
 *
 * <h2>Why attendanceStatus is no longer honoured</h2>
 *
 * <p>Because on this company's real data it was wrong, and being wrong in that
 * field is worse than being silent. On 07 September the terminal supplied a
 * status on some punches and not others, and the exported sheet came out with
 * arrivals in the evening and departures in the morning:
 *
 * <pre>
 *   PIX-E025   07 Sep IN 6:48 PM   07 Sep OUT 8:48 AM
 *   PIX-E008   07 Sep IN 9:18 PM   07 Sep OUT 8:45 AM
 * </pre>
 *
 * <p>Nobody arrived at a quarter past nine at night. The 8:48 punch was a
 * status-2 ("off work") reading on somebody walking in, so it was filed as a
 * departure, and the evening punch that followed carried status 1 and was filed
 * as an arrival. Once that has happened the row is not merely odd: late minutes
 * are computed against an evening "arrival", and the worked duration comes out
 * negative or absurd, and those numbers reach a payslip.
 *
 * <p>A terminal's rule configuration is not observation. What actually happened
 * is in the timestamps, and the timestamps are never wrong about their own
 * order.
 *
 * <h2>The rule</h2>
 *
 * <p>The earliest punch of the day is the arrival; the latest is the departure.
 * That is all. It is not clever — somebody who steps out at noon and returns
 * will have the return recorded as their departure until they punch again — and
 * cleverness here is a trap: a rule that infers breaks from gaps guesses at what
 * somebody was doing, and when it guesses wrong it writes a shorter working day
 * onto a payslip. Earliest-and-latest is wrong in a way people can see on the
 * timeline and correct, rather than wrong in a way that quietly costs them.
 *
 * <p>It also survives out-of-order delivery, which the old rule did not: a
 * device replaying its offline buffer can hand over the evening punch before
 * the morning one, and the answer must be the same either way.
 */
public enum PunchDirection {

    IN,
    OUT;

    /**
     * What this punch means.
     *
     * <p>The signature keeps {@code attendanceStatus} so that callers do not
     * change and so that the deliberate decision to ignore it is visible at the
     * call site rather than hidden in a deleted parameter. It is not read.
     *
     * @param attendanceStatus Hikvision's own reading. Ignored — see above.
     * @param existingPunchIn  the arrival already recorded for that day, if any
     * @param existingPunchOut the departure already recorded for that day, if any
     * @param candidate        when this punch happened
     */
    public static PunchDirection decide(Integer attendanceStatus,
                                        LocalDateTime existingPunchIn,
                                        LocalDateTime existingPunchOut,
                                        LocalDateTime candidate) {
        // Nothing recorded yet today: the first punch is the arrival.
        if (existingPunchIn == null) {
            return IN;
        }

        /*
         * A punch earlier than the arrival on record is the real arrival. This
         * is what makes replay safe: whichever order the device hands them
         * over, the earliest ends up in punch-in.
         */
        if (candidate != null && candidate.isBefore(existingPunchIn)) {
            return IN;
        }

        // Anything at or after the arrival is the departure.
        return OUT;
    }

    /**
     * Whether a punch of this kind should overwrite what is already recorded.
     *
     * <p>The two directions differ, and the difference is the point.
     *
     * <p>An arrival moves backwards only. The earliest punch of the day is when
     * the person got in; a later one is somebody re-entering the building, and
     * letting it overwrite would erase the real arrival and manufacture
     * lateness that never happened.
     *
     * <p>A departure moves forwards only. The last punch of the day is when they
     * left, and an out-of-order delivery must not pull a six o'clock departure
     * back to lunchtime and shorten the working day.
     */
    public boolean shouldReplace(LocalDateTime existing, LocalDateTime candidate) {
        if (candidate == null) {
            return false;
        }
        if (existing == null) {
            return true;
        }
        return this == OUT ? candidate.isAfter(existing) : candidate.isBefore(existing);
    }
}
