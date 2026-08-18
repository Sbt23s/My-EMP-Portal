import { useNavigate } from "react-router-dom";
import { AlertCircle, BellRing, CheckCheck } from "lucide-react";
import dayjs from "dayjs";
import { useAuth } from "@/hooks/useAuth";
import { useNotifications } from "@/hooks/useNotifications";
import { PageHeader } from "@/components/PageHeader";
import { EmptyState } from "@/components/EmptyState";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

export default function NotificationsPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { notifications, unreadCount, loading, failed, retry, markAllRead, markRead } = useNotifications(user?.id);

  return (
    <div>
      <PageHeader
        title="Notifications"
        subtitle="Everything that needs your attention, in one place."
        actions={
          unreadCount > 0 ? (
            <Button variant="outline" onClick={() => markAllRead()}>
              <CheckCheck className="h-4 w-4" /> Mark all read
            </Button>
          ) : null
        }
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
            const isCallNotification = n.type === "CALL" || n.title?.toLowerCase().includes("call") || n.body?.toLowerCase().includes("calling");
            const isPastCall = isCallNotification && (Date.now() - new Date(n.createdAt).getTime() > 45000 || n.body?.includes("Voice call") || n.read);
            const displayTitle = isPastCall && n.title === "Incoming Call" ? (n.body ? `${n.body.replace(/\s+is calling you\.\.\.$/, "")} - voice call` : "Voice call") : n.title;
            const displayBody = isPastCall && n.body?.includes("is calling you...") ? "Called you" : n.body;

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
