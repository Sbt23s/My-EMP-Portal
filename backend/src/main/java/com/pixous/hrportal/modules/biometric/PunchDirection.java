package com.pixous.hrportal.modules.biometric;

import java.time.LocalDateTime;

/**
 * Whether a punch is somebody arriving or somebody leaving.
 *
 * <p>Hikvision does not reliably say. Two fields look as though they might:
 *
 * <ul>
 *   <li>{@code IntelliInfo.attendanceStatus} — 1 on work, 2 off work. Real, but
 *       frequently 0 ("undefined"); the guide's own worked example of a
 *       successful face punch carries 0, and it is only populated when the
 *       terminal has been configured with attendance rules that this portal
 *       does not control.
 *   <li>{@code direction} — 1 entrance, 2 exit. This one belongs to the
 *       card-swiping <em>record search</em> response (§A.3.134), not to the
 *       pushed event, so it is simply not present when a punch arrives.
 * </ul>
 *
 * <p>So the decision is this portal's, and it is made here rather than inside
 * the attendance write so that it can be read, argued with and changed in one
 * place. {@code attendanceStatus} is still honoured when the terminal does
 * supply it — using our own rule in the face of a real answer would be
 * inventing a disagreement.
 *
 * <p>The fallback is deliberately the simplest rule that is defensible: the
 * first punch of the day is the arrival, and every later one is the current
 * departure. It is not clever — somebody who steps out at noon and returns
 * will have the return recorded as their departure until they punch again —
 * and cleverness here is a trap. A rule that tries to infer breaks from gaps
 * guesses at what somebody was doing, and when it guesses wrong it writes a
 * shorter working day onto a payslip. Last-punch-wins is wrong in a way people
 * can see on the timeline and correct, rather than wrong in a way that quietly
 * costs them.
 */
public enum PunchDirection {

    IN,
    OUT;

    /** {@code attendanceStatus} 1: the terminal says this is the start of work. */
    private static final int STATUS_ON_WORK = 1;
    /** {@code attendanceStatus} 2: the terminal says this is the end of work. */
    private static final int STATUS_OFF_WORK = 2;

    /**
     * What this punch means.
     *
     * @param attendanceStatus Hikvision's own reading, or null when absent
     * @param existingPunchIn  the arrival already recorded for that day, if any
     * @param existingPunchOut the departure already recorded for that day, if any
     */
    public static PunchDirection decide(Integer attendanceStatus,
                                        LocalDateTime existingPunchIn,
                                        LocalDateTime existingPunchOut) {
        // The terminal's own answer, when it gave one. 3-6 (break and overtime
        // boundaries) are deliberately not mapped: they are neither an arrival
        // nor a departure, and forcing them into one would move a real punch
        // time to a wrong field.
        if (attendanceStatus != null) {
            if (attendanceStatus == STATUS_ON_WORK) {
                return IN;
            }
            if (attendanceStatus == STATUS_OFF_WORK) {
                return OUT;
            }
        }

        // Nothing recorded yet today, so this is the arrival.
        if (existingPunchIn == null) {
            return IN;
        }

        /*
         * Already arrived. Everything after that is the departure, and a later
         * punch replaces an earlier one rather than being discarded -- somebody
         * who punches out, comes back and punches out again at six o'clock left
         * at six o'clock.
         */
        return OUT;
    }

    /**
     * Whether a punch of this kind should overwrite what is already recorded.
     *
     * <p>The two directions differ, and the difference is the point.
     *
     * <p>An arrival does not move. The first punch of the day is when the
     * person got in, and a second one an hour later is somebody re-entering the
     * building, not arriving late — letting it overwrite would erase the real
     * arrival and manufacture lateness that never happened.
     *
     * <p>A departure does move, forwards only. The last punch of the day is
     * when they left. Forwards only, because an out-of-order delivery — a
     * device replaying its offline buffer hours later — must not pull a
     * six o'clock departure back to lunchtime and shorten the working day.
     */
    public boolean shouldReplace(LocalDateTime existing, LocalDateTime candidate) {
        if (existing == null) {
            return true;
        }
        return this == OUT && candidate.isAfter(existing);
    }
}
