import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import { useState, useRef, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Map, Plus, Settings, Upload, ImagePlus, Pencil, Clock, Check, X } from "lucide-react";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";

const EXPENSE_CATEGORIES = [
  "Petrol",
  "House Rent",
  "Snacks",
  "Room",
  "Construction Things",
  "Others"
];
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow
} from "@/components/ui/table";
import { useAuth } from "@/hooks/useAuth";
import { resolvePhotoUrl } from "@/components/ui/avatar";
import { ClaimInvoice } from "@/components/ClaimInvoice";
import { StatTile, TILE_FILLS } from "@/components/ui/stat-tile";
import { usePagedRows, TablePagination } from "@/components/ui/table-pagination";

const inr = (n: number) =>
  "₹" + (Number(n) || 0).toLocaleString("en-IN", { maximumFractionDigits: 0 });
import { Eye } from "lucide-react";
import dayjs from "dayjs";

export default function TaExpensesPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { user, hasPermission, hasRole } = useAuth();

  // HR and admins review every claim; a Team Leader may view their team's but
  // not act on them; everyone else sees only their own.
  const canApprove = hasPermission("USER_MANAGE", "CLAIM_APPROVE", "DASHBOARD_EXEC");
  const isTeamLeader = hasRole("IT_TL") && !canApprove;
  const canManage = canApprove;
  // Correcting someone else's claim is HR's job alone. Admin and the company
  // head look at the same list, but read-only.
  const isCompanyHead = user?.employeeCode === "PIX-E100";
  const canEditAnyClaim =
    hasRole("IT_MGR", "IT_HR", "CV_HR") && !isCompanyHead && !hasPermission("USER_MANAGE");
  const [scope, setScope] = useState<"MINE" | "TEAM">("MINE");
  const [statusTab, setStatusTab] = useState<"ALL" | "PENDING" | "APPROVED" | "REJECTED">("ALL");
  const [q, setQ] = useState("");
  const [showSettings, setShowSettings] = useState(false);
  const [viewRow, setViewRow] = useState<any | null>(null);
  const [decideRow, setDecideRow] = useState<any | null>(null);

  // Settings
  const settingsQuery = useQuery({
    queryKey: ["settings"],
    queryFn: async () => (await api.get("/settings")).data.data
  });

  const listKey = canApprove ? "all" : isTeamLeader && scope === "TEAM" ? "team" : "me";
  const taList = useQuery({
    queryKey: ["ta-expenses", listKey],
    queryFn: async () => (await api.get(`/ta-expenses/${listKey}`)).data.data
  });

  const updateStatus = useMutation({
    mutationFn: async ({ id, status, comment }: { id: number; status: string; comment?: string }) => {
      await api.put(`/ta-expenses/${id}/status`, { status, comment });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["ta-expenses"] });
      setDecideRow(null);
      toast.success("Status updated");
    }
  });

  // Status tabs + search over whichever list this role is looking at.
  const rows = useMemo(() => {
    const all: any[] = taList.data ?? [];
    const needle = q.trim().toLowerCase();
    return all.filter((r) => {
      if (statusTab !== "ALL" && r.status !== statusTab) return false;
      if (!needle) return true;
      return `${r.userName ?? ""} ${r.employeeCode ?? ""} ${r.category ?? ""} ${r.location ?? ""}`
        .toLowerCase().includes(needle);
    });
  }, [taList.data, statusTab, q]);

  // Paged like every other table, with the numbers and rows-per-page.
  const paged = usePagedRows(rows, 15, [statusTab, q, scope, taList.data]);

  const counts = useMemo(() => {
    const all: any[] = taList.data ?? [];
    return {
      ALL: all.length,
      PENDING: all.filter((r) => r.status === "PENDING").length,
      APPROVED: all.filter((r) => r.status === "APPROVED").length,
      REJECTED: all.filter((r) => r.status === "REJECTED").length
    };
  }, [taList.data]);

  // Money actually granted — more useful on the Approved tile than a bare count.
  const approvedTotal = useMemo(
    () => (taList.data ?? [])
      .filter((r: any) => r.status === "APPROVED")
      .reduce((s: number, r: any) => s + (Number(r.grossTotal) || 0), 0),
    [taList.data]
  );

  return (
    <div>
      <PageHeader
        title="Claims"
        subtitle={
          canApprove ? "Every employee's expense claims — review and decide."
            : isTeamLeader && scope === "TEAM" ? "Your team's claims — view only."
              : "Travel allowance and expense claims."
        }
        actions={
          !canApprove ? (
            <Button onClick={() => navigate("/ta-expenses/new")}>
              <Plus className="mr-2 h-4 w-4" />
              Add Entry
            </Button>
          ) : null
        }
      />

      {/* A Team Leader switches between their own claims and their team's */}
      {isTeamLeader && (
          <div className="mb-4 inline-flex rounded-full border bg-muted/60 p-1">
            {([["MINE", "My claims"], ["TEAM", "Team claims"]] as const).map(([key, label]) => (
              <button
                key={key}
                type="button"
                onClick={() => setScope(key)}
                className={
                  "rounded-full px-4 py-1.5 text-xs font-semibold transition-colors " +
                  (scope === key ? "bg-primary text-primary-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground")
                }
              >
                {label}
              </button>
            ))}
          </div>
      )}

      {/* Counts for whichever list is open — each tile is also its filter. */}
      <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <StatTile
              label="All claims" value={counts.ALL} icon={Map} fill={TILE_FILLS.violet}
              hint={canApprove ? "Across every team"
                : scope === "TEAM" ? "Raised by your team" : "Raised by you"}
              active={statusTab === "ALL"} onClick={() => setStatusTab("ALL")}
            />
            <StatTile
              label="Pending" value={counts.PENDING} icon={Clock} fill={TILE_FILLS.amber}
              hint={counts.PENDING > 0 ? "Waiting on HR" : "Nothing waiting"}
              active={statusTab === "PENDING"} onClick={() => setStatusTab("PENDING")}
            />
            <StatTile
              label="Approved" value={counts.APPROVED} icon={Check} fill={TILE_FILLS.green}
              hint={approvedTotal > 0 ? inr(approvedTotal) + " approved" : "Nothing approved yet"}
              active={statusTab === "APPROVED"} onClick={() => setStatusTab("APPROVED")}
            />
            <StatTile
              label="Rejected" value={counts.REJECTED} icon={X} fill={TILE_FILLS.red}
              hint="Turned down" active={statusTab === "REJECTED"}
          onClick={() => setStatusTab("REJECTED")}
        />
      </div>

      <Card>
        <CardContent className="p-0 overflow-x-auto">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b p-4">
            {/* The tiles above are the status filter for every role. */}
            <div className="hidden">
              {(["ALL", "PENDING", "APPROVED", "REJECTED"] as const).map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => setStatusTab(t)}
                  className={
                    "rounded-full px-3.5 py-1 text-xs font-semibold transition-colors " +
                    (statusTab === t ? "bg-primary text-primary-foreground shadow-sm"
                      : "text-muted-foreground hover:text-foreground")
                  }
                >
                  {t.charAt(0) + t.slice(1).toLowerCase()} {counts[t]}
                </button>
              ))}
            </div>
            <Input
              placeholder={
                canApprove || (isTeamLeader && scope === "TEAM")
                  ? "Search employee, category or location…"
                  : "Search category or location…"
              }
              className="max-w-xs"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </div>

          {taList.isLoading ? (
             <div className="p-4 text-sm text-muted-foreground">Loading...</div>
          ) : rows.length === 0 ? (
            <div className="p-8">
              <EmptyState
                icon={Map}
                title={statusTab === "ALL" ? "No claims" : `No ${statusTab.toLowerCase()} claims`}
                description={
                  statusTab !== "ALL"
                    ? "Pick another status above to see the rest."
                    : canApprove ? "Employee claims will appear here."
                      : isTeamLeader && scope === "TEAM" ? "Your team hasn't raised any claims yet."
                        : "Add your first claim using the button above."
                }
              />
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Employee</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead>Location</TableHead>
                  <TableHead>Bus Fare</TableHead>
                  <TableHead>Others</TableHead>
                  <TableHead>Gross</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Decided by</TableHead>
                  <TableHead></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {paged.pageRows.map((row: any) => (
                  <TableRow key={row.id}>
                    <TableCell className="whitespace-nowrap">{dayjs(row.date).format("DD.MM.YYYY")}</TableCell>
                    <TableCell>
                      <div className="font-medium">{row.userName}</div>
                      {row.employeeCode && (
                        <div className="code-chip text-xs text-muted-foreground">
                          {row.employeeCode}{row.team ? ` · ${row.team}` : ""}
                        </div>
                      )}
                    </TableCell>
                    <TableCell>
                      {row.category ? <Badge variant="secondary">{row.category}</Badge> : "—"}
                    </TableCell>
                    <TableCell>{row.location}</TableCell>
                    <TableCell>{row.busFare}</TableCell>
                    <TableCell>{row.others}</TableCell>
                    <TableCell className="font-bold">{row.grossTotal}</TableCell>
                    <TableCell>
                      <Badge variant={row.status === 'APPROVED' ? 'default' : row.status === 'REJECTED' ? 'destructive' : 'secondary'}>
                        {row.status}
                      </Badge>
                      {row.status === "REJECTED" && row.decisionComment && (
                        <div className="mt-0.5 max-w-[180px] truncate text-[11px] text-muted-foreground"
                          title={row.decisionComment}>
                          {row.decisionComment}
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-xs text-muted-foreground">
                      {row.decidedByName || "—"}
                      {row.decidedAt && (
                        <div className="text-[10px]">{dayjs(row.decidedAt).format("DD MMM, h:mm A")}</div>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-1">
                        <Button size="sm" variant="ghost" className="text-primary hover:bg-primary/10" onClick={() => setViewRow(row)}>
                          <Eye className="mr-1 h-4 w-4" /> View
                        </Button>
                        {canApprove && row.status === "PENDING" && (
                          <Button size="sm" variant="outline" onClick={() => setDecideRow(row)}>
                            Review
                          </Button>
                        )}
                        {/* Claims can only be edited while PENDING, by HR or the creator */}
                        {(row.status === "PENDING" && (canEditAnyClaim || (!canApprove && scope === "MINE"))) && (
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => navigate(`/ta-expenses/${row.id}/edit`)}
                          >
                            <Pencil className="mr-1 h-3.5 w-3.5" /> Edit
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
        {rows.length > 0 && (
          <TablePagination
            page={paged.page} totalPages={paged.totalPages} onChange={paged.setPage}
            pageSize={paged.pageSize} onPageSizeChange={paged.setPageSize}
            total={paged.total}
            always
          />
        )}
      </Card>

      {showSettings && (
        <TaSettingsModal
           onClose={() => setShowSettings(false)}
           settings={settingsQuery.data}
        />
      )}

      {/* One invoice view for everyone — HR's Review opens the same document with
          the decision panel enabled. */}
      {(viewRow || decideRow) && (
        <ClaimInvoice
          row={decideRow || viewRow}
          canApprove={canApprove && Boolean(decideRow)}
          onClose={() => { setViewRow(null); setDecideRow(null); }}
          onDecide={(status, comment) =>
            updateStatus.mutate({ id: (decideRow || viewRow).id, status, comment })}
          pending={updateStatus.isPending}
        />
      )}
    </div>
  );
}

function TaSettingsModal({ onClose, settings }: { onClose: () => void, settings: any }) {
  const queryClient = useQueryClient();
  const [hills, setHills] = useState(settings?.HILLS_KM_RATE || "");
  const [plains, setPlains] = useState(settings?.PLAINS_KM_RATE || "");

  const saveSettings = useMutation({
    mutationFn: async () => {
      await api.post("/settings", {
        HILLS_KM_RATE: hills,
        PLAINS_KM_RATE: plains
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["settings"] });
      toast.success("Settings saved");
      onClose();
    }
  });

  return (
    <Dialog open={true} onClose={onClose}>
      <DialogHeader title="TA Settings" />
      <div className="space-y-4 mt-4">
        <div>
          <label className="text-sm">Hills KM Rate (₹)</label>
          <Input type="number" value={hills} onChange={e => setHills(e.target.value)} />
        </div>
        <div>
          <label className="text-sm">Plains KM Rate (₹)</label>
          <Input type="number" value={plains} onChange={e => setPlains(e.target.value)} />
        </div>
      </div>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button onClick={() => saveSettings.mutate()}>Save Settings</Button>
      </div>
    </Dialog>
  );
}
