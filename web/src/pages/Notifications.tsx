import { useNavigate } from "react-router-dom";
import { useState } from "react";
import { AlertCircle, BellRing, CheckCheck, Trash2 } from "lucide-react";
import dayjs from "dayjs";
import { useAuth } from "@/hooks/useAuth";
import { useNotifications } from "@/hooks/useNotifications";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { cn } from "@/lib/utils";
import { useCalls } from "@/hooks/useCalls";
import { describeCallNotification } from "@/lib/callNotifications";

export default function NotificationsPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { notifications, unreadCount, loading, failed, retry, markAllRead, markRead, clearAll } =
    useNotifications(user?.id);
  const { callState, activeCallPartner } = useCalls();
  const liveCallerName = callState !== "idle" ? (activeCallPartner?.name ?? null) : null;

  /*
    Clearing is confirmed, marking read is not.

    Marking read is reversible by scrolling — the rows are still there. Clearing
    deletes them, and a misclick on a page whose whole content is a list is easy
    to make and impossible to undo.
  */
  const [confirmClear, setConfirmClear] = useState(false);

  return (
    <div>
      <PageHeader
        title="Notifications"
        subtitle="Everything that needs your attention, in one place."
        actions={
          <div className="flex gap-2">
            {unreadCount > 0 && (
              <Button variant="outline" onClick={() => markAllRead()}>
                <CheckCheck className="h-4 w-4" /> Mark all read
              </Button>
            )}
            {notifications.length > 0 && (
              <Button
                variant="outline"
                className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                onClick={() => setConfirmClear(true)}
              >
                <Trash2 className="h-4 w-4" /> Clear all
              </Button>
            )}
          </div>
        }
      />

      <ConfirmDialog
        open={confirmClear}
        title="Clear every notification?"
        /* Says what survives, because that is the question somebody hesitating
           over this button actually has. */
        description={
          `This removes all ${notifications.length} from your list, read and unread. `
          + "The leave requests, tickets and payslips they point at are not affected — "
          + "only the announcements about them."
        }
        confirmLabel="Clear all"
        cancelLabel="Keep them"
        onConfirm={async () => { await clearAll(); setConfirmClear(false); }}
        onCancel={() => setConfirmClear(false)}
      />

      {loading ? (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-16" />
          ))}
        </div>
      ) : failed ? (
        // Not the same thing as having nothing. Before, a failed request showed
        // "You're all caught up", which is a claim about your inbox that we were
        // in no position to make.
        <Card className="border-destructive/20 bg-destructive/5">
          <CardContent className="flex flex-col items-center justify-center p-10 text-center">
            <AlertCircle className="mb-3 h-8 w-8 text-destructive" />
            <h3 className="font-semibold text-foreground">Couldn't load your notifications</h3>
            <p className="mt-1 text-sm text-muted-foreground">
              The server did not answer. This does not mean you have none.
            </p>
            <Button variant="outline" className="mt-5" onClick={() => retry()}>Try again</Button>
          </CardContent>
        </Card>
      ) : notifications.length === 0 ? (
        <EmptyState
          icon={BellRing}
          title="You're all caught up"
          description="New alerts about leave, attendance, assets and tickets will appear here."
        />
      ) : (
        <div className="space-y-2">
          {notifications.map((n) => {
            const { title: displayTitle, body: displayBody } =
              describeCallNotification(n, liveCallerName);

            return (
              <Card
                key={n.id}
                className={cn(
                  "cursor-pointer transition-colors hover:bg-muted/40",
                  !n.read && "border-l-4 border-l-primary"
                )}
                onClick={() => {
                  if (!n.read) markRead(n.id);
                  if (n.link) navigate(n.link);
                }}
              >
                <CardContent className="flex items-start gap-3 p-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      {!n.read && <span className="h-2 w-2 rounded-full bg-primary" />}
                      <span className="font-medium">{displayTitle}</span>
                      {n.type && (
                        <Badge variant="secondary" className="text-[10px]">
                          {n.type}
                        </Badge>
                      )}
                    </div>
                    {displayBody && <p className="mt-0.5 text-sm text-muted-foreground">{displayBody}</p>}
                  </div>
                  <span className="shrink-0 text-xs text-muted-foreground">
                    {dayjs(n.createdAt).format("DD MMM, h:mm A")}
                  </span>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
