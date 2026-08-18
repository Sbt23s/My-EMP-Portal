import React, { useState, useEffect, useRef } from "react";
import { X, Volume2, VolumeX, Play, Pause, Megaphone } from "lucide-react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { api, tokenStore } from "@/lib/api";
import { useAuth } from "@/context/AuthContext";

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
}

export function GlobalLoginAnnouncementModal() {
  const { user } = useAuth();
  const [announcement, setAnnouncement] = useState<Announcement | null>(null);
  const [isOpen, setIsOpen] = useState(false);
  const [timeLeft, setTimeLeft] = useState(15);
  const [isMuted, setIsMuted] = useState(true);
  const [isPlaying, setIsPlaying] = useState(true);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const timerRef = useRef<NodeJS.Timeout | null>(null);

  // Fetch active announcement on mount or when user changes
  useEffect(() => {
    if (!user) return;

    let isMounted = true;

    async function fetchActive() {
      try {
        const res = await api.get<{ data: Announcement | null }>("/global-announcements/active");
        const active = res.data?.data;
        if (!isMounted) return;

        if (active && active.status === "ACTIVE") {
          // Check if already seen in current browser session
          const seenKey = `seen_announcement_${active.id}`;
          const hasSeen = sessionStorage.getItem(seenKey);
          if (!hasSeen) {
            setAnnouncement(active);
            setTimeLeft(active.durationSeconds || 15);
            setIsOpen(true);
          }
        }
      } catch (err) {
        console.error("Failed to fetch active announcement", err);
      }
    }

    fetchActive();

    // Subscribe to WebSocket / STOMP real-time updates
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
              // Check target roles
              const userRole = user.roles?.[0] || "Employee";
              const targetRoles = (body.targetRoles || "Employee,TL,HR,Admin").split(",");
              const matches = targetRoles.some(
                (r: string) => r.trim().toLowerCase() === userRole.toLowerCase() || r.trim() === "All" || userRole.toLowerCase().includes("admin")
              );
              if (matches) {
                const newAnn: Announcement = {
                  id: body.id,
                  title: body.title,
                  description: body.description,
                  mediaType: body.mediaType,
                  mediaUrl: body.mediaUrl,
                  status: "ACTIVE",
                  targetRoles: body.targetRoles,
                  durationSeconds: body.durationSeconds || 15
                };
                setAnnouncement(newAnn);
                setTimeLeft(newAnn.durationSeconds);
                setIsOpen(true);
              }
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
      isMounted = false;
      client.deactivate();
    };
  }, [user]);

  // Countdown timer logic
  useEffect(() => {
    if (!isOpen || !announcement) return;

    timerRef.current = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev <= 1) {
          clearInterval(timerRef.current!);
          handleClose();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [isOpen, announcement]);

  const handleClose = () => {
    if (announcement) {
      sessionStorage.setItem(`seen_announcement_${announcement.id}`, "true");
    }
    setIsOpen(false);
    if (videoRef.current) {
      videoRef.current.pause();
    }
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

  const duration = announcement.durationSeconds || 15;
  const progressPercent = Math.max(0, Math.min(100, ((duration - timeLeft) / duration) * 100));

  return (
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/90 backdrop-blur-xl animate-in fade-in duration-300">
      {/* Background Media Blur Container */}
      <div className="absolute inset-0 overflow-hidden opacity-30 pointer-events-none">
        {announcement.mediaType === "VIDEO" ? (
          <video src={announcement.mediaUrl} className="h-full w-full object-cover blur-2xl scale-110" autoPlay loop muted playsInline />
        ) : (
          <img src={announcement.mediaUrl} alt="" className="h-full w-full object-cover blur-2xl scale-110" />
        )}
      </div>

      {/* Top Header Bar */}
      <div className="absolute top-0 left-0 right-0 z-20 flex items-center justify-between px-8 py-6">
        <div className="flex items-center gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-xl bg-primary text-primary-foreground font-bold shadow-lg shadow-primary/30">
            HR
          </div>
          <div className="flex flex-col">
            <span className="text-sm font-bold tracking-wide text-white uppercase">COMPANY PORTAL</span>
            <span className="text-[11px] text-white/70">Official Announcement</span>
          </div>
        </div>

        {/* 15-Second Circular Countdown Timer */}
        <div className="flex items-center gap-4">
          <div className="relative grid h-16 w-16 place-items-center">
            <svg className="h-16 w-16 -rotate-90 transform" viewBox="0 0 36 36">
              <path
                className="text-white/10"
                strokeWidth="3"
                stroke="currentColor"
                fill="none"
                d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
              />
              <path
                className="text-primary transition-all duration-1000 ease-linear"
                strokeDasharray={`${progressPercent}, 100`}
                strokeWidth="3"
                strokeLinecap="round"
                stroke="currentColor"
                fill="none"
                d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
              />
            </svg>
            <div className="absolute flex flex-col items-center justify-center text-center">
              <span className="font-display text-xl font-black text-white tabular-nums leading-none">{timeLeft}</span>
            </div>
          </div>
          <div className="hidden sm:flex flex-col text-right">
            <span className="text-[10px] font-bold uppercase tracking-wider text-white/60">SECS REMAINING</span>
            <span className="text-xs font-semibold text-white">Auto-closing</span>
          </div>

          <button
            onClick={handleClose}
            className="grid h-10 w-10 place-items-center rounded-full bg-white/10 text-white hover:bg-white/20 transition-all border border-white/20 shadow-lg"
            title="Close popup"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
      </div>

      {/* Main Content Modal Container */}
      <div className="relative z-10 mx-auto w-full max-w-5xl px-4 py-8">
        <div className="relative overflow-hidden rounded-3xl border border-white/20 bg-slate-950/80 shadow-2xl backdrop-blur-2xl">
          {/* Media Display Window */}
          <div className="relative aspect-video w-full overflow-hidden bg-black grid place-items-center">
            {announcement.mediaType === "VIDEO" ? (
              <>
                <video
                  ref={videoRef}
                  src={announcement.mediaUrl}
                  className="h-full w-full object-contain"
                  autoPlay
                  loop
                  muted={isMuted}
                  playsInline
                />
                {/* Video Controls Overlay */}
                <div className="absolute bottom-4 right-4 flex items-center gap-2 rounded-full bg-black/60 backdrop-blur-md px-3 py-1.5 border border-white/20">
                  <button onClick={togglePlay} className="text-white hover:text-primary transition-colors">
                    {isPlaying ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
                  </button>
                  <button onClick={toggleMute} className="text-white hover:text-primary transition-colors">
                    {isMuted ? <VolumeX className="h-4 w-4" /> : <Volume2 className="h-4 w-4" />}
                  </button>
                </div>
              </>
            ) : (
              <img
                src={announcement.mediaUrl}
                alt={announcement.title || "Announcement"}
                className="h-full w-full object-contain"
              />
            )}

            {/* Content Text Overlay */}
            {(announcement.title || announcement.description) && (
              <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/95 via-black/60 to-transparent p-8 text-center sm:text-left">
                {announcement.title && (
                  <h2 className="text-2xl sm:text-4xl font-black text-white tracking-tight drop-shadow-md">
                    {announcement.title}
                  </h2>
                )}
                {announcement.description && (
                  <p className="mt-2 text-sm sm:text-base text-white/80 max-w-2xl drop-shadow-sm font-medium">
                    {announcement.description}
                  </p>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Bottom Closing Info Badge */}
        <div className="mt-4 flex items-center justify-center">
          <div className="inline-flex items-center gap-2 rounded-full border border-primary/40 bg-primary/20 px-5 py-2 text-xs font-semibold text-white shadow-lg backdrop-blur-md">
            <Megaphone className="h-4 w-4 text-primary animate-pulse" />
            <span>This message will close automatically after <span className="font-bold text-primary tabular-nums">{timeLeft} seconds</span>.</span>
          </div>
        </div>
      </div>
    </div>
  );
}
