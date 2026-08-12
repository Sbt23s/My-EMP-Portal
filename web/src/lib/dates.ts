import dayjs from "dayjs";

/**
 * Today, as the value an <input type="date"> expects.
 *
 * Used as `min` on every picker where only a future date makes sense — leave,
 * permission, a task's due date, a calendar entry. It is a function rather than
 * a constant so a tab left open overnight still offers the right day.
 *
 * Filters over past records, dates of birth and dates of joining deliberately
 * do NOT use this: there the past is the whole point.
 */
export function todayIso() {
  return dayjs().format("YYYY-MM-DD");
}

/** This month, as the value an <input type="month"> expects. */
export function thisMonthIso() {
  return dayjs().format("YYYY-MM");
}

/**
 * "14:30" -> "2:30 PM". Times are stored and sent as 24-hour strings; people
 * read them in 12-hour with AM/PM, so every screen formats them on the way out.
 */
export function to12Hour(hhmm?: string | null) {
  if (!hhmm) return "—";
  const [h, m] = hhmm.split(":");
  const hour = Number(h);
  if (Number.isNaN(hour)) return hhmm;
  const suffix = hour < 12 ? "AM" : "PM";
  const shown = hour % 12 === 0 ? 12 : hour % 12;
  return `${shown}:${(m ?? "00").padStart(2, "0")} ${suffix}`;
}
