import * as React from "react";
import { AlertTriangle } from "lucide-react";
import { Dialog } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

/**
 * A confirmation the application draws itself.
 *
 * window.confirm() blocks the whole page, renders in the browser's chrome
 * rather than the product's, and says "pixoushrportal.pixous.info says" above
 * the question -- which reads as a warning from somewhere else rather than a
 * question from the screen the person is looking at. It also cannot show what
 * is about to happen: no dates, no leave type, nothing to check the decision
 * against.
 *
 * This asks the same question in the application's own dialog, with room for
 * the detail underneath, and leaves the destructive answer looking destructive.
 */
export function ConfirmDialog({
  open,
  title,
  description,
  detail,
  confirmLabel = "Yes, continue",
  cancelLabel = "No, go back",
  busy = false,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  title: string;
  description?: string;
  /** Optional lines of what is about to change, shown as label/value pairs. */
  detail?: [string, string][];
  confirmLabel?: string;
  cancelLabel?: string;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  if (!open) return null;

  return (
    <Dialog open onClose={busy ? () => {} : onCancel} className="max-w-md" hideCloseButton>
      <div className="flex items-start gap-4">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-amber-100 text-amber-600 dark:bg-amber-500/15 dark:text-amber-400">
          <AlertTriangle className="h-5 w-5" />
        </span>
        <div className="min-w-0">
          <h2 className="font-display text-lg font-semibold">{title}</h2>
          {description && (
            <p className="mt-1 text-sm text-muted-foreground">{description}</p>
          )}
        </div>
      </div>

      {detail && detail.length > 0 && (
        <dl className="mt-4 divide-y rounded-lg border bg-muted/30 text-sm">
          {detail.map(([label, value]) => (
            <div key={label} className="flex items-start justify-between gap-4 px-3 py-2">
              <dt className="text-muted-foreground">{label}</dt>
              <dd className="text-right font-medium">{value}</dd>
            </div>
          ))}
        </dl>
      )}

      <div className="mt-5 flex justify-end gap-2">
        {/* Going back is the safe answer, so it is the plain one and it is
            first in the tab order. */}
        <Button type="button" variant="outline" onClick={onCancel} disabled={busy}>
          {cancelLabel}
        </Button>
        <Button
          type="button"
          onClick={onConfirm}
          disabled={busy}
          className="bg-rose-600 text-white hover:bg-rose-700 focus-visible:ring-rose-600"
        >
          {busy ? "Working…" : confirmLabel}
        </Button>
      </div>
    </Dialog>
  );
}
