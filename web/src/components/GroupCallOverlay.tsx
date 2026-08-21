import { useEffect, useMemo, useRef, useState } from "react";
import {
  Mic, MicOff, Video, VideoOff, MonitorUp, Users, PhoneOff, Circle
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useGroupCall, type GroupParticipant } from "@/hooks/useGroupCall";
import { useAuth } from "@/hooks/useAuth";

function initials(name?: string) {
  if (!name) return "?";
  return name.split(" ").filter(Boolean).slice(0, 2)
    .map((p) => p[0]?.toUpperCase()).join("");
}

function clock(total: number) {
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(h)}:${pad(m)}:${pad(s)}`;
}

/**
 * One face in the grid.
 *
 * Split out so React can keep each video element mounted across re-renders.
 * Inlining it would remount every tile whenever anyone's mute state changed,
 * and a remounted video element loses its stream and shows black for a beat.
 */
function Tile({
  name, stream, muted, cameraOff, connected, isSelf
}: {
  name: string;
  stream: MediaStream | null;
  muted: boolean;
  cameraOff: boolean;
  connected: boolean;
  isSelf?: boolean;
}) {
  const ref = useRef<HTMLVideoElement | null>(null);

  // Streams are attached through the DOM. A src attribute cannot carry one,
  // and re-attaching on every render would restart playback.
  useEffect(() => {
    if (!ref.current) return;
    if (ref.current.srcObject !== stream) {
      ref.current.srcObject = stream;
      if (stream) ref.current.play().catch(() => {});
    }
  }, [stream]);

  const showing = !!stream && !cameraOff && stream.getVideoTracks().length > 0;

  return (
    <div className="relative aspect-video overflow-hidden rounded-xl bg-slate-800 ring-1 ring-white/10">
      <video
        ref={ref}
        autoPlay
        playsInline
        // Hearing your own microphone back is an echo; everyone else is not.
        muted={isSelf}
        className={cn(
          "h-full w-full object-cover",
          showing ? "opacity-100" : "opacity-0"
        )}
      />

      {!showing && (
        <div className="absolute inset-0 grid place-items-center">
          <div className="grid h-16 w-16 place-items-center rounded-full bg-slate-700 text-lg font-semibold text-white">
            {initials(name)}
          </div>
        </div>
      )}

      {!connected && !isSelf && (
        <div className="absolute inset-0 grid place-items-center bg-slate-900/60">
          <span className="text-xs text-slate-300">Connecting…</span>
        </div>
      )}

      <div className="absolute inset-x-0 bottom-0 flex items-center justify-between gap-2 bg-gradient-to-t from-black/70 to-transparent px-2 py-1.5">
        <span className="truncate text-xs font-medium text-white">
          {isSelf ? "You" : name}
        </span>
        {muted && <MicOff className="h-3.5 w-3.5 shrink-0 text-red-400" />}
      </div>
    </div>
  );
}

function ControlButton({
  icon: Icon, label, active, danger, badge, onClick
}: {
  icon: typeof Mic;
  label: string;
  active?: boolean;
  danger?: boolean;
  badge?: number;
  onClick?: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      // The label is the accessible name and the visible one, so the control
      // reads the same to a screen reader as it does on screen.
      aria-label={label}
      aria-pressed={active}
      className="group flex w-16 flex-col items-center gap-1.5 focus:outline-none"
    >
      <span
        className={cn(
          "relative grid h-11 w-11 place-items-center rounded-full transition-colors",
          "group-focus-visible:ring-2 group-focus-visible:ring-white/70",
          danger
            ? "bg-red-500 text-white group-hover:bg-red-600"
            : active
              ? "bg-white text-slate-900"
              : "bg-white/10 text-white group-hover:bg-white/20"
        )}
      >
        <Icon className="h-5 w-5" />
        {!!badge && badge > 0 && (
          <span className="absolute -right-1 -top-1 grid h-4 min-w-4 place-items-center rounded-full bg-indigo-500 px-1 text-[10px] font-semibold text-white">
            {badge}
          </span>
        )}
      </span>
      <span className="text-[11px] text-slate-300">{label}</span>
    </button>
  );
}

/**
 * A group call, filling the screen.
 *
 * Rendered above the routes rather than inside the chat page, because a call
 * outlives the page somebody happened to be on when it started.
 */
export function GroupCallOverlay() {
  const { user } = useAuth();
  const g = useGroupCall();
  const [seconds, setSeconds] = useState(0);

  const active = g.state === "active";

  useEffect(() => {
    if (!active) { setSeconds(0); return; }
    const t = setInterval(() => setSeconds((s) => s + 1), 1000);
    return () => clearInterval(t);
  }, [active]);

  /*
    Columns from the count, not a fixed grid.

    Two people should not be shown as two small tiles in a four-column layout
    with half the screen empty. The steps are chosen so the tiles stay close
    to a comfortable size at every room size up to the mesh ceiling.
  */
  const columns = useMemo(() => {
    const n = g.participants.length + 1;
    if (n <= 1) return "grid-cols-1";
    if (n <= 2) return "grid-cols-1 sm:grid-cols-2";
    if (n <= 4) return "grid-cols-2";
    return "grid-cols-2 lg:grid-cols-3";
  }, [g.participants.length]);

  /* ------------------------------------------------------- being invited */

  if (g.state === "incoming" && g.invite) {
    return (
      <div className="fixed inset-0 z-[100] grid place-items-center bg-slate-950/95 p-6">
        <div className="w-full max-w-sm rounded-2xl bg-slate-900 p-8 text-center ring-1 ring-white/10">
          <div className="mx-auto grid h-20 w-20 place-items-center rounded-full bg-indigo-500/20 text-2xl font-semibold text-indigo-300">
            {initials(g.invite.roomName)}
          </div>
          <h2 className="mt-5 text-xl font-semibold text-white">{g.invite.roomName}</h2>
          <p className="mt-1 text-sm text-slate-400">
            {g.invite.fromName} started a group {g.invite.isVideo ? "video" : "voice"} call
          </p>

          <div className="mt-8 flex items-center justify-center gap-5">
            <button
              type="button"
              onClick={g.decline}
              className="grid h-14 w-14 place-items-center rounded-full bg-red-500 text-white transition-colors hover:bg-red-600"
              aria-label="Decline"
            >
              <PhoneOff className="h-6 w-6" />
            </button>
            <button
              type="button"
              onClick={() => void g.accept()}
              className="grid h-14 w-14 place-items-center rounded-full bg-emerald-500 text-white transition-colors hover:bg-emerald-600"
              aria-label="Join"
            >
              {g.invite.isVideo ? <Video className="h-6 w-6" /> : <Mic className="h-6 w-6" />}
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (g.state === "idle") return null;

  /* --------------------------------------------------------- in the call */

  const count = g.participants.length + 1;

  return (
    <div className="fixed inset-0 z-[100] flex flex-col bg-slate-950">
      <header className="flex shrink-0 items-center justify-between gap-3 px-4 py-3 sm:px-6">
        <div className="min-w-0">
          <h1 className="truncate text-base font-semibold text-white">{g.roomName}</h1>
          <p className="text-xs tabular-nums text-slate-400">
            {active ? clock(seconds) : g.state === "joining" ? "Joining…" : "Calling…"}
          </p>
        </div>
        <span className="flex shrink-0 items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5 text-xs font-medium text-white">
          <Users className="h-3.5 w-3.5" />
          {count} {count === 1 ? "participant" : "participants"}
        </span>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-3 sm:px-6">
        <div className={cn("grid gap-3", columns)}>
          <Tile
            isSelf
            name={user?.name || "You"}
            stream={g.localStream}
            muted={g.muted}
            cameraOff={g.cameraOff}
            connected
          />
          {g.participants.map((p: GroupParticipant) => (
            <Tile
              key={p.id}
              name={p.name}
              stream={p.stream}
              muted={p.muted}
              cameraOff={p.cameraOff}
              connected={p.connected}
            />
          ))}
        </div>

        {g.participants.length === 0 && (
          <p className="py-10 text-center text-sm text-slate-400">
            Waiting for others to join…
          </p>
        )}
      </div>

      <footer className="shrink-0 border-t border-white/10 bg-slate-900/80 px-4 py-3 backdrop-blur">
        <div className="flex items-center justify-center gap-1 sm:gap-3">
          <ControlButton
            icon={g.muted ? MicOff : Mic}
            label={g.muted ? "Unmute" : "Mute"}
            active={g.muted}
            onClick={g.toggleMute}
          />
          {g.isVideo && (
            <ControlButton
              icon={g.cameraOff ? VideoOff : Video}
              label={g.cameraOff ? "Start Video" : "Stop Video"}
              active={g.cameraOff}
              onClick={g.toggleCamera}
            />
          )}
          <ControlButton
            icon={MonitorUp}
            label={g.sharingScreen ? "Stop Share" : "Share Screen"}
            active={g.sharingScreen}
            onClick={() => void g.toggleScreenShare()}
          />
          <ControlButton icon={Users} label="Participants" badge={count} />

          <button
            type="button"
            onClick={g.leave}
            className="ml-2 flex items-center gap-2 rounded-full bg-red-500 px-5 py-3 text-sm font-semibold text-white transition-colors hover:bg-red-600 sm:ml-4 sm:px-6"
          >
            <PhoneOff className="h-4 w-4" />
            End
          </button>
        </div>
      </footer>
    </div>
  );
}
