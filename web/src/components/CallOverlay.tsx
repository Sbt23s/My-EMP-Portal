import { useEffect, useRef, useState } from "react";
import { Mic, MicOff, Phone, PhoneOff, Video, VideoOff, MonitorUp } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

function initials(name?: string) {
  if (!name) return "?";
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join("");
}

/** One labelled round button in the call's control bar. */
function CallControl({
  icon: Icon, label, active, onClick
}: {
  icon: typeof Mic;
  label: string;
  active?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      aria-pressed={active}
      className="group flex w-16 flex-col items-center gap-1.5 focus:outline-none"
    >
      <span
        className={cn(
          "grid h-12 w-12 place-items-center rounded-full transition-colors",
          "group-focus-visible:ring-2 group-focus-visible:ring-white/70",
          active
            ? "bg-white text-neutral-900"
            : "bg-white/15 text-white group-hover:bg-white/25"
        )}
      >
        <Icon className="h-5 w-5" />
      </span>
      <span className="text-[11px] text-white/70">{label}</span>
    </button>
  );
}

export type CallState = "idle" | "calling" | "ringing" | "incoming" | "connecting" | "connected";

/**
 * A voice or video call, filling the screen. One screen covers being called,
 * calling out and talking — the buttons underneath are what change.
 *
 * It lives above the whole app rather than inside the chat page, because a call
 * can arrive while somebody is looking at their payslip.
 */
export function CallOverlay({
  state, partnerName, isVideo, localStream, remoteStream,
  muted, cameraOff, onAccept, onReject, onHangUp, onToggleMute, onToggleCamera,
  sharingScreen, onToggleScreenShare
}: {
  state: CallState;
  partnerName: string;
  isVideo: boolean;
  localStream: MediaStream | null;
  remoteStream: MediaStream | null;
  muted: boolean;
  cameraOff: boolean;
  onAccept: () => void;
  onReject: () => void;
  onHangUp: () => void;
  onToggleMute: () => void;
  onToggleCamera: () => void;
  sharingScreen: boolean;
  onToggleScreenShare: () => void;
}) {
  const localVideo = useRef<HTMLVideoElement | null>(null);
  const mainLocalVideo = useRef<HTMLVideoElement | null>(null);
  // The other side plays through a video element on a video call and a bare
  // audio element otherwise; only one of the two is ever mounted.
  const remoteVideo = useRef<HTMLVideoElement | null>(null);
  const remoteAudio = useRef<HTMLAudioElement | null>(null);
  const [seconds, setSeconds] = useState(0);

  const hasRemoteVideo = isVideo && !!remoteStream && remoteStream.getVideoTracks().some(t => t.readyState === "live" && t.enabled);
  const hasLocalVideo = isVideo && !!localStream && localStream.getVideoTracks().some(t => t.readyState === "live" && t.enabled) && !cameraOff;

  // Streams are attached through the DOM rather than a src attribute. The state
  // and kind are watched too: connecting swaps the audio element for a video
  // one, and the new element would otherwise never be given the stream.
  useEffect(() => {
    if (localVideo.current) {
      localVideo.current.srcObject = localStream ?? null;
      if (localStream) localVideo.current.play().catch(() => {});
    }
    if (mainLocalVideo.current) {
      mainLocalVideo.current.srcObject = localStream ?? null;
      if (localStream) mainLocalVideo.current.play().catch(() => {});
    }
    /*
      hasLocalVideo and cameraOff are dependencies because they decide whether
      the element exists at all. Turning the camera off unmounted the <video>;
      turning it back on mounted a brand new one with no srcObject, and this
      effect did not re-run because the stream object itself had not changed.
      The result was your own picture never coming back after Stop Video --
      the camera was running and the far side could see you, but your own
      preview stayed black.
    */
  }, [localStream, state, isVideo, hasRemoteVideo, hasLocalVideo, cameraOff]);
  useEffect(() => {
    if (remoteVideo.current) {
      remoteVideo.current.srcObject = remoteStream ?? null;
      if (remoteStream) remoteVideo.current.play().catch(() => {});
    }
    if (remoteAudio.current) {
      remoteAudio.current.srcObject = remoteStream ?? null;
      if (remoteStream) remoteAudio.current.play().catch(() => {});
    }
  }, [remoteStream, state, isVideo, hasRemoteVideo]);

  useEffect(() => {
    if (state !== "connected") return;
    setSeconds(0);
    const timer = setInterval(() => setSeconds((s) => s + 1), 1000);
    return () => clearInterval(timer);
  }, [state]);

  const label =
    state === "incoming" ? `Incoming ${isVideo ? "video" : "voice"} call`
      : state === "calling" ? "Calling…"
        : state === "ringing" ? "Ringing…"
          : state === "connecting" ? "Connecting…"
            : `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;

  return (
    <div className="fixed inset-0 z-[100] flex flex-col items-center justify-center bg-neutral-900/95 p-6 text-white">
      {/* Background or Main Video */}
      {isVideo ? (
        <div className="relative flex-1 w-full max-w-4xl overflow-hidden rounded-2xl bg-black shadow-2xl flex items-center justify-center">
          {state === "connected" ? (
            hasRemoteVideo ? (
              <>
                <video
                  ref={remoteVideo}
                  autoPlay
                  playsInline
                  className="h-full w-full object-contain bg-black"
                />
                {/*
                  Hidden rather than unmounted. A remounted video element is a
                  new element with no stream attached, and re-attaching it is
                  easy to get wrong -- as it was. Keeping the one element for
                  the life of the call means the picture is always ready the
                  instant the camera is switched back on, with no black frame
                  while it reconnects.
                */}
                <video
                  ref={localVideo}
                  autoPlay
                  playsInline
                  muted
                  className={cn(
                    "absolute bottom-4 right-4 w-36 h-24 sm:w-48 sm:h-32 rounded-xl border-2 border-white/30 object-contain bg-black shadow-xl -scale-x-100",
                    !hasLocalVideo && "hidden"
                  )}
                />
                <div className="absolute left-4 top-4 rounded-full bg-black/60 px-4 py-1.5 text-sm font-semibold tabular-nums backdrop-blur-md">
                  {partnerName} · {label}
                </div>
              </>
            ) : hasLocalVideo ? (
              <>
                <video
                  ref={mainLocalVideo}
                  autoPlay
                  playsInline
                  muted
                  className="h-full w-full object-contain bg-black -scale-x-100"
                />
                <div className="absolute left-4 top-4 rounded-full bg-black/60 px-4 py-1.5 text-sm font-semibold tabular-nums backdrop-blur-md">
                  {partnerName} · {label}
                </div>
              </>
            ) : (
              <div className="absolute inset-0 flex flex-col items-center justify-center gap-4 bg-black/80">
                <div className="flex h-28 w-28 items-center justify-center rounded-full bg-white/20 text-4xl font-bold backdrop-blur-sm shadow-xl">
                  {initials(partnerName)}
                </div>
                <h3 className="font-display text-2xl font-bold drop-shadow-md">{partnerName}</h3>
                <p className="text-base font-medium tabular-nums text-white/90 drop-shadow-md">{label}</p>
              </div>
            )
          ) : (
            <>
              <video
                ref={localVideo}
                autoPlay
                playsInline
                muted
                className="absolute inset-0 h-full w-full object-cover opacity-50 blur-sm scale-105"
              />
              <div className="absolute inset-0 flex flex-col items-center justify-center gap-4 bg-black/30">
                {/* This is the not-connected half of the branch above, so the
                    pulse is unconditional here -- the `state !== "connected"`
                    guard that used to be on it could never be false. */}
                <div className="flex h-28 w-28 animate-pulse items-center justify-center rounded-full bg-white/20 text-4xl font-bold backdrop-blur-sm shadow-xl">
                  {initials(partnerName)}
                </div>
                <h3 className="font-display text-2xl font-bold drop-shadow-md">{partnerName}</h3>
                <p className="text-base font-medium tabular-nums text-white/90 drop-shadow-md">{label}</p>
              </div>
            </>
          )}
        </div>
      ) : (
        <div className="flex flex-1 flex-col items-center justify-center gap-3">
          <div className={cn(
            "flex h-28 w-28 items-center justify-center rounded-full bg-white/10 text-4xl font-bold shadow-lg",
            state !== "connected" && "animate-pulse"
          )}>
            {initials(partnerName)}
          </div>
          <h3 className="font-display text-2xl font-bold">{partnerName}</h3>
          <p className="text-base tabular-nums text-white/70">{label}</p>
        </div>
      )}
      {/* Audio element ensuring remote voice is played unconditionally for all calls */}
      <audio ref={remoteAudio} autoPlay className="hidden" />

      <div className="mt-6 flex items-center gap-3">
        {state === "incoming" ? (
          <>
            <Button
              type="button"
              size="icon"
              onClick={onReject}
              title="Decline"
              className="h-14 w-14 rounded-full bg-red-500 text-white hover:bg-red-600"
            >
              <PhoneOff className="h-6 w-6" />
            </Button>
            <Button
              type="button"
              size="icon"
              onClick={onAccept}
              title="Accept"
              className="h-14 w-14 rounded-full bg-emerald-500 text-white hover:bg-emerald-600"
            >
              {isVideo ? <Video className="h-6 w-6" /> : <Phone className="h-6 w-6" />}
            </Button>
          </>
        ) : (
          <>
            {/* Labelled, because an unlabelled circle of icons makes people
                guess which one hangs up. The label is also the accessible
                name, so the control reads the same either way. */}
            <CallControl
              icon={muted ? MicOff : Mic}
              label={muted ? "Unmute" : "Mute"}
              active={muted}
              onClick={onToggleMute}
            />
            {isVideo && (
              <CallControl
                icon={cameraOff ? VideoOff : Video}
                label={cameraOff ? "Start Video" : "Stop Video"}
                active={cameraOff}
                onClick={onToggleCamera}
              />
            )}
            {isVideo && state === "connected" && (
              <CallControl
                icon={MonitorUp}
                label={sharingScreen ? "Stop Share" : "Share Screen"}
                active={sharingScreen}
                onClick={onToggleScreenShare}
              />
            )}
            <button
              type="button"
              onClick={onHangUp}
              className="ml-3 flex items-center gap-2 rounded-full bg-red-500 px-6 py-3.5 text-sm font-semibold text-white transition-colors hover:bg-red-600"
            >
              <PhoneOff className="h-4 w-4" />
              End Call
            </button>
          </>
        )}
      </div>
    </div>
  );
}
