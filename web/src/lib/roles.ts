/**
 * IT_MGR is the code the HR role was seeded under. It reads as "manager" and
 * confuses people, so anywhere a raw role code is shown on screen it is
 * displayed as IT_HR instead. Only the label changes — every permission check
 * still runs against the code stored on the account.
 */
export function roleCodeLabel(code: string): string {
  if (code === "IT_MGR") return "IT_HR";
  /*
   * COMPANY_ADMIN and SUPER_ADMIN are one job under two names — a company's own
   * top administrator. They hold the same permissions and pass the same checks,
   * so showing two different labels only raised the question of which one an
   * account was. Shown under one name; the codes on the accounts are untouched.
   */
  if (code === "COMPANY_ADMIN" || code === "SUPER_ADMIN") return "SYSTEM_ADMIN";
  return code;
}
