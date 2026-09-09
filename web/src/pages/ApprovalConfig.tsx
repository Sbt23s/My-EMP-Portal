import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { SlidersHorizontal, Save, RotateCcw } from "lucide-react";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { PageLoader } from "@/components/ui/page-loader";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { ApiEnvelope } from "@/types";

/**
 * Who each module may address a request to.
 *
 * Every "Request to" / "Send to" dropdown in the portal was assembled by its
 * own service with its own hard-coded idea of the answer, so changing it meant
 * changing Java — and the four lists had drifted apart from each other because
 * nothing held them to a common answer. This screen is that answer.
 *
 * It narrows rather than produces. The services still decide the candidates,
 * because they know things a grid cannot: that a complaint raised by HR goes
 * above HR, that an employee's short leave stays with their own team leader,
 * that nobody appears in their own dropdown. Ticking a role a module never
 * offers changes nothing; unticking one removes it wherever that module asks.
 *
 * A module with nothing ticked is unrestricted, not silent. That is the only
 * way to say "offer whatever you would have offered", and it is what every
 * module does today — so this screen changes nothing until somebody uses it.
 */

type Grid = Record<string, Record<string, boolean>>;

interface ConfigResponse {
  modules: string[];
  roles: string[];
  config: Grid;
}

/** What each module code is called on screen. */
const MODULE_LABELS: Record<string, string> = {
  HELPDESK: "Support requests",
  COMPLAINT: "Complaints",
  PERMISSION: "Permission",
  LEAVE: "Leave",
  WFH: "Work from home"
};

/** What each recipient is called, and a word on who that is. */
const ROLE_LABELS: Record<string, { label: string; hint: string }> = {
  CTO: { label: "CTO", hint: "The company head" },
  SUPER_ADMIN: { label: "Super Admin", hint: "Platform owner" },
  COMPANY_ADMIN: { label: "Company Admin", hint: "System administrator" },
  IT_MGR: { label: "HR (Manager)", hint: "The HR account" },
  IT_HR: { label: "HR", hint: "The HR desk" },
  CV_HR: { label: "HR (Civil)", hint: "Civil-side HR" },
  IT_TL: { label: "Team Leader", hint: "The employee's own TL" }
};

export default function ApprovalConfigPage() {
  const qc = useQueryClient();
  /** Local edits, by module. Absent means "unchanged from the server". */
  const [draft, setDraft] = useState<Grid>({});

  const query = useQuery({
    queryKey: ["approval-config"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<ConfigResponse>>("/admin/approval-config")).data.data
  });

  const save = useMutation({
    mutationFn: async ({ module, roles }: { module: string; roles: string[] }) =>
      api.put(`/admin/approval-config/${module}`, { roles }),
    onSuccess: (_res, v) => {
      toast.success(`${MODULE_LABELS[v.module] ?? v.module} updated`);
      setDraft((d) => {
        const next = { ...d };
        delete next[v.module];
        return next;
      });
      qc.invalidateQueries({ queryKey: ["approval-config"] });
    },
    onError: (e) => toast.error(apiMessage(e, "Could not save that configuration"))
  });

  if (query.isLoading) return <PageLoader />;

  const data = query.data;
  if (!data) return null;

  /** The current state of one module's ticks: the draft if edited, else saved. */
  const rowOf = (module: string): Record<string, boolean> =>
    draft[module] ?? data.config[module] ?? {};

  const toggle = (module: string, role: string) => {
    const current = rowOf(module);
    setDraft((d) => ({ ...d, [module]: { ...current, [role]: !current[role] } }));
  };

  const revert = (module: string) =>
    setDraft((d) => {
      const next = { ...d };
      delete next[module];
      return next;
    });

  return (
    <div className="p-4 sm:p-6">
      <PageHeader
        title="Approval configuration"
        subtitle="Which roles each module may address a request to."
      />

      <Card className="mb-4">
        <CardContent className="flex gap-3 p-4 text-sm text-muted-foreground">
          <SlidersHorizontal className="mt-0.5 h-4 w-4 shrink-0" />
          <p className="leading-relaxed">
            Ticking a role lets that module's <strong>Request to</strong> list
            offer it. <strong>A module with nothing ticked is unrestricted</strong>
            {" "}— it offers whatever it would have offered, which is how every
            module behaves today. This narrows the list; it does not replace the
            routing rules, so a request still reaches the person the module was
            always going to send it to, chosen from a shorter list.
          </p>
        </CardContent>
      </Card>

      <div className="space-y-4">
        {data.modules.map((module) => {
          const row = rowOf(module);
          const edited = !!draft[module];
          const ticked = data.roles.filter((r) => row[r]).length;
          return (
            <Card key={module}>
              <CardContent className="p-4">
                <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <h3 className="font-display text-lg font-bold">
                      {MODULE_LABELS[module] ?? module}
                    </h3>
                    <p className="text-xs text-muted-foreground">
                      {ticked === 0
                        ? "Unrestricted — offers whatever the module would offer"
                        : `${ticked} role${ticked === 1 ? "" : "s"} may be addressed`}
                    </p>
                  </div>
                  <div className="flex gap-2">
                    {edited && (
                      <Button variant="outline" size="sm" onClick={() => revert(module)}>
                        <RotateCcw className="h-4 w-4" /> Undo
                      </Button>
                    )}
                    <Button
                      size="sm"
                      disabled={!edited || save.isPending}
                      onClick={() =>
                        save.mutate({
                          module,
                          roles: data.roles.filter((r) => row[r])
                        })
                      }
                    >
                      <Save className="h-4 w-4" /> Save
                    </Button>
                  </div>
                </div>

                <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
                  {data.roles.map((role) => {
                    const on = !!row[role];
                    const meta = ROLE_LABELS[role] ?? { label: role, hint: "" };
                    return (
                      <label
                        key={role}
                        className={cn(
                          "flex cursor-pointer items-start gap-2 rounded-lg border p-2.5 transition",
                          on ? "border-primary bg-primary/5" : "hover:bg-muted/40"
                        )}
                      >
                        <input
                          type="checkbox"
                          className="mt-0.5 h-4 w-4 accent-[hsl(var(--primary))]"
                          checked={on}
                          onChange={() => toggle(module, role)}
                        />
                        <span className="min-w-0">
                          <span className="block text-sm font-semibold">{meta.label}</span>
                          <span className="block text-[11px] leading-tight text-muted-foreground">
                            {meta.hint}
                          </span>
                        </span>
                      </label>
                    );
                  })}
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
