import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

/**
 * One employee's salary structure.
 *
 * <p>Lives here rather than inside a page because two places need it: the
 * payroll table, where a salary is set one employee at a time, and the runs
 * page, where the person about to generate a month's payroll is exactly the
 * person who notices somebody has no salary yet. A second copy of this form
 * would be two places for the field list to drift apart.
 */

export interface SalaryStructure {
  userId: number;
  basicSalary: number;
  hra: number;
  allowances: number;
  pfPercentage: number;
  esiApplicable: boolean;
  ptAmount: number;
  grossSalary: number;
}

const inr = (n?: number) => (n == null ? "—" : "₹" + Number(n).toLocaleString("en-IN"));

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <Label>{label}</Label>
      {children}
    </div>
  );
}

export function SalaryDialog({ employee, current, monthBasic, periodLabel, onClose }: {
  employee: { id: number; name: string };
  current?: SalaryStructure;
  monthBasic?: number;
  periodLabel?: string;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const [basic, setBasic] = useState(
    String(monthBasic != null ? monthBasic : current?.basicSalary ?? "")
  );
  const [hra, setHra] = useState(String(current?.hra ?? ""));
  const [allowances, setAllowances] = useState(String(current?.allowances ?? ""));
  const [pf, setPf] = useState(String(current?.pfPercentage ?? ""));
  const [esi, setEsi] = useState(current?.esiApplicable ?? true);
  const [pt, setPt] = useState(String(current?.ptAmount ?? ""));

  const num = (v: string) => (v.trim() === "" ? 0 : Number(v));
  const gross = num(basic) + num(hra) + num(allowances);

  const save = useMutation({
    mutationFn: async () =>
      api.post("/payroll/salary", {
        userId: employee.id,
        basicSalary: num(basic),
        hra: num(hra),
        allowances: num(allowances),
        pfPercentage: num(pf),
        esiApplicable: esi,
        ptAmount: num(pt)
      }),
    onSuccess: () => {
      toast.success("Salary saved");
      // Both the table and the runs page read these; a saved salary changes
      // what either one should be showing.
      qc.invalidateQueries({ queryKey: ["payroll-salaries"] });
      qc.invalidateQueries({ queryKey: ["payroll-run-salaries"] });
      onClose();
    },
    onError: (e) => toast.error(apiMessage(e, "Could not save salary"))
  });

  return (
    <Dialog open onClose={onClose} className="max-w-md">
      <DialogHeader title={`Salary — ${employee.name}`} />
      <form
        className="mt-3 space-y-3"
        onSubmit={(ev) => {
          ev.preventDefault();
          if (num(basic) <= 0) { toast.error("Enter a basic salary"); return; }
          save.mutate();
        }}
      >
        <div className="grid grid-cols-2 gap-3">
          <Field label="Basic salary *">
            <Input type="number" min="0" value={basic} onChange={(e) => setBasic(e.target.value)} placeholder="e.g. 25000" />
            {monthBasic != null && periodLabel && (
              <p className="mt-1 text-[11px] text-primary">
                Filled from Salary details — {periodLabel}
              </p>
            )}
          </Field>
          <Field label="HRA"><Input type="number" min="0" value={hra} onChange={(e) => setHra(e.target.value)} placeholder="e.g. 8000" /></Field>
          <Field label="Allowances"><Input type="number" min="0" value={allowances} onChange={(e) => setAllowances(e.target.value)} placeholder="e.g. 3000" /></Field>
          <Field label="PF (₹)"><Input type="number" min="0" value={pf} onChange={(e) => setPf(e.target.value)} placeholder="e.g. 1800" /></Field>
          <Field label="Professional Tax (₹)"><Input type="number" min="0" value={pt} onChange={(e) => setPt(e.target.value)} placeholder="e.g. 200" /></Field>
          <label className="flex items-center gap-2 self-end pb-2 text-sm">
            <input type="checkbox" checked={esi} onChange={(e) => setEsi(e.target.checked)} className="h-4 w-4 accent-[hsl(var(--primary))]" />
            ESI applicable
          </label>
        </div>
        <div className="rounded-md bg-muted/40 px-3 py-2 text-sm">
          Monthly Gross: <span className="font-semibold">{inr(gross)}</span>
          <span className="text-muted-foreground"> (Basic + HRA + Allowances)</span>
        </div>
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Save Salary
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
