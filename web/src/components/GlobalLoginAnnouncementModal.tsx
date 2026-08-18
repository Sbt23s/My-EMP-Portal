import React, { useState, useEffect, useRef } from "react";
import { X, Volume2, VolumeX, Play, Pause, Megaphone } from "lucide-react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { api, tokenStore } from "@/lib/api";
import { useAuth } from "@/context/AuthContext";
import { resolvePhotoUrl } from "@/components/ui/avatar";
import { Lottie } from "lottie-react";

interface Announcement {
  id: number;
  title?: string;
  description?: string;
  mediaType: "VIDEO" | "IMAGE" | "POSTER";
  mediaUrl: string;
  mediaName?: string;
  status: "ACTIVE" | "INACTIVE" | "DELETED";
  targetRoles: string;
  durationSeconds: number;
  /** Lottie JSON played over the media. Absent when none was uploaded. */
  effectUrl?: string | null;
  /** Whether to play it. An effect can be uploaded and switched off. */
  effectEnabled?: boolean;
}

export function GlobalLoginAnnouncementModal() {
  const { user } = useAuth();
  const [announcement, setAnnouncement] = useState<Announcement | null>(null);
  const [isOpen, setIsOpen] = useState(false);
  const [timeLeft, setTimeLeft] = useState(15);
  const [isMuted, setIsMuted] = useState(false);
  const [isPlaying, setIsPlaying] = useState(true);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const timerRef = useRef<any>(null);
  const shownThisSessionRef = useRef<Set<number>>(new Set());

  const showAnnouncement = (active: Announcement) => {
    // Show on every login - only skip if already shown in this page load session
    if (shownThisSessionRef.current.has(active.id)) return;
    shownThisSessionRef.current.add(active.id);
    setAnnouncement(active);
    setTimeLeft(active.durationSeconds || 15);
    setIsPlaying(true);
    setIsMuted(false);
    setIsOpen(true);
  };

  const fetchActive = async () => {
    try {
      const res = await api.get<{ data: Announcement | null }>("/global-announcements/active");
      const active = res.data?.data;
      if (active && active.status === "ACTIVE") {
        showAnnouncement(active);
      }
    } catch (err) {
      console.error("Failed to fetch active announcement", err);
    }
  };

  useEffect(() => {
    if (!user) return;

    // Short delay so the dashboard renders first, then popup appears
    const delay = setTimeout(() => {
      fetchActive();
    }, 800);

    const protocol = window.location.protocol === "https:" ? "https:" : "http:";
    const host = window.location.host;
    const wsUrl = `${protocol}//${host}/ws`;

    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      connectHeaders: {
        Authorization: tokenStore.access ? `Bearer ${tokenStore.access}` : ""
      },
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe("/topic/global-announcement", (message) => {
          try {
            const body = JSON.parse(message.body);
            if (body.action === "PUBLISHED" && body.status === "ACTIVE") {
              const newAnn: Announcement = {
                id: body.id,
                title: body.title,
                description: body.description,
                mediaType: body.mediaType,
                mediaUrl: body.mediaUrl,
                status: "ACTIVE",
                targetRoles: body.targetRoles,
                durationSeconds: body.durationSeconds || 15,
                effectUrl: body.effectUrl,
                effectEnabled: body.effectEnabled
              };
              // Real-time: always show immediately for new published announcements
              shownThisSessionRef.current.delete(body.id);
              showAnnouncement(newAnn);
            } else if (body.action === "DELETED" || body.action === "INACTIVATED") {
              setAnnouncement((prev) => {
                if (prev && prev.id === body.id) {
                  setIsOpen(false);
                  return null;
                }
                return prev;
              });
            }
          } catch (e) {
            console.error("STOMP announcement parse error", e);
          }
        });
      }
    });

    client.activate();

    return () => {
      clearTimeout(delay);
      client.deactivate();
    };
  }, [user]);

  // Reset shown set when user changes (i.e. new login)
  useEffect(() => {
    shownThisSessionRef.current.clear();
  }, [user?.id]);

  // Countdown timer
  useEffect(() => {
    if (!isOpen || !announcement) return;

    if (timerRef.current) clearInterval(timerRef.current);

    timerRef.current = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev <= 1) {
          clearInterval(timerRef.current);
          handleClose();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [isOpen, announcement?.id]);

  const handleClose = () => {
    setIsOpen(false);
    if (videoRef.current) {
      videoRef.current.pause();
    }
    if (timerRef.current) clearInterval(timerRef.current);
  };

  const toggleMute = () => {
    if (videoRef.current) {
      videoRef.current.muted = !isMuted;
      setIsMuted(!isMuted);
    }
  };

  const togglePlay = () => {
    if (videoRef.current) {
      if (isPlaying) {
        videoRef.current.pause();
      } else {
        videoRef.current.play();
      }
      setIsPlaying(!isPlaying);
    }
  };

  if (!isOpen || !announcement) return null;

  const resolvedUrl = resolvePhotoUrl(announcement.mediaUrl) || announcement.mediaUrl;
  const duration = announcement.durationSeconds || 15;

  // Null unless there is an effect and it is switched on. Uploading one and
  // turning it off has to leave the popup exactly as it was without one.
  const effectUrl =
    announcement.effectEnabled && announcement.effectUrl
      ? resolvePhotoUrl(announcement.effectUrl) || announcement.effectUrl
      : null;
  const progressPercent = Math.max(0, Math.min(100, ((duration - timeLeft) / duration) * 100));

  return (
    <div
      style={{ position: "fixed", inset: 0, zIndex: 99999 }}
      className="flex flex-col bg-black"
    >
      {/* Full-screen background blurred media */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        {announcement.mediaType === "VIDEO" ? (
          <video
            src={resolvedUrl}
            className="h-full w-full object-cover opacity-20 blur-2xl scale-110"
            autoPlay
            loop
            muted
            playsInline
          />
        ) : (
          <img
            src={resolvedUrl}
            alt=""
            className="h-full w-full object-cover opacity-20 blur-2xl scale-110"
          />
        )}
      </div>

      {/*
        The entrance effect, over everything.

        Full bleed and above the media rather than beside it: the point of a
        light sweep is that it crosses the poster, and a small player in a
        corner would be decoration instead of an entrance.

        pointer-events-none throughout, so the animation never swallows the
        close button underneath it -- an effect that traps somebody in the popup
        is worse than no effect. It plays once rather than looping; a loop turns
        an entrance into a distraction for the full fifteen seconds.
      */}
      {effectUrl ? (
        <div className="absolute inset-0 z-30 pointer-events-none">
          <Lottie
            src={effectUrl}
            autoplay
            loop={false}
            className="h-full w-full"
          />
        </div>
      ) : null}

      {/* Top Header Bar */}
      <div className="absolute top-0 left-0 right-0 z-20 flex items-center justify-between px-6 py-4 bg-gradient-to-b from-black/80 to-transparent">
        <div className="flex items-center gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-xl bg-primary text-primary-foreground text-sm font-black shadow-lg">
            HR
          </div>
          <div className="flex flex-col">
            <span className="text-sm font-bold tracking-widest text-white uppercase">COMPANY PORTAL</span>
            <span className="text-[11px] text-white/60">Official Announcement</span>
          </div>
        </div>

        {/* Circular Countdown Timer + Close */}
        <div className="flex items-center gap-3">
          <div className="relative grid h-14 w-14 place-items-center">
            <svg className="h-14 w-14 -rotate-90 transform" viewBox="0 0 36 36">
              <path
                stroke="rgba(255,255,255,0.15)"
                strokeWidth="3"
                fill="none"
                d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
              />
              <path
                stroke="#6366f1"
                strokeDasharray={`${progressPercent}, 100`}
                strokeWidth="3"
                strokeLinecap="round"
                fill="none"
                style={{ transition: "stroke-dasharray 1s linear" }}
                d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
              />
            </svg>
            <span className="absolute text-lg font-black text-white tabular-nums">{timeLeft}</span>
          </div>
          <div className="flex flex-col text-right mr-1">
            <span className="text-[10px] font-bold uppercase tracking-wider text-white/50">SECS</span>
            <span className="text-xs font-semibold text-white/80">Auto-close</span>
          </div>
          <button
            onClick={handleClose}
            className="grid h-10 w-10 place-items-center rounded-full bg-white/10 text-white hover:bg-white/25 transition-all border border-white/20"
            title="Close"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
      </div>

      {/* Full-Screen Media */}
      <div className="relative flex-1 w-full h-full overflow-hidden flex items-center justify-center">
        {announcement.mediaType === "VIDEO" ? (
          <>
            <video
              ref={videoRef}
              src={resolvedUrl}
              className="w-full h-full object-cover"
              autoPlay
              loop
              muted={isMuted}
              playsInline
              style={{ objectFit: "cover" }}
            />
            {/* Video Controls - bottom right */}
            <div className="absolute bottom-20 right-6 flex items-center gap-2 rounded-full bg-black/60 backdrop-blur-md px-4 py-2 border border-white/20">
              <button onClick={togglePlay} className="text-white hover:text-primary transition-colors" title={isPlaying ? "Pause" : "Play"}>
                {isPlaying ? <Pause className="h-5 w-5" /> : <Play className="h-5 w-5" />}
              </button>
              <div className="w-px h-4 bg-white/30" />
              <button onClick={toggleMute} className="text-white hover:text-primary transition-colors" title={isMuted ? "Unmute" : "Mute"}>
                {isMuted ? <VolumeX className="h-5 w-5" /> : <Volume2 className="h-5 w-5" />}
              </button>
            </div>
          </>
        ) : (
          <img
            src={resolvedUrl}
            alt={announcement.title || "Announcement"}
            className="w-full h-full"
            style={{ objectFit: "cover" }}
          />
        )}

        {/* Title / Description Overlay at bottom */}
        {(announcement.title || announcement.description) && (
          <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black via-black/60 to-transparent px-10 py-10">
            {announcement.title && (
              <h2 className="text-3xl sm:text-5xl font-black text-white tracking-tight drop-shadow-lg">
                {announcement.title}
              </h2>
            )}
            {announcement.description && (
              <p className="mt-3 text-base sm:text-lg text-white/85 max-w-3xl font-medium drop-shadow">
                {announcement.description}
              </p>
            )}
          </div>
        )}
      </div>

      {/* Bottom Info Bar */}
      <div className="absolute bottom-0 left-0 right-0 z-20 flex items-center justify-center py-4 bg-gradient-to-t from-black/80 to-transparent">
        <div className="inline-flex items-center gap-2 rounded-full border border-primary/40 bg-primary/20 backdrop-blur-md px-6 py-2.5 text-sm font-semibold text-white shadow-xl">
          <Megaphone className="h-4 w-4 text-primary animate-pulse" />
          <span>
            This message will close automatically after{" "}
            <span className="font-black text-primary tabular-nums">{timeLeft} seconds</span>.
          </span>
        </div>
      </div>
    </div>
  );
}
