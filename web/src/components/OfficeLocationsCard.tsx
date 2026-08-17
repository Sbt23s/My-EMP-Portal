import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  Building2, MapPin, Crosshair, Pencil, Trash2, Plus, AlertTriangle, Check
} from "lucide-react";
import toast from "react-hot-toast";
import { api, apiMessage } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import type { ApiEnvelope } from "@/types";

interface OfficeLocation {
  id: number;
  name: string;
  address?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  geofenceRadiusMetres?: number | null;
  active?: boolean;
}

/**
 * The offices a punch is matched against.
 *
 * <p>Attendance stores true coordinates, and coordinates alone cannot say whether
 * somebody was at the office — they have to be compared against something. This is
 * that something, and until the real office is on it every punch made there is
 * correctly reported as somewhere unrecognised.
 *
 * <p>The coordinates are taken from the browser standing in the building rather
 * than typed. Typing them means looking them up somewhere else and getting them
 * slightly wrong, and slightly wrong here means turning people away from their own
 * office for months.
 */
export function OfficeLocationsCard() {
  const qc = useQueryClient();
  const [editing, setEditing] = useState<OfficeLocation | null>(null);
  const [adding, setAdding] = useState(false);

  const offices = useQuery({
    queryKey: ["office-locations"],
    queryFn: async () =>
      (await api.get<ApiEnvelope<OfficeLocation[]>>("/org/office-locations")).data.data
  });

  const remove = useMutation({
    mutationFn: async (id: number) => api.delete(`/org/office-locations/${id}`),
    onSuccess: () => {
      toast.success("Office removed");
      qc.invalidateQueries({ queryKey: ["office-locations"] });
      qc.invalidateQueries({ queryKey: ["attendance"] });
    },
    onError: (err) => toast.error(apiMessage(err, "Could not remove it"))
  });

  const list = offices.data ?? [];
  const anyPinned = list.some((o) => o.latitude != null && o.longitude != null);

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <CardTitle className="flex items-center gap-2 text-base">
              <Building2 className="h-4 w-4 text-primary" />
              Office locations
            </CardTitle>
            <p className="mt-0.5 text-xs text-muted-foreground">
              Every punch is matched against these. An office on this list is named on the
              timesheet; a punch anywhere else reads as another location.
            </p>
          </div>
          <Button size="sm" onClick={() => setAdding(true)}>
            <Plus className="mr-1.5 h-4 w-4" /> Add this office
          </Button>
        </div>
      </CardHeader>

      <CardContent>
        {offices.isLoading ? (
          <Skeleton className="h-24" />
        ) : list.length === 0 ? (
          <div className="rounded-lg border border-dashed p-4 text-center text-sm text-muted-foreground">
            No offices recorded. Until one is, every punch reads as another location.
          </div>
        ) : (
          <div className="space-y-2">
            {list.map((o) => {
              const pinned = o.latitude != null && o.longitude != null;
              return (
                <div
                  key={o.id}
                  className={cn(
                    "flex flex-wrap items-start justify-between gap-3 rounded-lg border p-3",
                    pinned ? "bg-muted/20" : "border-amber-500/40 bg-amber-500/5"
                  )}
                >
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-semibold">{o.name}</span>
                      {pinned ? (
                        <span className="inline-flex items-center gap-1 rounded-full bg-emerald-500/10 px-2 py-0.5 text-[10px] font-bold text-emerald-700 dark:text-emerald-400">
                          <Check className="h-3 w-3" /> Pinned
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 rounded-full bg-amber-500/10 px-2 py-0.5 text-[10px] font-bold text-amber-700 dark:text-amber-400">
                          <AlertTriangle className="h-3 w-3" /> No coordinates
                        </span>
                      )}
                    </div>
                    {o.address && (
                      <div className="text-[11px] text-muted-foreground">{o.address}</div>
                    )}
                    {pinned && (
                      <div className="mt-0.5 flex flex-wrap items-center gap-x-3 text-[11px] tabular-nums text-muted-foreground">
                        <span className="flex items-center gap-1">
                          <MapPin className="h-3 w-3" />
                          {Number(o.latitude).toFixed(5)}, {Number(o.longitude).toFixed(5)}
                        </span>
                        <span>within {o.geofenceRadiusMetres ?? 200} m</span>
                        <a
                          className="font-medium text-primary hover:underline"
                          href={`https://www.google.com/maps?q=${o.latitude},${o.longitude}`}
                          target="_blank"
                          rel="noreferrer"
                        >
                          Open on a map
                        </a>
                      </div>
                    )}
                  </div>

                  <div className="flex shrink-0 gap-1.5">
                    <Button size="sm" variant="outline" className="h-7 px-2 text-[11px]"
                      onClick={() => setEditing(o)}>
                      <Pencil className="mr-1 h-3 w-3" /> Edit
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      className="h-7 border-destructive/40 px-2 text-[11px] text-destructive hover:bg-destructive/10"
                      disabled={remove.isPending}
                      onClick={() => {
                        if (window.confirm(
                          `Remove "${o.name}"? Punches already recorded there keep pointing at it, `
                          + `but new punches will no longer be named with it.`
                        )) remove.mutate(o.id);
                      }}
                    >
                      <Trash2 className="h-3 w-3" />
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {!anyPinned && list.length > 0 && (
          <p className="mt-3 rounded-lg bg-amber-500/10 p-2.5 text-[11px] leading-relaxed text-amber-800 dark:text-amber-300">
            None of these has coordinates yet, so nothing can be matched against them.
            Stand in the office, press <strong>Add this office</strong> or <strong>Edit</strong>,
            and use your current location.
          </p>
        )}
      </CardContent>

      {(adding || editing) && (
        <OfficeDialog
          office={editing}
          onClose={() => { setAdding(false); setEditing(null); }}
          onSaved={() => {
            setAdding(false);
            setEditing(null);
            qc.invalidateQueries({ queryKey: ["office-locations"] });
            // The names on the timesheet are derived from these, so they change too.
            qc.invalidateQueries({ queryKey: ["attendance"] });
          }}
        />
      )}
    </Card>
  );
}

/** Adding an office, or moving one that already exists. */
function OfficeDialog({
  office, onClose, onSaved
}: {
  office: OfficeLocation | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [name, setName] = useState(office?.name ?? "");
  const [address, setAddress] = useState(office?.address ?? "");
  const [radius, setRadius] = useState(String(office?.geofenceRadiusMetres ?? 200));
  const [lat, setLat] = useState(office?.latitude != null ? String(office.latitude) : "");
  const [lng, setLng] = useState(office?.longitude != null ? String(office.longitude) : "");
  const [accuracy, setAccuracy] = useState<number | null>(null);
  const [locating, setLocating] = useState(false);

  const secure = typeof window !== "undefined" && window.isSecureContext;

  /**
   * Takes the position the browser reports.
   *
   * <p>High accuracy is asked for and no cached fix accepted: a stale position from
   * somewhere else this morning would pin the office to the wrong building, and
   * nobody would find out until punches started reading as elsewhere.
   */
  const useMyLocation = () => {
    if (!secure || !navigator.geolocation) {
      toast.error("Location needs a secure (https) connection. It works on localhost.");
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLat(String(pos.coords.latitude));
        setLng(String(pos.coords.longitude));
        setAccuracy(Math.round(pos.coords.accuracy));
        setLocating(false);
        toast.success(`Position taken (±${Math.round(pos.coords.accuracy)} m)`);
      },
      (err) => {
        setLocating(false);
        toast.error(
          err.code === err.PERMISSION_DENIED
            ? "Location permission was refused. Allow it in the address bar and try again."
            : "Could not get a position. Try again near a window."
        );
      },
      { enableHighAccuracy: true, timeout: 20000, maximumAge: 0 }
    );
  };

  const save = useMutation({
    mutationFn: async () =>
      api.post("/org/office-locations", {
        id: office?.id,
        name: name.trim(),
        address: address.trim() || null,
        latitude: lat,
        longitude: lng,
        geofenceRadiusMetres: Number(radius) || 200
      }),
    onSuccess: () => {
      toast.success(office ? "Office moved" : "Office added");
      onSaved();
    },
    onError: (err) => toast.error(apiMessage(err, "Could not save it"))
  });

  const ready = name.trim().length > 0 && lat !== "" && lng !== "";
  // A phone or laptop indoors is often tens of metres out. A radius smaller than
  // the fix itself turns away people standing in the building.
  const radiusTooTight = accuracy != null && Number(radius) < accuracy;

  return (
    <Dialog open onClose={onClose} className="max-w-md">
      <DialogHeader
        title={office ? `Edit ${office.name}` : "Add this office"}
        description="Stand inside the building and take your current position."
      />
      <div className="space-y-3">
        <div className="space-y-1.5">
          <Label htmlFor="office-name">Office name</Label>
          <Input
            id="office-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Pixous Technologies — Coimbatore"
            autoFocus
          />
          <p className="text-[11px] text-muted-foreground">
            This is the name that appears on every timesheet for punches made here.
          </p>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="office-address">Address (optional)</Label>
          <Input
            id="office-address"
            value={address}
            onChange={(e) => setAddress(e.target.value)}
            placeholder="Street, area, city"
          />
        </div>

        <div className="rounded-lg border bg-muted/30 p-3">
          <div className="mb-2 flex items-center justify-between gap-2">
            <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              Position
            </span>
            <Button size="sm" variant="outline" disabled={locating || !secure} onClick={useMyLocation}>
              {locating
                ? <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                : <Crosshair className="mr-1.5 h-3.5 w-3.5" />}
              Use my current location
            </Button>
          </div>

          {lat && lng ? (
            <div className="space-y-1">
              <div className="flex items-center gap-1.5 text-sm font-medium tabular-nums">
                <MapPin className="h-3.5 w-3.5 text-primary" />
                {Number(lat).toFixed(6)}, {Number(lng).toFixed(6)}
              </div>
              {accuracy != null && (
                <div className={cn(
                  "text-[11px]",
                  accuracy > 100 ? "text-amber-700 dark:text-amber-400" : "text-muted-foreground"
                )}>
                  Accurate to about ±{accuracy} m
                  {accuracy > 100 && " — that is loose for pinning a building. Try again near a window, or from a phone."}
                </div>
              )}
              <a
                className="inline-block text-[11px] font-medium text-primary hover:underline"
                href={`https://www.google.com/maps?q=${lat},${lng}`}
                target="_blank"
                rel="noreferrer"
              >
                Check it on a map before saving
              </a>
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">
              No position yet. Press the button above while you are in the office.
            </p>
          )}

          {!secure && (
            <p className="mt-2 text-[11px] text-amber-700 dark:text-amber-400">
              The browser only shares a position over a secure (https) connection.
              This works on localhost.
            </p>
          )}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="office-radius">How far around it still counts (metres)</Label>
          <Input
            id="office-radius"
            type="number"
            min={50}
            max={5000}
            value={radius}
            onChange={(e) => {
              const val = e.target.value;
              if (val.length <= 3) {
                setRadius(val);
              }
            }}
          />
          <p className={cn(
            "text-[11px]",
            radiusTooTight ? "text-amber-700 dark:text-amber-400" : "text-muted-foreground"
          )}>
            {radiusTooTight
              ? `Smaller than the ±${accuracy} m fix you just took, so people standing in the building may be reported as elsewhere. 200 m is the usual choice.`
              : "200 m suits most offices. A phone's own fix indoors is rarely better than 50 m, which is why it cannot go below that."}
          </p>
        </div>

        <div className="flex justify-end gap-2 pt-1">
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button disabled={!ready || save.isPending} onClick={() => save.mutate()}>
            {save.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {office ? "Save changes" : "Add office"}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
