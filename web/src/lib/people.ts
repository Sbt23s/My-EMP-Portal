/**
 * The company head's account is stored under the name "CEO".
 *
 * The company calls the post CTO, so every screen that shows a person's name
 * has to say CTO. That rewrite had been written out separately in the sidebar,
 * the dashboard, the notification feed and the approver lists -- four
 * implementations of one rule, which is why the audit log, written later,
 * showed "CEO" while the sidebar beside it showed "CTO".
 *
 * One rule, in one place.
 *
 * The real fix is the stored name: renaming that record in Employees would
 * correct every screen at once, web and mobile, with no code involved. This
 * keeps the display honest until someone does.
 */

/** The account this applies to. */
const HEAD_CODE = "PIX-E100";

/** Names that are really the job title, not a person's name. */
const PLACEHOLDER = /^(ceo|cto)$/i;

/**
 * The name to show for a person.
 *
 * Matched on the employee code first, because that identifies the account
 * whatever the record happens to be called. A name that merely contains "CEO"
 * is only rewritten for that account, so an unrelated employee whose name
 * contains those letters is left alone.
 *
 * @param name         the stored name
 * @param employeeCode the person's employee code, when the caller has it
 */
export function displayPersonName(
  name?: string | null,
  employeeCode?: string | null
): string | null | undefined {
  if (!name) return name;

  const isHead = (employeeCode ?? "").toUpperCase() === HEAD_CODE;
  if (!isHead) return name;

  const trimmed = name.trim();
  if (PLACEHOLDER.test(trimmed)) return "CTO";

  // A real name that carries the old title alongside it -- "CEO - Elamaran"
  // -- keeps the name and corrects the title.
  return trimmed.replace(/\bCEO\b/g, "CTO");
}
