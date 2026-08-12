/**
 * IT_MGR is the code the HR role was seeded under. It reads as "manager" and
 * confuses people, so anywhere a raw role code is shown on screen it is
 * displayed as IT_HR instead. Only the label changes — every permission check
 * still runs against the code stored on the account.
 */
export function roleCodeLabel(code: string): string {
  return code === "IT_MGR" ? "IT_HR" : code;
}
