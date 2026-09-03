import { useMemo, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  Settings, ListOrdered, ShieldCheck, Save, Plus, Ban, Search, Lock, Star
} from "lucide-react";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import { useAuth } from "@/hooks/useAuth";
import type { ApiEnvelope } from "@/types";

/* ------------------------------------------------------------------ types */

interface Setting {
  id: number;
  key: string;
  value: string | null;
  valueType: "STRING" | "INT" | "BOOLEAN" | "DECIMAL" | "JSON";
  category: string;
  label: string | null;
  description: string | null;
  platformOnly: boolean;
  editable: boolean;
  /** The value is the platform default; no company override exists yet. */
  inherited: boolean;
}

interface OptionSetSummary {
  id: number;
  code: string;
  name: string;
  module: string | null;
  systemSet: boolean;
  inherited: boolean;
  optionCount: number;
}

interface ConfigOption {
  id: number;
  code: string;
  label: string;
  sortOrder: number;
  active: boolean;
  isDefault: boolean;
  metadata: string | null;
}

interface OptionSetDetail extends Omit<OptionSetSummary, "optionCount"> {
  description: string | null;
  options: ConfigOption[];
}

interface RoleRow {
  id: number;
  code: string;
  name: string;
  description: string | null;
  companyId: number | null;
  permissions: string[];
}

type Tab = "settings" | "dropdowns" | "roles";

/* ------------------------------------------------------------------ page */

export default function AdminConfigurationPage() {
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const canEdit = hasPermission("CONFIG_MANAGE");

  const [tab, setTab] = useState<Tab>("settings");

  const settings = useQuery({
    queryKey: ["config", "settings"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<Setting[]>>("/config/settings")).data.data
  });

  const optionSets = useQuery({
    queryKey: ["config", "option-sets"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<OptionSetSummary[]>>("/config/option-sets")).data.data
  });

  const roles = useQuery({
    queryKey: ["config", "roles"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<RoleRow[]>>("/config/roles")).data.data
  });

  const tabs: { key: Tab; label: string; icon: typeof Settings; count?: number }[] = [
    { key: "settings", label: "Settings", icon: Settings, count: settings.data?.length },
    { key: "dropdowns", label: "Dropdowns", icon: ListOrdered, count: optionSets.data?.length },
    { key: "roles", label: "Roles & Permissions", icon: ShieldCheck, count: roles.data?.length }
  ];

  return (
    <div>
      <PageHeader
        icon={Settings}
        title="Admin Configuration"
        subtitle="Settings, dropdown values and role permissions for this company."
      />

      {!canEdit && (
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50/70 px-4 py-3 dark:border-amber-500/30 dark:bg-amber-500/10">
          <div className="text-sm font-semibold text-amber-900 dark:text-amber-200">
            Read-only
          </div>
          <p className="mt-0.5 text-xs text-amber-800 dark:text-amber-300">
            You can review the configuration but not change it. Changing it needs
            the Configuration Manage permission.
          </p>
        </div>
      )}

      <div className="mb-5 flex flex-wrap gap-2">
        {tabs.map((t) => {
          const Icon = t.icon;
          const on = tab === t.key;
          return (
            <button
              key={t.key}
              type="button"
              onClick={() => setTab(t.key)}
              aria-current={on ? "page" : undefined}
              className={
                "inline-flex items-center gap-2 rounded-lg border px-3.5 py-2 text-sm font-medium transition " +
                (on
                  ? "border-primary bg-primary text-primary-foreground shadow-sm"
                  : "border-border bg-card text-muted-foreground hover:bg-muted")
              }
            >
              <Icon className="h-4 w-4" />
              {t.label}
              {typeof t.count === "number" && (
                <span
                  className={
                    "rounded-md px-1.5 py-0.5 text-[11px] font-semibold " +
                    (on ? "bg-primary-foreground/20" : "bg-muted")
                  }
                >
                  {t.count}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {tab === "settings" && (
        <SettingsTab
          rows={settings.data}
          loading={settings.isLoading}
          canEdit={canEdit}
          onSaved={() => queryClient.invalidateQueries({ queryKey: ["config", "settings"] })}
        />
      )}
      {tab === "dropdowns" && (
        <DropdownsTab sets={optionSets.data} loading={optionSets.isLoading} canEdit={canEdit} />
      )}
      {tab === "roles" && <RolesTab rows={roles.data} loading={roles.isLoading} />}
    </div>
  );
}

/* -------------------------------------------------------------- settings */

function SettingsTab({
  rows,
  loading,
  canEdit,
  onSaved
}: {
  rows: Setting[] | undefined;
  loading: boolean;
  canEdit: boolean;
  onSaved: () => void;
}) {
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("");
  /* Edits are held here until saved, so a mistyped number can be corrected
     before it reaches the server rather than after. */
  const [draft, setDraft] = useState<Record<string, string>>({});

  const save = useMutation({
    mutationFn: async (payload: { key: string; value: string }) =>
      (await api.put<ApiEnvelope<Setting>>("/config/settings", payload)).data.data,
    onSuccess: (saved) => {
      toast.success(`${saved.label ?? saved.key} saved`);
      setDraft((d) => {
        const next = { ...d };
        delete next[saved.key];
        return next;
      });
      onSaved();
    },
    onError: (err) => toast.error(apiMessage(err, "Could not save the setting"))
  });

  const categories = useMemo(
    () => Array.from(new Set((rows ?? []).map((r) => r.category))).sort(),
    [rows]
  );

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (rows ?? []).filter((r) => {
      if (category && r.category !== category) return false;
      if (!q) return true;
      return (
        r.key.toLowerCase().includes(q) ||
        (r.label ?? "").toLowerCase().includes(q) ||
        (r.description ?? "").toLowerCase().includes(q)
      );
    });
  }, [rows, query, category]);

  const grouped = useMemo(() => {
    const map = new Map<string, Setting[]>();
    visible.forEach((r) => {
      const list = map.get(r.category) ?? [];
      list.push(r);
      map.set(r.category, list);
    });
    return Array.from(map.entries()).sort((a, b) => a[0].localeCompare(b[0]));
  }, [visible]);

  if (loading) return <LoadingRows />;

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="min-w-[220px] flex-1">
          <Label htmlFor="cfg-search">Search</Label>
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              id="cfg-search"
              className="pl-9"
              placeholder="Setting name or key"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </div>
        </div>
        <div className="w-[190px]">
          <Label htmlFor="cfg-category">Category</Label>
          <Select
            id="cfg-category"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            <option value="">All categories</option>
            {categories.map((c) => (
              <option key={c} value={c}>
                {titleCase(c)}
              </option>
            ))}
          </Select>
        </div>
      </div>

      {grouped.length === 0 && <EmptyState message="No settings match that search." />}

      <div className="space-y-5">
        {grouped.map(([cat, list]) => (
          <Card key={cat}>
            <CardContent className="p-0">
              <div className="border-b border-border px-4 py-3">
                <h2 className="font-display text-sm font-semibold uppercase tracking-wide">
                  {titleCase(cat)}
                </h2>
              </div>
              <div className="divide-y divide-border">
                {list.map((s) => {
                  const locked = !canEdit || !s.editable;
                  const current = draft[s.key] ?? s.value ?? "";
                  const dirty = draft[s.key] !== undefined && draft[s.key] !== (s.value ?? "");
                  return (
                    <div
                      key={s.key}
                      className="flex flex-col gap-3 px-4 py-3.5 sm:flex-row sm:items-center"
                    >
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="text-sm font-medium">
                            {s.label ?? s.key}
                          </span>
                          {s.platformOnly && (
                            <span className="inline-flex items-center gap-1 rounded-md bg-muted px-1.5 py-0.5 text-[10px] font-semibold uppercase text-muted-foreground">
                              <Lock className="h-3 w-3" /> Platform
                            </span>
                          )}
                          {s.inherited && (
                            <span className="rounded-md bg-sky-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-sky-700 dark:bg-sky-500/15 dark:text-sky-300">
                              Default
                            </span>
                          )}
                        </div>
                        <code className="mt-0.5 block truncate font-mono text-[11px] text-muted-foreground">
                          {s.key}
                        </code>
                        {s.description && (
                          <p className="mt-1 text-xs text-muted-foreground">{s.description}</p>
                        )}
                      </div>

                      <div className="flex shrink-0 items-center gap-2">
                        {s.valueType === "BOOLEAN" ? (
                          <Select
                            className="w-[130px]"
                            aria-label={s.label ?? s.key}
                            disabled={locked}
                            value={current || "false"}
                            onChange={(e) =>
                              setDraft((d) => ({ ...d, [s.key]: e.target.value }))
                            }
                          >
                            <option value="true">Enabled</option>
                            <option value="false">Disabled</option>
                          </Select>
                        ) : (
                          <Input
                            className="w-[160px]"
                            aria-label={s.label ?? s.key}
                            disabled={locked}
                            inputMode={
                              s.valueType === "INT" || s.valueType === "DECIMAL"
                                ? "numeric"
                                : undefined
                            }
                            value={current}
                            onChange={(e) =>
                              setDraft((d) => ({ ...d, [s.key]: e.target.value }))
                            }
                          />
                        )}
                        <Button
                          size="sm"
                          disabled={locked || !dirty || save.isPending}
                          onClick={() => save.mutate({ key: s.key, value: current })}
                        >
                          <Save className="mr-1.5 h-3.5 w-3.5" />
                          Save
                        </Button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------- dropdowns */

function DropdownsTab({
  sets,
  loading,
  canEdit
}: {
  sets: OptionSetSummary[] | undefined;
  loading: boolean;
  canEdit: boolean;
}) {
  const [selected, setSelected] = useState<string | null>(null);
  const [query, setQuery] = useState("");

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (sets ?? []).filter(
      (s) =>
        !q ||
        s.name.toLowerCase().includes(q) ||
        s.code.toLowerCase().includes(q) ||
        (s.module ?? "").toLowerCase().includes(q)
    );
  }, [sets, query]);

  if (loading) return <LoadingRows />;

  return (
    <div>
      <div className="mb-4 max-w-sm">
        <Label htmlFor="set-search">Search dropdowns</Label>
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            id="set-search"
            className="pl-9"
            placeholder="Name, code or module"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
      </div>

      {visible.length === 0 && <EmptyState message="No dropdowns match that search." />}

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {visible.map((s) => (
          <Card key={s.code} className="transition hover:shadow-md">
            <CardContent className="p-4">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <h3 className="truncate text-sm font-semibold">{s.name}</h3>
                  <code className="mt-0.5 block truncate font-mono text-[11px] text-muted-foreground">
                    {s.code}
                  </code>
                </div>
                {s.systemSet && (
                  <span
                    title="The application reads this set by code, so it cannot be removed."
                    className="inline-flex shrink-0 items-center gap-1 rounded-md bg-muted px-1.5 py-0.5 text-[10px] font-semibold uppercase text-muted-foreground"
                  >
                    <Lock className="h-3 w-3" /> System
                  </span>
                )}
              </div>
              <div className="mt-3 flex items-center justify-between">
                <span className="text-xs text-muted-foreground">
                  {s.optionCount} {s.optionCount === 1 ? "value" : "values"}
                  {s.module ? ` · ${s.module}` : ""}
                </span>
                <Button size="sm" variant="outline" onClick={() => setSelected(s.code)}>
                  Manage
                </Button>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {selected && (
        <OptionSetDialog
          setCode={selected}
          canEdit={canEdit}
          onClose={() => setSelected(null)}
        />
      )}
    </div>
  );
}

function OptionSetDialog({
  setCode,
  canEdit,
  onClose
}: {
  setCode: string;
  canEdit: boolean;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [newCode, setNewCode] = useState("");
  const [newLabel, setNewLabel] = useState("");

  const detail = useQuery({
    queryKey: ["config", "option-set", setCode],
    queryFn: async () =>
      (
        await api.get<ApiEnvelope<OptionSetDetail>>(
          `/config/option-sets/${encodeURIComponent(setCode)}?activeOnly=false`
        )
      ).data.data
  });

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ["config", "option-set", setCode] });
    queryClient.invalidateQueries({ queryKey: ["config", "option-sets"] });
  };

  const saveOption = useMutation({
    mutationFn: async (payload: {
      code: string;
      label: string;
      sortOrder?: number;
      active?: boolean;
      isDefault?: boolean;
    }) =>
      (
        await api.post<ApiEnvelope<ConfigOption>>(
          `/config/option-sets/${encodeURIComponent(setCode)}/options`,
          payload
        )
      ).data.data,
    onSuccess: () => {
      toast.success("Saved");
      setNewCode("");
      setNewLabel("");
      refresh();
    },
    onError: (err) => toast.error(apiMessage(err, "Could not save the value"))
  });

  const deactivate = useMutation({
    mutationFn: async (code: string) => {
      await api.delete(
        `/config/option-sets/${encodeURIComponent(setCode)}/options/${encodeURIComponent(code)}`
      );
    },
    onSuccess: () => {
      toast.success("Value deactivated");
      refresh();
    },
    onError: (err) => toast.error(apiMessage(err, "Could not deactivate the value"))
  });

  const options = detail.data?.options ?? [];

  return (
    <Dialog open onClose={onClose} className="max-w-2xl">
      <DialogHeader
        title={detail.data?.name ?? "Dropdown values"}
        description={detail.data?.description ?? setCode}
      />

      <div className="max-h-[52vh] overflow-y-auto">
        {detail.isLoading ? (
          <div className="space-y-2 p-1">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : options.length === 0 ? (
          <EmptyState message="This dropdown has no values yet." />
        ) : (
          <div className="divide-y divide-border rounded-lg border border-border">
            {options.map((o) => (
              <div key={o.code} className="flex items-center gap-3 px-3 py-2.5">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span
                      className={
                        "text-sm " + (o.active ? "font-medium" : "text-muted-foreground line-through")
                      }
                    >
                      {o.label}
                    </span>
                    {o.isDefault && (
                      <span className="inline-flex items-center gap-1 rounded-md bg-emerald-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300">
                        <Star className="h-3 w-3" /> Default
                      </span>
                    )}
                    {!o.active && (
                      <span className="rounded-md bg-muted px-1.5 py-0.5 text-[10px] font-semibold uppercase text-muted-foreground">
                        Inactive
                      </span>
                    )}
                  </div>
                  <code className="font-mono text-[11px] text-muted-foreground">{o.code}</code>
                </div>

                {canEdit && (
                  <div className="flex shrink-0 items-center gap-1.5">
                    {!o.isDefault && o.active && (
                      <Button
                        size="sm"
                        variant="ghost"
                        title="Make this the default"
                        onClick={() =>
                          saveOption.mutate({
                            code: o.code,
                            label: o.label,
                            sortOrder: o.sortOrder,
                            active: true,
                            isDefault: true
                          })
                        }
                      >
                        <Star className="h-3.5 w-3.5" />
                      </Button>
                    )}
                    {o.active ? (
                      <Button
                        size="sm"
                        variant="ghost"
                        title="Deactivate — existing records keep this value"
                        onClick={() => deactivate.mutate(o.code)}
                      >
                        <Ban className="h-3.5 w-3.5 text-destructive" />
                      </Button>
                    ) : (
                      <Button
                        size="sm"
                        variant="ghost"
                        title="Reactivate"
                        onClick={() =>
                          saveOption.mutate({
                            code: o.code,
                            label: o.label,
                            sortOrder: o.sortOrder,
                            active: true
                          })
                        }
                      >
                        Restore
                      </Button>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {canEdit && (
        <div className="mt-4 rounded-lg border border-border bg-muted/40 p-3">
          <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Add a value
          </h4>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
            <div className="flex-1">
              <Label htmlFor="opt-label">Label</Label>
              <Input
                id="opt-label"
                placeholder="Shown to employees"
                value={newLabel}
                onChange={(e) => {
                  setNewLabel(e.target.value);
                  /* The code is derived so it is always a valid identifier;
                     it stays editable for anyone who wants a specific one. */
                  setNewCode(slugCode(e.target.value));
                }}
              />
            </div>
            <div className="flex-1">
              <Label htmlFor="opt-code">Code</Label>
              <Input
                id="opt-code"
                className="font-mono"
                placeholder="STORED_VALUE"
                value={newCode}
                onChange={(e) => setNewCode(slugCode(e.target.value))}
              />
            </div>
            <Button
              disabled={!newCode.trim() || !newLabel.trim() || saveOption.isPending}
              onClick={() =>
                saveOption.mutate({
                  code: newCode.trim(),
                  label: newLabel.trim(),
                  sortOrder: (options.at(-1)?.sortOrder ?? 0) + 1,
                  active: true
                })
              }
            >
              <Plus className="mr-1.5 h-4 w-4" />
              Add
            </Button>
          </div>
          <p className="mt-2 text-[11px] text-muted-foreground">
            Removing a value deactivates it. Records that already use it keep
            their value; new records can no longer choose it.
          </p>
        </div>
      )}
    </Dialog>
  );
}

/* ----------------------------------------------------------------- roles */

function RolesTab({ rows, loading }: { rows: RoleRow[] | undefined; loading: boolean }) {
  const [query, setQuery] = useState("");

  /* Every permission any role holds, so the matrix has a stable column set
     rather than one that shifts with whichever roles are on screen. */
  const allPermissions = useMemo(
    () => Array.from(new Set((rows ?? []).flatMap((r) => r.permissions))).sort(),
    [rows]
  );

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (rows ?? []).filter(
      (r) =>
        !q || r.code.toLowerCase().includes(q) || r.name.toLowerCase().includes(q)
    );
  }, [rows, query]);

  if (loading) return <LoadingRows />;

  return (
    <div>
      <div className="mb-4 max-w-sm">
        <Label htmlFor="role-search">Search roles</Label>
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            id="role-search"
            className="pl-9"
            placeholder="Role name or code"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
      </div>

      <div className="mb-4 rounded-lg border border-sky-200 bg-sky-50/70 px-4 py-3 dark:border-sky-500/30 dark:bg-sky-500/10">
        <p className="text-xs text-sky-900 dark:text-sky-200">
          This is what the server currently enforces, read from the live role
          grants. Editing role permissions is not done here — it changes what
          every account in that role can reach, so it stays with the platform
          administrator.
        </p>
      </div>

      {visible.length === 0 && <EmptyState message="No roles match that search." />}

      {/* The matrix is wide; it scrolls inside its own container so the page
          itself never scrolls sideways. */}
      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/50">
                  <th className="sticky left-0 z-10 bg-muted/50 px-4 py-2.5 text-left font-semibold">
                    Role
                  </th>
                  {allPermissions.map((p) => (
                    <th
                      key={p}
                      className="whitespace-nowrap px-2 py-2.5 text-center font-mono text-[10px] font-semibold uppercase text-muted-foreground"
                    >
                      {p.replace(/_/g, " ")}
                    </th>
                  ))}
                  <th className="px-3 py-2.5 text-right font-semibold">Total</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((r) => (
                  <tr key={r.id} className="border-b border-border last:border-0 hover:bg-muted/30">
                    <td className="sticky left-0 z-10 bg-card px-4 py-2.5">
                      <div className="font-medium">{r.name}</div>
                      <code className="font-mono text-[11px] text-muted-foreground">
                        {r.code}
                        {r.companyId === null ? " · shared" : ""}
                      </code>
                    </td>
                    {allPermissions.map((p) => {
                      const held = r.permissions.includes(p);
                      return (
                        <td key={p} className="px-2 py-2.5 text-center">
                          <span
                            aria-label={held ? `${r.code} has ${p}` : `${r.code} does not have ${p}`}
                            className={
                              "inline-block h-4 w-4 rounded " +
                              (held
                                ? "bg-emerald-500"
                                : "border border-border bg-transparent")
                            }
                          />
                        </td>
                      );
                    })}
                    <td className="px-3 py-2.5 text-right font-semibold tabular-nums">
                      {r.permissions.length}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

/* ------------------------------------------------------------------ bits */

function LoadingRows() {
  return (
    <div className="space-y-3">
      <Skeleton className="h-12 w-full" />
      <Skeleton className="h-12 w-full" />
      <Skeleton className="h-12 w-full" />
      <Skeleton className="h-12 w-full" />
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="rounded-lg border border-dashed border-border py-10 text-center text-sm text-muted-foreground">
      {message}
    </div>
  );
}

function titleCase(s: string): string {
  return s
    .toLowerCase()
    .split(/[\s_]+/)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

/** A label turned into a storable code: upper case, underscores, no padding. */
export function slugCode(label: string): string {
  return label
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 80);
}
