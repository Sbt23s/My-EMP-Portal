import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import { useState, useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import { useForm } from "react-hook-form";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import QRCode from "react-qr-code";
import * as XLSX from "xlsx";
import dayjs from "dayjs";
import {
  Plus, Boxes, QrCode, CheckCircle2, PackageCheck, PackageX, Trash2,
  Download, Eye
} from "lucide-react";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { todayIso } from "@/lib/dates";
import { useAuth } from "@/hooks/useAuth";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { PageLoader } from "@/components/ui/page-loader";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge, statusVariant } from "@/components/ui/badge";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow
} from "@/components/ui/table";
import type { ApiEnvelope, PageEnvelope, Asset, UserSummary } from "@/types";
import { StatTile, TILE_FILLS } from "@/components/ui/stat-tile";
import { usePagedRows, TablePagination } from "@/components/ui/table-pagination";
import { Search, UserCheck, Wrench } from "lucide-react";

export default function AssetsPage() {
  const qc = useQueryClient();
  const { hasPermission, hasRole } = useAuth();
  // HR runs the inventory — registering, allocating and retiring equipment. An
  // admin oversees it and reads the same list without acting on it.
  const canView = hasPermission("ASSET_MANAGE");
  const isAdmin = hasRole("SUPER_ADMIN") || hasRole("COMPANY_ADMIN") || hasPermission("USER_MANAGE");
  const canManage = canView && !isAdmin;
  const [invSearch, setInvSearch] = useState("");
  const [invStatus, setInvStatus] = useState("ALL");
  const [invCategory, setInvCategory] = useState("ALL");
  const [invHolder, setInvHolder] = useState("ALL");
  // The asset a holder has opened to read in full.
  const [myView, setMyView] = useState<Asset | null>(null);
  const [invPage, setInvPage] = useState(0);
  const [qrAsset, setQrAsset] = useState<Asset | null>(null);
  const [allocateAsset, setAllocateAsset] = useState<Asset | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Asset | null>(null);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);

  const [searchParams, setSearchParams] = useSearchParams();
  const lookupCode = searchParams.get("code");

  const lookupAsset = useQuery({
    queryKey: ["assets", "lookup", lookupCode],
    enabled: !!lookupCode,
    queryFn: async () => {
      const res = await api.get<ApiEnvelope<Asset>>(`/assets/lookup?code=${lookupCode}`);
      return res.data.data;
    }
  });

  const mine = useQuery({
    queryKey: ["assets", "mine"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<Asset[]>>("/assets/my-assets")).data.data
  });

  const inventory = useQuery({
    queryKey: ["assets", "inventory"],
    enabled: canView,
    queryFn: async () => {
      const res = await api.get<PageEnvelope<Asset>>("/assets?size=1000");
      return res.data?.content || [];
    }
  });

  const acknowledge = useMutation({
    mutationFn: async (id: number) => api.post(`/assets/${id}/acknowledge`),
    onSuccess: () => {
      toast.success("Receipt acknowledged");
      qc.invalidateQueries({ queryKey: ["assets"] });
    },
    onError: (err) => toast.error(apiMessage(err, "Could not acknowledge"))
  });

  const deleteAsset = useMutation({
    mutationFn: async (id: number) => api.delete(`/assets/${id}`),
    onSuccess: () => {
      toast.success("Asset deleted");
      qc.invalidateQueries({ queryKey: ["assets"] });
      setDeleteTarget(null);
    },
    onError: (err) => toast.error(apiMessage(err, "Could not delete asset"))
  });

  /** The inventory as the table shows it: searched, filtered, then paged. */
  const invAll = inventory.data ?? [];
  const invStats = useMemo(() => {
    const by = (fn: (a: Asset) => boolean) => invAll.filter(fn).length;
    const stock = (a: Asset) => a.status === "IN_STOCK" || a.status === "AVAILABLE";
    // Out of stock means there is none left to give out: the status says so, or
    // the count has reached zero. Both are the same thing to whoever is asking.
    const outOfStock = (a: Asset) => a.status === "OUT_OF_STOCK" || (a.quantity ?? 1) <= 0;
    return {
      total: invAll.length,
      units: invAll.reduce((n, a) => n + (a.quantity ?? 1), 0),
      inStock: by((a) => stock(a) && !outOfStock(a)),
      allocated: by((a) => a.status === "ALLOCATED" || a.status === "ASSIGNED" || !!a.assignedTo),
      outOfStock: by(outOfStock)
    };
  }, [invAll]);

  // Names for the "Allocated to" column and filter. The allocate dialog already
  // reads this list, so it is usually in cache by the time it is needed here.
  const peopleQ = useQuery({
    enabled: canView,
    queryKey: ["employees"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<{ content: UserSummary[] }>>("/users?size=1000")).data.data.content ?? []
  });
  const nameById = useMemo(() => {
    const m = new Map<number, string>();
    (peopleQ.data ?? []).forEach((u) => m.set(u.id, u.name));
    return m;
  }, [peopleQ.data]);

  /** Everyone currently holding at least one asset, with how many. */
  const holders = useMemo(() => {
    const counts = new Map<number, number>();
    invAll.forEach((a) => {
      if (a.assignedTo) counts.set(a.assignedTo, (counts.get(a.assignedTo) ?? 0) + 1);
    });
    return Array.from(counts, ([id, count]) => ({
      id, count, name: nameById.get(id) ?? `#${id}`
    })).sort((x, y) => x.name.localeCompare(y.name));
  }, [invAll, nameById]);

  const invCategories = useMemo(
    () => [...new Set(invAll.map((a) => a.category).filter(Boolean))].sort(),
    [invAll]
  );
  const invStatuses = useMemo(
    () => [...new Set(invAll.map((a) => a.status).filter(Boolean))].sort(),
    [invAll]
  );

  const invRows = useMemo(() => {
    const needle = invSearch.trim().toLowerCase();
    return invAll.filter((a) => {
      if (invStatus !== "ALL" && a.status !== invStatus) return false;
      if (invCategory !== "ALL" && a.category !== invCategory) return false;
      if (invHolder !== "ALL" && String(a.assignedTo ?? "") !== invHolder) return false;
      if (!needle) return true;
      return `${a.assetCode ?? ""} ${a.assetType ?? ""} ${a.brand ?? ""} ${a.model ?? ""} ${a.serialNumber ?? ""}`
        .toLowerCase().includes(needle);
    });
  }, [invAll, invSearch, invStatus, invCategory, invHolder]);

  // The shared hook rather than a hand-rolled slice, so the inventory gains the
  // page numbers and the rows-per-page choice like every other table.
  const invPaged = usePagedRows(invRows, 15, [invSearch, invStatus, invCategory, invHolder]);
  const invPageRows = invPaged.pageRows;

  // The employee's own equipment pages the same way. Somebody holding a laptop,
  // a phone, a headset and a monitor is common; a long list here is not.
  const minePaged = usePagedRows(mine.data ?? [], 15, [mine.data]);

  return (
    <div>
      <PageHeader
        title="Assets"
        subtitle={isAdmin
          ? "The full equipment inventory — view and export; HR registers and allocates."
          : canManage
            ? "Register equipment, allocate it, and print QR tags."
            : "Equipment assigned to you, with QR tags. HR registers and allocates."}
        actions={
          canView ? (
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => setExportOpen(true)}>
                <Download className="h-4 w-4 mr-2" /> Export
              </Button>
              {canManage && (
                <Button onClick={() => setRegisterOpen(true)}>
                  <Plus className="h-4 w-4" /> Register asset
                </Button>
                <tr className="border-b bg-muted/20 text-left text-[11px] uppercase tracking-wide text-muted-foreground">
                  <th className="w-14 px-4 py-2.5">S.No</th>
                  <th className="px-4 py-2.5">Asset</th>
                  <th className="px-4 py-2.5">Asset Code</th>
                  <th className="px-4 py-2.5">Brand / Model</th>
                  <th className="px-4 py-2.5">Warranty</th>
                  <th className="px-4 py-2.5">Purchased</th>
                  <th className="px-4 py-2.5 text-right">Details</th>
                </tr>
              </thead>
              <tbody>
                {minePaged.pageRows.map((a, i) => (
                  <tr key={a.id} className="border-b align-middle last:border-0 hover:bg-muted/20">
                    <td className="px-4 py-2.5 text-muted-foreground">
                      {minePaged.page * minePaged.pageSize + i + 1}
                    </td>
                    <td className="px-4 py-2.5 font-medium">{a.assetType || a.category}</td>
                    <td className="px-4 py-2.5">
                      <span className="code-chip text-xs text-muted-foreground">{a.assetCode}</span>
                    </td>
                    <td className="px-4 py-2.5 text-muted-foreground">
                      {[a.brand, a.model].filter(Boolean).join(" ") || "—"}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2.5 text-muted-foreground">
                      {warrantyLabel(a.warrantyExpiry)}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2.5 text-muted-foreground">
                      {a.purchaseDate ? dayjs(a.purchaseDate).format("DD MMM YYYY") : "—"}
                    </td>
                    <td className="px-4 py-2.5 text-right">
                      <Button variant="outline" size="sm" onClick={() => setMyView(a)}>
                        <Eye className="mr-1 h-3.5 w-3.5" /> View
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <TablePagination
              page={minePaged.page} totalPages={minePaged.totalPages} onChange={minePaged.setPage}
              pageSize={minePaged.pageSize} onPageSizeChange={minePaged.setPageSize}
              total={minePaged.total}
              always
            />
          </CardContent>
        </Card>
      ))}

      {/* Inventory — HR acts on it, an admin reads it. */}
      {canView && (
        <Card className="mt-8">
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <CardTitle>Inventory</CardTitle>
              <span className="text-xs text-muted-foreground">
                {invRows.length} of {invStats.total} asset{invStats.total === 1 ? "" : "s"}
                {" · "}{invStats.units} unit{invStats.units === 1 ? "" : "s"} in total
              </span>
            </div>

            <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <StatTile
                compact label="Total assets" value={invStats.total} icon={Boxes}
                fill={TILE_FILLS.violet} hint="Everything registered"
                active={invStatus === "ALL"} onClick={() => { setInvStatus("ALL"); setInvPage(0); }}
              />
              <StatTile
                compact label="In stock" value={invStats.inStock} icon={PackageCheck}
                fill={TILE_FILLS.green} hint="Ready to allocate"
              />
              <StatTile
                compact label="Allocated" value={invStats.allocated} icon={UserCheck}
                fill={TILE_FILLS.blue} hint="With an employee"
              />
              <StatTile
                compact label="Out of stock" value={invStats.outOfStock} icon={PackageX}
                fill={TILE_FILLS.amber} hint="Nothing left to allocate"
                active={invStatus === "OUT_OF_STOCK"}
                onClick={() => { setInvStatus(invStatus === "OUT_OF_STOCK" ? "ALL" : "OUT_OF_STOCK"); setInvPage(0); }}
              />
            </div>

            <div className="mt-3 flex flex-wrap items-end gap-3">
              <div className="relative w-full sm:w-72">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  placeholder="Search code, type, brand or serial…"
                  className="pl-9"
                  value={invSearch}
                  onChange={(e) => { setInvSearch(e.target.value); setInvPage(0); }}
                />
              </div>
              <div className="space-y-1">
                <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Status</label>
                <Select
                  value={invStatus}
                  onChange={(e) => { setInvStatus(e.target.value); setInvPage(0); }}
                  className="w-40"
                >
                  <option value="ALL">All statuses</option>
                  {invStatuses.map((v) => <option key={v} value={v}>{v.replace(/_/g, " ")}</option>)}
                </Select>
              </div>
              <div className="space-y-1">
                <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Category</label>
                <Select
                  value={invCategory}
                  onChange={(e) => { setInvCategory(e.target.value); setInvPage(0); }}
                  className="w-40"
                >
                  <option value="ALL">All categories</option>
                  {invCategories.map((v) => <option key={v} value={v}>{v}</option>)}
                </Select>
              </div>
              <div className="space-y-1">
                <label className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
                  Allocated to
                </label>
                <Select
                  className="h-10 w-56"
                  value={invHolder}
                  onChange={(e) => { setInvHolder(e.target.value); setInvPage(0); }}
                >
                  <option value="ALL">Everyone ({holders.length} holding)</option>
                  {holders.map((h) => (
                    <option key={h.id} value={String(h.id)}>
                      {h.name} — {h.count} asset{h.count === 1 ? "" : "s"}
                    </option>
                  ))}
                </Select>
              </div>
              {(invSearch || invStatus !== "ALL" || invCategory !== "ALL" || invHolder !== "ALL") && (
                <Button
                  variant="outline"
                  onClick={() => {
                    setInvSearch(""); setInvStatus("ALL"); setInvCategory("ALL");
                    setInvHolder("ALL"); setInvPage(0);
                  }}
                >
                  Reset
                </Button>
              )}
            </div>
          </CardHeader>
          <CardContent>
            {inventory.isLoading ? (
              <Skeleton className="h-52" />
            ) : invAll.length === 0 ? (
              <EmptyState title="No assets registered" description="Register your first asset to begin." />
            ) : invRows.length === 0 ? (
              <EmptyState title="Nothing matches these filters" description="Clear the filters above to see the whole inventory." />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Code</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead>Category</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Allocated to</TableHead>
                    <TableHead className="text-right">Stock</TableHead>
                    <TableHead>Warranty</TableHead>
                    <TableHead>Purchased</TableHead>
                    {canManage && <TableHead className="text-right">Action</TableHead>}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {invPageRows.map((a) => (
                    <TableRow key={a.id}>
                      <TableCell className="code-chip">{a.assetCode}</TableCell>
                      <TableCell>{a.assetType || "—"}</TableCell>
                      <TableCell>
                        <Badge variant="secondary">{a.category}</Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant={statusVariant(a.status)}>{a.status}</Badge>
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-sm">
                        {a.assignedTo ? (nameById.get(a.assignedTo) ?? `#${a.assignedTo}`)
                          : <span className="text-muted-foreground">—</span>}
                      </TableCell>
                      <TableCell className="text-right font-medium tabular-nums">{a.quantity ?? 1}</TableCell>
                      <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                        {warrantyLabel(a.warrantyExpiry)}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                        {a.purchaseDate ? dayjs(a.purchaseDate).format("DD MMM YYYY") : "—"}
                      </TableCell>
                      {canManage && (
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          {a.status === "IN_STOCK" && (
                            <Button variant="ghost" size="sm" onClick={() => setAllocateAsset(a)}>
                              <PackageCheck className="h-4 w-4" /> Allocate
                            </Button>
                          )}
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-red-600 hover:text-red-700 hover:bg-red-50 dark:hover:bg-red-950/30"
                            onClick={() => setDeleteTarget(a)}
                            title="Delete asset"
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
          {invAll.length > 0 && (
            <div className="border-t">
              <div className="px-4 py-2 text-xs text-muted-foreground">
                Showing {invPageRows.length} of {invRows.length} asset{invRows.length === 1 ? "" : "s"}
              </div>
              <TablePagination
                page={invPaged.page} totalPages={invPaged.totalPages} onChange={invPaged.setPage}
                pageSize={invPaged.pageSize} onPageSizeChange={invPaged.setPageSize}
                total={invPaged.total}
                always
              />
            </div>
          )}
        </Card>
      )}

      {/* QR dialog */}
      <Dialog open={qrAsset != null} onClose={() => setQrAsset(null)} className="max-w-sm">
        {qrAsset && (
          <div className="text-center">
            <DialogHeader title={qrAsset.assetType || qrAsset.category} />
            <div className="mx-auto w-fit rounded-lg bg-white p-4">
              <QRCode value={`${window.location.origin}/assets?code=${qrAsset.assetCode}`} size={180} />
            </div>
            <div className="code-chip mt-3 text-sm text-muted-foreground">{qrAsset.assetCode}</div>
            <p className="mt-2 text-xs text-muted-foreground">
              Scan to look up this asset's details and service history.
            </p>
          </div>
        )}
      </Dialog>

      {allocateAsset && (
        <AllocateDialog asset={allocateAsset} onClose={() => setAllocateAsset(null)} />
      )}

      {myView && <MyAssetView asset={myView} onClose={() => setMyView(null)} />}

      <Dialog open={deleteTarget != null} onClose={() => setDeleteTarget(null)} className="max-w-sm">
        {deleteTarget && (
          <div>
            <DialogHeader title="Delete asset?" />
            <p className="mt-2 text-sm text-muted-foreground">
              This permanently deletes{" "}
              <span className="font-medium text-foreground">{deleteTarget.assetCode}</span>
              {deleteTarget.assetType ? ` (${deleteTarget.assetType})` : ""} and its allocation
              history. This cannot be undone.
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <Button variant="outline" onClick={() => setDeleteTarget(null)}>
                Cancel
              </Button>
              <Button
                className="bg-red-600 text-white hover:bg-red-700"
                disabled={deleteAsset.isPending}
                onClick={() => deleteAsset.mutate(deleteTarget.id)}
              >
                {deleteAsset.isPending ? (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                ) : (
                  <Trash2 className="mr-2 h-4 w-4" />
                )}
                Delete
              </Button>
            </div>
          </div>
        )}
      </Dialog>
      {registerOpen && <RegisterDialog onClose={() => setRegisterOpen(false)} />}
      {exportOpen && <ExportDialog inventoryData={inventory.data ?? []} onClose={() => setExportOpen(false)} />}

      {lookupCode && (
        <Dialog open onClose={() => setSearchParams({})} className="max-w-md">
          {lookupAsset.isLoading ? (
            <div className="flex flex-col items-center justify-center p-8">
              <Loader2 className="h-8 w-8 animate-spin text-primary" />
              <p className="mt-2 text-sm text-muted-foreground">Loading asset details...</p>
            </div>
          ) : lookupAsset.isError ? (
            <div className="text-center p-4">
              <DialogHeader title="Asset Not Found" />
              <p className="text-sm text-destructive mt-2">Could not load details for asset "{lookupCode}".</p>
              <div className="mt-4 flex justify-end">
                <Button onClick={() => setSearchParams({})}>Close</Button>
              </div>
            </div>
          ) : lookupAsset.data ? (
            <div>
              <DialogHeader
                title={`${lookupAsset.data.brand || ""} ${lookupAsset.data.model || ""}`}
                description={`Asset Code: ${lookupAsset.data.assetCode}`}
              />
              <div className="mt-4 space-y-3 text-sm">
                <div className="flex justify-between border-b pb-1.5">
                  <span className="text-muted-foreground">Category</span>
                  <span className="font-medium">{lookupAsset.data.category}</span>
                </div>
                <div className="flex justify-between border-b pb-1.5">
                  <span className="text-muted-foreground">Type</span>
                  <span className="font-medium">{lookupAsset.data.assetType || "—"}</span>
                </div>
                <div className="flex justify-between border-b pb-1.5">
                  <span className="text-muted-foreground">Serial Number</span>
                  <span className="font-medium code-chip">{lookupAsset.data.serialNumber || "—"}</span>
                </div>
                <div className="flex justify-between border-b pb-1.5">
                  <span className="text-muted-foreground">Status</span>
                  <Badge variant={statusVariant(lookupAsset.data.status)}>{lookupAsset.data.status}</Badge>
                </div>
                <div className="flex justify-between border-b pb-1.5">
                  <span className="text-muted-foreground">Available Stock</span>
                  <span className="font-medium">{lookupAsset.data.quantity ?? 1}</span>
                </div>
              </div>
              <div className="mt-6 flex justify-end">
                <Button onClick={() => setSearchParams({})}>Close</Button>
              </div>
            </div>
          ) : null}
        </Dialog>
      )}
    </div>
  );
}

/**
 * How long is left on the warranty, in the words someone would use: a year and
 * a bit, a few months, or that it has already run out.
 */
function warrantyLabel(until?: string) {
  if (!until) return "—";
  const end = dayjs(until);
  const days = end.diff(dayjs(), "day");
  if (days < 0) return `Expired ${end.format("DD MMM YYYY")}`;
  const years = Math.floor(days / 365);
  const months = Math.round((days % 365) / 30);
  const left = years > 0
    ? `${years} yr${years === 1 ? "" : "s"}${months > 0 ? ` ${months} mo` : ""}`
    : `${Math.max(1, months)} mo`;
  return `${left} left · ${end.format("DD MMM YYYY")}`;
}

/** Everything recorded about an asset, for whoever is holding it. */
function MyAssetView({ asset, onClose }: { asset: Asset; onClose: () => void }) {
  const rows: [string, string][] = [
    ["Asset code", asset.assetCode || "—"],
    ["Type", asset.assetType || "—"],
    ["Category", asset.category || "—"],
    ["Brand", asset.brand || "—"],
    ["Model", asset.model || "—"],
    ["Serial number", asset.serialNumber || "—"],
    ["Date of purchase", asset.purchaseDate ? dayjs(asset.purchaseDate).format("DD MMM YYYY") : "—"],
    ["Warranty", warrantyLabel(asset.warrantyExpiry)],
    ["Status", asset.status || "—"]
  ];
  return (
    <Dialog open onClose={onClose} className="max-w-md">
      <DialogHeader
        title={asset.assetType || asset.category || "Asset"}
        description="Everything recorded about the equipment you are holding."
      />
      <dl className="divide-y text-sm">
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-start justify-between gap-4 py-2">
            <dt className="text-muted-foreground">{label}</dt>
            <dd className="text-right font-medium">{value}</dd>
          </div>
        ))}
      </dl>
      <div className="flex justify-end pt-3">
        <Button variant="outline" onClick={onClose}>Close</Button>
      </div>
    </Dialog>
  );
}

function AllocateDialog({ asset, onClose }: { asset: Asset; onClose: () => void }) {
  const qc = useQueryClient();
  const [userId, setUserId] = useState("");

  const employees = useQuery({
    queryKey: ["employees"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<{ content: UserSummary[] }>>("/users?size=1000")).data.data.content ?? []
  });

  const allocate = useMutation({
    mutationFn: async () =>
      api.post(`/assets/${asset.id}/allocate`, { userId: Number(userId) }),
    onSuccess: () => {
      toast.success("Asset allocated");
      qc.invalidateQueries({ queryKey: ["assets"] });
      onClose();
    },
    onError: (err) => toast.error(apiMessage(err, "Could not allocate"))
  });

  return (
    <Dialog open onClose={onClose} className="max-w-sm">
      <DialogHeader
        title={`Allocate ${asset.assetCode}`}
        description="Assign this asset to an employee by selecting their name."
      />
      <div className="space-y-3">
        <div className="space-y-1.5">
          <Label htmlFor="userId">Select Employee</Label>
          {employees.isLoading ? (
            <div className="flex items-center gap-2 text-xs text-muted-foreground h-10 border rounded-md px-3 bg-muted/20">
              <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading employees...
            </div>
          ) : (
            <Select
              id="userId"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
            >
              <option value="">Choose an employee...</option>
              {(employees.data ?? [])
                .filter((u) => (u.profileStatus || "ACTIVE") !== "OFFBOARDED")
                .map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.name} ({u.employeeCode})
                  </option>
                ))}
            </Select>
          )}
        </div>
        <div className="flex justify-end gap-2 pt-1">
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button disabled={!userId || allocate.isPending} onClick={() => allocate.mutate()}>
            {allocate.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Allocate
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

interface RegisterForm {
  category: string;
  assetType: string;
  brand: string;
  model: string;
  serialNumber: string;
  quantity: number;
  purchaseDate: string;
  warrantyExpiry: string;
}

function RegisterDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const { register, handleSubmit } = useForm<RegisterForm>({
    defaultValues: { category: "IT", quantity: 1 }
  });

  const create = useMutation({
    mutationFn: async (v: RegisterForm) => api.post("/assets", {
      ...v,
      // Both are optional; an empty date field must not be sent as "".
      purchaseDate: v.purchaseDate || undefined,
      warrantyExpiry: v.warrantyExpiry || undefined
    }),
    onSuccess: () => {
      toast.success("Asset registered");
      qc.invalidateQueries({ queryKey: ["assets"] });
      onClose();
    },
    onError: (err) => toast.error(apiMessage(err, "Could not register asset"))
  });

  return (
    <Dialog open onClose={onClose}>
      <DialogHeader
        title="Register asset"
        description="A unique asset code and QR tag are generated automatically."
      />
      <form onSubmit={handleSubmit((v) => create.mutate(v))} className="space-y-4">
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="category">Category</Label>
            <Select id="category" required {...register("category")}>
              <option value="IT">IT</option>
              <option value="INFRA">Infrastructure</option>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="assetType">Type</Label>
            <Input id="assetType" required placeholder="Laptop, Excavator…" {...register("assetType")} />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="brand">Brand</Label>
            <Input id="brand" required {...register("brand")} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="model">Model</Label>
            <Input id="model" required {...register("model")} />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="serialNumber">Serial number</Label>
            <Input id="serialNumber" className="code-chip" required {...register("serialNumber")} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="quantity">Quantity (Stock)</Label>
            <Input id="quantity" type="number" min="1" required {...register("quantity", { valueAsNumber: true })} />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="purchaseDate">Date of purchase</Label>
            {/* Bought in the past, so today is the latest it can be. */}
            <Input id="purchaseDate" type="date" max={todayIso()} {...register("purchaseDate")} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="warrantyExpiry">Warranty until</Label>
            <Input id="warrantyExpiry" type="date" {...register("warrantyExpiry")} />
            <p className="text-[11px] text-muted-foreground">
              The date cover runs out. Whoever holds the asset sees how long is left.
            </p>
          </div>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={create.isPending}>
            {create.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
            Register
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

function ExportDialog({ inventoryData, onClose }: { inventoryData: Asset[]; onClose: () => void }) {
  const [exportType, setExportType] = useState<"date" | "month">("date");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [selectedMonth, setSelectedMonth] = useState(""); // YYYY-MM

  const handleExport = () => {
    let filtered = [...inventoryData];

    if (exportType === "date") {
      if (startDate) {
        filtered = filtered.filter(a => a.createdAt && dayjs(a.createdAt).isAfter(dayjs(startDate).startOf('day')));
      }
      if (endDate) {
        filtered = filtered.filter(a => a.createdAt && dayjs(a.createdAt).isBefore(dayjs(endDate).endOf('day')));
      }
    } else if (exportType === "month" && selectedMonth) {
      filtered = filtered.filter(a => a.createdAt && dayjs(a.createdAt).format("YYYY-MM") === selectedMonth);
    }

    if (filtered.length === 0) {
      toast.error("No assets found for the selected filter.");
      return;
    }

    // Map details for excel sheet
    const excelData = filtered.map(a => ({
      "Asset Code": a.assetCode,
      "Category": a.category,
      "Type": a.assetType || "—",
      "Brand": a.brand || "—",
      "Model": a.model || "—",
      "Serial Number": a.serialNumber || "—",
      "Status": a.status,
      "Stock Quantity": a.quantity ?? 1,
      "Registration Date": a.createdAt ? dayjs(a.createdAt).format("YYYY-MM-DD HH:mm:ss") : "—"
    }));

    // Generate Excel sheet
    const worksheet = XLSX.utils.json_to_sheet(excelData);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "Assets");

    // Auto-adjust column widths
    const maxLens = Object.keys(excelData[0]).map(key => {
      return Math.max(
        key.length,
        ...excelData.map(row => String(row[key as keyof typeof row] || "").length)
      );
    });
    worksheet["!cols"] = maxLens.map(len => ({ wch: len + 3 }));

    XLSX.writeFile(workbook, `assets_export_${dayjs().format("YYYYMMDD_HHmmss")}.xlsx`);
    toast.success("Assets exported successfully!");
    onClose();
  };

  return (
    <Dialog open onClose={onClose} className="max-w-sm">
      <DialogHeader
        title="Export Assets"
        description="Filter assets by date range or month to export to Excel."
      />
      <div className="space-y-4 mt-3">
        <div className="flex gap-4 border-b pb-2">
          <button
            className={`text-sm font-semibold pb-1 border-b-2 transition-colors ${exportType === "date" ? "border-primary text-primary" : "border-transparent text-muted-foreground"}`}
            onClick={() => setExportType("date")}
          >
            Date Range
          </button>
          <button
            className={`text-sm font-semibold pb-1 border-b-2 transition-colors ${exportType === "month" ? "border-primary text-primary" : "border-transparent text-muted-foreground"}`}
            onClick={() => setExportType("month")}
          >
            Month Wise
          </button>
        </div>

        {exportType === "date" ? (
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="startDate">Start Date</Label>
              <Input id="startDate" type="date" value={startDate} onChange={e => setStartDate(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="endDate">End Date</Label>
              <Input id="endDate" type="date" value={endDate} onChange={e => setEndDate(e.target.value)} />
            </div>
          </div>
        ) : (
          <div className="space-y-1.5">
            <Label htmlFor="monthSelect">Select Month</Label>
            <Input id="monthSelect" type="month" value={selectedMonth} onChange={e => setSelectedMonth(e.target.value)} />
          </div>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={handleExport}>
            Export
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
