import React, { useState, useRef } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  Video,
  Image as ImageIcon,
  FileImage,
  Upload,
  CheckCircle2,
  Eye,
  Edit2,
  Trash2,
  Plus,
  Play,
  Info
} from "lucide-react";
import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";
import toast from "react-hot-toast";
import dayjs from "dayjs";
import { api, apiMessage } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Dialog, DialogHeader } from "@/components/ui/dialog";
import { resolvePhotoUrl } from "@/components/ui/avatar";

interface Announcement {
  id: number;
  title?: string;
  description?: string;
  mediaType: "VIDEO" | "IMAGE" | "POSTER";
  mediaUrl: string;
  mediaName?: string;
  mediaSize?: number;
  status: "ACTIVE" | "INACTIVE" | "DELETED";
  targetRoles: string;
  durationSeconds: number;
  createdByName?: string;
  createdAt: string;
  publishedAt?: string;
}

export function TechAdminGlobalAnnouncements() {
  const qc = useQueryClient();
  const [mediaType, setMediaType] = useState<"VIDEO" | "IMAGE" | "POSTER">("VIDEO");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [filePreviewUrl, setFilePreviewUrl] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [targetRoles, setTargetRoles] = useState<string[]>(["Employee", "TL", "HR", "Admin"]);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [previewModal, setPreviewModal] = useState<Announcement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // Fetch all announcements for Tech Admin
  const listQuery = useQuery({
    queryKey: ["tech-admin-announcements"],
    queryFn: async () => {
      const res = await api.get<{ data: Announcement[] }>("/tech-admin/global-announcements");
      return res.data?.data || [];
    }
  });

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      if (file.size > 50 * 1024 * 1024) {
        toast.error("File size must be under 50MB");
        return;
      }
      setSelectedFile(file);
      setFilePreviewUrl(URL.createObjectURL(file));
    }
  };

  const resetForm = () => {
    setSelectedFile(null);
    setFilePreviewUrl(null);
    setTitle("");
    setDescription("");
    setTargetRoles(["Employee", "TL", "HR", "Admin"]);
    setEditingId(null);
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  // Publish / Create Mutation
  const publishMutation = useMutation({
    mutationFn: async () => {
      if (!selectedFile && !editingId) {
        throw new Error("Please select a media file to upload");
      }

      const formData = new FormData();
      if (selectedFile) {
        formData.append("file", selectedFile);
      }
      formData.append("mediaType", mediaType);
      formData.append("title", title);
      formData.append("description", description);
      formData.append("targetRoles", targetRoles.join(","));
      formData.append("durationSeconds", "15");
      formData.append("publishImmediately", "true");

      const res = await api.post("/tech-admin/global-announcements", formData, {
        headers: { "Content-Type": "multipart/form-data" }
      });
      return res.data;
    },
    onSuccess: () => {
      toast.success("Announcement published successfully!");
      qc.invalidateQueries({ queryKey: ["tech-admin-announcements"] });
      resetForm();
    },
    onError: (err) => {
      toast.error(apiMessage(err, "Failed to publish announcement"));
    }
  });

  // Toggle Active Status Mutation
  const statusMutation = useMutation({
    mutationFn: async ({ id, status }: { id: number; status: "ACTIVE" | "INACTIVE" }) => {
      const res = await api.put(`/tech-admin/global-announcements/${id}/status`, { status });
      return res.data;
    },
    onSuccess: () => {
      toast.success("Announcement status updated");
      qc.invalidateQueries({ queryKey: ["tech-admin-announcements"] });
    },
    onError: (err) => toast.error(apiMessage(err, "Could not update status"))
  });

  // Delete Mutation
  const deleteMutation = useMutation({
    mutationFn: async (id: number) => {
      const res = await api.delete(`/tech-admin/global-announcements/${id}`);
      return res.data;
    },
    onSuccess: () => {
      toast.success("Announcement deleted");
      qc.invalidateQueries({ queryKey: ["tech-admin-announcements"] });
    },
    onError: (err) => toast.error(apiMessage(err, "Failed to delete announcement"))
  });

  const toggleRole = (role: string) => {
    setTargetRoles((prev) =>
      prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role]
    );
  };

  const handleEdit = (ann: Announcement) => {
    setEditingId(ann.id);
    setMediaType(ann.mediaType);
    setTitle(ann.title || "");
    setDescription(ann.description || "");
    setFilePreviewUrl(resolvePhotoUrl(ann.mediaUrl) || ann.mediaUrl);
    setTargetRoles(ann.targetRoles ? ann.targetRoles.split(",") : ["Employee", "TL", "HR", "Admin"]);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Global Login Announcement</h1>
        <p className="text-sm text-muted-foreground">
          Upload and manage global announcement (Video, Photo, Poster) shown to all users after login
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left Side: Create / Update Form */}
        <div className="lg:col-span-5 rounded-2xl border bg-card p-6 shadow-sm space-y-5">
          <h2 className="text-base font-bold">Create / Update Announcement</h2>

          {/* Media Type Selector */}
          <div className="space-y-2">
            <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Media Type</label>
            <div className="flex items-center gap-4 pt-1">
              <label className="flex items-center gap-2 cursor-pointer text-sm font-medium">
                <input
                  type="radio"
                  name="mediaType"
                  value="VIDEO"
                  checked={mediaType === "VIDEO"}
                  onChange={() => setMediaType("VIDEO")}
                  className="accent-primary"
                />
                <Video className="h-4 w-4 text-violet-600" />
                <span>Video</span>
              </label>

              <label className="flex items-center gap-2 cursor-pointer text-sm font-medium">
                <input
                  type="radio"
                  name="mediaType"
                  value="IMAGE"
                  checked={mediaType === "IMAGE"}
                  onChange={() => setMediaType("IMAGE")}
                  className="accent-primary"
                />
                <ImageIcon className="h-4 w-4 text-blue-600" />
                <span>Photo / Image</span>
              </label>

              <label className="flex items-center gap-2 cursor-pointer text-sm font-medium">
                <input
                  type="radio"
                  name="mediaType"
                  value="POSTER"
                  checked={mediaType === "POSTER"}
                  onChange={() => setMediaType("POSTER")}
                  className="accent-primary"
                />
                <FileImage className="h-4 w-4 text-pink-600" />
                <span>Poster</span>
              </label>
            </div>
          </div>

          {/* File Upload Dropzone + Preview */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="space-y-2">
              <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                Upload Media <span className="text-destructive">*</span>
              </label>
              <input
                ref={fileInputRef}
                type="file"
                accept={mediaType === "VIDEO" ? "video/mp4,video/webm" : "image/jpeg,image/png,image/webp"}
                onChange={handleFileChange}
                className="hidden"
                id="media-upload-input"
              />
              <label
                htmlFor="media-upload-input"
                className="flex flex-col items-center justify-center p-4 border-2 border-dashed border-primary/30 rounded-xl hover:bg-muted/50 cursor-pointer transition-colors text-center min-h-[120px]"
              >
                {selectedFile ? (
                  <div className="flex items-center gap-2 text-xs font-semibold text-emerald-600">
                    <CheckCircle2 className="h-5 w-5 shrink-0" />
                    <span className="truncate max-w-[120px]">{selectedFile.name}</span>
                  </div>
                ) : (
                  <>
                    <Upload className="h-6 w-6 text-primary mb-1" />
                    <span className="text-xs font-semibold text-foreground">Choose File</span>
                  </>
                )}
                <span className="text-[10px] text-muted-foreground mt-2">
                  {mediaType === "VIDEO" ? "MP4, WEBM (Max 50MB)" : "JPG, PNG, WEBP (Max 50MB)"}
                </span>
              </label>
            </div>

            {/* Preview Box */}
            <div className="space-y-2">
              <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Preview</label>
              <div className="relative aspect-video w-full rounded-xl border bg-black/90 overflow-hidden flex items-center justify-center min-h-[120px]">
                {filePreviewUrl ? (
                  mediaType === "VIDEO" ? (
                    <video src={filePreviewUrl} className="h-full w-full object-contain" controls />
                  ) : (
                    <img src={filePreviewUrl} alt="Preview" className="h-full w-full object-contain" />
                  )
                ) : (
                  <div className="text-xs text-muted-foreground flex flex-col items-center gap-1">
                    <Play className="h-5 w-5 text-muted-foreground/50" />
                    <span>No Preview</span>
                  </div>
                )}
                <div className="absolute bottom-1 right-1 bg-black/70 text-[9px] font-mono text-white px-1.5 py-0.5 rounded">
                  00:15
                </div>
              </div>
            </div>
          </div>

          {/* Title (Optional) */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Title (Optional)</label>
            <Input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Welcome to Our New HR Portal"
              className="bg-background"
            />
          </div>

          {/* Description (Optional) */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Description (Optional)</label>
            <Input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Stay updated, stay connected."
              className="bg-background"
            />
          </div>

          {/* Target Users & Duration */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Target Users</label>
              <div className="flex flex-wrap gap-1.5 pt-1">
                {["Employee", "TL", "HR", "Admin"].map((r) => (
                  <Badge
                    key={r}
                    variant={targetRoles.includes(r) ? "default" : "outline"}
                    className="cursor-pointer text-[11px] font-semibold"
                    onClick={() => toggleRole(r)}
                  >
                    {r} {targetRoles.includes(r) && "✓"}
                  </Badge>
                ))}
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Duration</label>
              <div className="h-9 px-3 rounded-md border bg-muted/30 flex items-center text-xs font-bold text-muted-foreground">
                15 Seconds (Fixed)
              </div>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex items-center justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={resetForm}>
              Cancel
            </Button>
            <Button
              type="button"
              disabled={publishMutation.isPending}
              onClick={() => publishMutation.mutate()}
              className="bg-primary hover:bg-primary/90"
            >
              {publishMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Confirm / Publish
            </Button>
          </div>
        </div>

        {/* Right Side: Announcement History Table */}
        <div className="lg:col-span-7 rounded-2xl border bg-card p-6 shadow-sm space-y-5">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold">Announcement History</h2>
            <Button size="sm" onClick={resetForm} className="bg-primary text-xs">
              <Plus className="mr-1.5 h-3.5 w-3.5" /> Create Announcement
            </Button>
          </div>

          {listQuery.isLoading ? (
            <Skeleton className="h-64 w-full rounded-xl" />
          ) : (listQuery.data?.length ?? 0) === 0 ? (
            <div className="py-12 text-center text-sm text-muted-foreground">
              No announcements published yet.
            </div>
          ) : (
            <div className="overflow-x-auto rounded-xl border">
              <table className="w-full text-left text-xs">
                <thead className="bg-muted/50 border-b font-semibold text-muted-foreground uppercase">
                  <tr>
                    <th className="p-3">Media</th>
                    <th className="p-3">Type</th>
                    <th className="p-3">Title</th>
                    <th className="p-3">Target Users</th>
                    <th className="p-3">Status</th>
                    <th className="p-3">Published At</th>
                    <th className="p-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {listQuery.data?.map((ann) => {
                    const resolved = resolvePhotoUrl(ann.mediaUrl) || ann.mediaUrl;
                    return (
                      <tr key={ann.id} className="hover:bg-muted/30 transition-colors">
                        <td className="p-3">
                          <div className="relative h-10 w-16 rounded overflow-hidden bg-black/90 grid place-items-center">
                            {ann.mediaType === "VIDEO" ? (
                              <div className="relative h-full w-full grid place-items-center">
                                <video src={resolved} className="h-full w-full object-cover opacity-70" />
                                <Play className="absolute h-4 w-4 text-white fill-white" />
                              </div>
                            ) : (
                              <img src={resolved} alt="" className="h-full w-full object-cover" />
                            )}
                          </div>
                        </td>
                        <td className="p-3 font-semibold">
                          <span className="flex items-center gap-1">
                            {ann.mediaType === "VIDEO" ? <Video className="h-3.5 w-3.5 text-violet-500" /> : <ImageIcon className="h-3.5 w-3.5 text-blue-500" />}
                            {ann.mediaType}
                          </span>
                        </td>
                        <td className="p-3 font-medium max-w-[140px] truncate" title={ann.title || "Untitled"}>
                          {ann.title || "Untitled"}
                        </td>
                        <td className="p-3">
                          <div className="flex flex-wrap gap-1">
                            {(ann.targetRoles || "Employee,TL,HR,Admin").split(",").map((r) => (
                              <span key={r} className="px-1.5 py-0.5 rounded bg-muted text-[10px] font-semibold">
                                {r}
                              </span>
                            ))}
                          </div>
                        </td>
                        <td className="p-3">
                          <Badge
                            className={`text-[10px] font-bold uppercase border-0 ${
                              ann.status === "ACTIVE"
                                ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300"
                                : "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300"
                            }`}
                          >
                            {ann.status}
                          </Badge>
                        </td>
                        <td className="p-3 text-muted-foreground whitespace-nowrap">
                          {ann.publishedAt ? dayjs(ann.publishedAt).format("DD MMM YYYY, hh:mm A") : "—"}
                        </td>
                        <td className="p-3 text-right">
                          <div className="flex items-center justify-end gap-1.5">
                            <button
                              onClick={() => setPreviewModal(ann)}
                              className="p-1 rounded hover:bg-muted text-muted-foreground hover:text-foreground"
                              title="Preview"
                            >
                              <Eye className="h-4 w-4" />
                            </button>

                            <button
                              onClick={() => handleEdit(ann)}
                              className="p-1 rounded hover:bg-muted text-muted-foreground hover:text-foreground"
                              title="Edit"
                            >
                              <Edit2 className="h-4 w-4" />
                            </button>

                            <button
                              onClick={() =>
                                statusMutation.mutate({
                                  id: ann.id,
                                  status: ann.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"
                                })
                              }
                              className={`p-1 rounded ${ann.status === "ACTIVE" ? "text-emerald-600" : "text-slate-400"}`}
                              title={ann.status === "ACTIVE" ? "Deactivate" : "Activate"}
                            >
                              <input
                                type="checkbox"
                                checked={ann.status === "ACTIVE"}
                                readOnly
                                className="h-4 w-4 accent-emerald-600 cursor-pointer"
                              />
                            </button>

                            <button
                              onClick={() => {
                                if (confirm("Are you sure you want to delete this announcement?")) {
                                  deleteMutation.mutate(ann.id);
                                }
                              }}
                              className="p-1 rounded hover:bg-rose-50 text-rose-500 hover:text-rose-700"
                              title="Delete"
                            >
                              <Trash2 className="h-4 w-4" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {/* Info Card Banner */}
          <div className="rounded-xl border border-primary/20 bg-primary/5 p-4 flex items-start gap-3 text-xs text-muted-foreground">
            <Info className="h-4 w-4 text-primary shrink-0 mt-0.5" />
            <span>
              Active announcement will be shown to all selected users immediately after login for 15 seconds.
            </span>
          </div>
        </div>
      </div>

      {/* Preview Dialog */}
      {previewModal && (
        <Dialog open={!!previewModal} onClose={() => setPreviewModal(null)} className="sm:max-w-2xl">
          <DialogHeader title={previewModal.title || "Announcement Preview"} />
          <div className="space-y-4 pt-2">
            <div className="relative aspect-video w-full rounded-2xl overflow-hidden bg-black grid place-items-center">
              {previewModal.mediaType === "VIDEO" ? (
                <video src={resolvePhotoUrl(previewModal.mediaUrl) || previewModal.mediaUrl} className="h-full w-full object-contain" controls autoPlay />
              ) : (
                <img src={resolvePhotoUrl(previewModal.mediaUrl) || previewModal.mediaUrl} alt="" className="h-full w-full object-contain" />
              )}
            </div>
            {previewModal.description && (
              <p className="text-sm text-muted-foreground">{previewModal.description}</p>
            )}
          </div>
        </Dialog>
      )}
    </div>
  );
}
