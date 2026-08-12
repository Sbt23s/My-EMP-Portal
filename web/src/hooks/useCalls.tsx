import {
  createContext, useCallback, useContext, useEffect, useMemo, useRef, useState
} from "react";
import { useNavigate } from "react-router-dom";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import toast from "react-hot-toast";
import { api, tokenStore } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";
import { CallOverlay, type CallState } from "@/components/CallOverlay";

const BASE = import.meta.env.VITE_API_URL || "";

/** A call rings for this long before it is given up on as unanswered. */
const NO_ANSWER_MS = 45_000;

const iceServers = {
  iceServers: [
    { urls: "stun:stun.l.google.com:19302" },
    { urls: "stun:stun1.l.google.com:19302" }
  ]
};

interface CallSignal {
  senderId: number;
  senderName: string;
  type: string;
  data: any;
}

interface CallApi {
  callState: "idle" | CallState;
  activeCallPartner: { id: number; name: string } | null;
  callIsVideo: boolean;
  localStream: MediaStream | null;
  remoteStream: MediaStream | null;
  startCall: (partnerId: number, partnerName: string, isVideo: boolean) => Promise<void>;
  acceptCall: () => Promise<void>;
  rejectCall: () => void;
  hangUp: () => void;
  toggleTrack: (kind: "audio" | "video") => boolean;
}

const CallContext = createContext<CallApi | null>(null);

/**
 * Calling, for the whole portal rather than the chat page.
 *
 * <p>The engine used to live inside the chat hook, which meant the person being
 * called only ever heard about it if they happened to be sitting on Chat — from
 * anywhere else the phone simply never rang. It is mounted once, above the
 * routes, so a call arrives wherever somebody is working.
 *
 * <p>The handshake is ordered so that a late listener still connects. The caller
 * opens their own microphone, says "calling", and then <em>waits</em>. Only when
 * the other side answers with "ringing" — which it cannot do until it is
 * listening — does the offer go out. Answering opens the callee's devices and
 * replies; the caller learns the room is live from the answer.
 */
export function CallProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [callState, setCallState] = useState<"idle" | CallState>("idle");
  const [activeCallPartner, setActiveCallPartner] = useState<{ id: number; name: string } | null>(null);
  const [callIsVideo, setCallIsVideo] = useState(false);
  const [localStream, setLocalStream] = useState<MediaStream | null>(null);
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);
  const [muted, setMuted] = useState(false);
  const [cameraOff, setCameraOff] = useState(false);

  const pcRef = useRef<RTCPeerConnection | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const clientRef = useRef<Client | null>(null);
  const [connected, setConnected] = useState(false);

  // The signal handler is rebuilt on every render, so it reads live state from
  // refs rather than from a closure that was captured at subscribe time.
  const callStateRef = useRef(callState);
  useEffect(() => { callStateRef.current = callState; }, [callState]);
  const partnerRef = useRef(activeCallPartner);
  useEffect(() => { partnerRef.current = activeCallPartner; }, [activeCallPartner]);

  /** An offer that arrived before Accept was pressed. */
  const pendingOfferRef = useRef<{ senderId: number; sdp: any; isVideo: boolean } | null>(null);
  /** Candidates that arrived before there was a connection to feed them to. */
  const pendingCandidatesRef = useRef<any[]>([]);
  /** True on the side that placed the call, which is the side that offers. */
  const isCallerRef = useRef(false);
  /** Guards against sending a second offer if "ringing" arrives twice. */
  const offerSentRef = useRef(false);

  const sendSignal = useCallback(async (recipientId: number, type: string, data: any) => {
    try {
      await api.post("/calls/signal", { recipientId, type, data });
    } catch (e) {
      console.error("Failed to send calling signal", e);
    }
  }, []);

  /** Records the call in the conversation, so the chat shows it happened. */
  const logCall = useCallback(async (
    partnerId: number,
    outcome: "MISSED" | "DECLINED" | "ENDED",
    isVideo: boolean,
    seconds: number
  ) => {
    try {
      await api.post("/calls/log", { recipientId: partnerId, outcome, video: isVideo, seconds });
    } catch (e) {
      console.error("Could not record the call in the chat", e);
    }
  }, []);

  const cleanupCall = useCallback(() => {
    pendingOfferRef.current = null;
    pendingCandidatesRef.current = [];
    isCallerRef.current = false;
    offerSentRef.current = false;
    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((track) => track.stop());
      localStreamRef.current = null;
    }
    if (pcRef.current) {
      pcRef.current.close();
      pcRef.current = null;
    }
    setLocalStream(null);
    setRemoteStream(null);
    setActiveCallPartner(null);
    setCallState("idle");
    setMuted(false);
    setCameraOff(false);
  }, []);

  /** When the conversation started, so a call log can say how long it ran. */
  const connectedAtRef = useRef<number | null>(null);
  useEffect(() => {
    connectedAtRef.current = callState === "connected" ? Date.now() : null;
  }, [callState]);

  const elapsedSeconds = () =>
    connectedAtRef.current ? Math.round((Date.now() - connectedAtRef.current) / 1000) : 0;

  const drainCandidates = useCallback(async () => {
    const queued = pendingCandidatesRef.current;
    pendingCandidatesRef.current = [];
    for (const candidate of queued) {
      try {
        await pcRef.current?.addIceCandidate(new RTCIceCandidate(candidate));
      } catch (e) {
        console.error("Error adding queued ICE candidate", e);
      }
    }
  }, []);

  const setupPeerConnection = useCallback(async (partnerId: number, isVideo: boolean) => {
    // The camera and microphone are only offered to a secure page. On plain HTTP
    // navigator.mediaDevices does not exist at all, so say why rather than
    // failing with a browser message about permissions.
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      throw new Error(
        "Calls need a secure (https) connection. Ask your admin to enable HTTPS on the portal."
      );
    }
    const stream = await navigator.mediaDevices.getUserMedia({ video: isVideo, audio: true });
    localStreamRef.current = stream;
    setLocalStream(stream);

    const pc = new RTCPeerConnection(iceServers);
    pcRef.current = pc;
    stream.getTracks().forEach((track) => pc.addTrack(track, stream));

    pc.ontrack = (event) => {
      if (event.streams && event.streams[0]) setRemoteStream(event.streams[0]);
    };
    pc.onicecandidate = (event) => {
      if (event.candidate) sendSignal(partnerId, "candidate", { candidate: event.candidate });
    };
    // A connection that drops mid-call should not leave the screen covered.
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === "failed" || pc.connectionState === "closed") {
        if (callStateRef.current === "connected" || callStateRef.current === "connecting" || callStateRef.current === "ringing") {
          toast.error("The connection dropped.");
          cleanupCall();
        }
      }
    };
    return pc;
  }, [sendSignal, cleanupCall]);

  const startCall = useCallback(async (partnerId: number, partnerName: string, isVideo: boolean) => {
    if (callStateRef.current !== "idle") return;

    setActiveCallPartner({ id: partnerId, name: partnerName });
    setCallIsVideo(isVideo);
    setCallState("calling");
    pendingOfferRef.current = null;
    pendingCandidatesRef.current = [];
    isCallerRef.current = true;
    offerSentRef.current = false;

    try {
      // Own devices first: if they are unavailable there is no point making the
      // other phone ring, and the caller hears the reason straight away.
      await setupPeerConnection(partnerId, isVideo);
      // The offer waits for "ringing" — see the note on this provider.
      await sendSignal(partnerId, "calling", { isVideo });
    } catch (e) {
      cleanupCall();
      throw e;
    }
  }, [setupPeerConnection, sendSignal, cleanupCall]);

  const acceptCall = useCallback(async () => {
    const partner = partnerRef.current;
    // We read it here just to know if it's video, but we MUST read it again after setup.
    const initialOffer = pendingOfferRef.current;
    if (!partner) return;

    try {
      const isVideo = initialOffer?.isVideo ?? callIsVideo;
      const pc = await setupPeerConnection(partner.id, isVideo);

      if (pc.remoteDescription) {
        // The offer arrived and was processed by handleSignal while we were waiting for the camera!
        return;
      }

      // READ AGAIN! The offer might have arrived while we were waiting for the camera.
      const currentOffer = pendingOfferRef.current;

      if (!currentOffer) {
        // Answered before the offer landed. Ask again and let the arriving offer
        // be answered below; the caller re-sends on "ringing".
        setCallState("connecting");
        await sendSignal(partner.id, "ringing", null);
        return;
      }

      await pc.setRemoteDescription(new RTCSessionDescription(currentOffer.sdp));
      await drainCandidates();
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      await sendSignal(partner.id, "answer", { sdp: answer });
      pendingOfferRef.current = null;
      setCallState("connected");
    } catch (e: any) {
      if (partner) sendSignal(partner.id, "decline", null);
      cleanupCall();
      throw e;
    }
  }, [callIsVideo, setupPeerConnection, drainCandidates, sendSignal, cleanupCall]);

  const rejectCall = useCallback(() => {
    const partner = partnerRef.current;
    if (partner) {
      sendSignal(partner.id, "decline", null);
      logCall(partner.id, "DECLINED", callIsVideo, 0);
    }
    cleanupCall();
  }, [sendSignal, logCall, callIsVideo, cleanupCall]);

  const hangUp = useCallback(() => {
    const partner = partnerRef.current;
    if (partner) {
      sendSignal(partner.id, "hangup", null);
      const seconds = elapsedSeconds();
      // Hanging up before anybody answered is a missed call, not a short one.
      logCall(partner.id, seconds > 0 ? "ENDED" : "MISSED", callIsVideo, seconds);
    }
    cleanupCall();
  }, [sendSignal, logCall, callIsVideo, cleanupCall]);

  const toggleTrack = useCallback((kind: "audio" | "video") => {
    const tracks = kind === "audio"
      ? localStreamRef.current?.getAudioTracks()
      : localStreamRef.current?.getVideoTracks();
    if (!tracks || tracks.length === 0) return false;
    const next = !tracks[0].enabled;
    tracks.forEach((t) => { t.enabled = next; });
    return next;
  }, []);

  /** Signals arriving from the other side. */
  const handleSignal = useCallback(async (signal: CallSignal) => {
    const { senderId, senderName, type, data } = signal;

    switch (type) {
      case "calling":
        if (callStateRef.current === "idle") {
          pendingOfferRef.current = null;
          pendingCandidatesRef.current = [];
          isCallerRef.current = false;
          setActiveCallPartner({ id: senderId, name: senderName });
          setCallIsVideo(!!data?.isVideo);
          setCallState("incoming");
          // Saying "ringing" is also what tells the caller to send the offer,
          // so it must only be said once this side is genuinely listening.
          sendSignal(senderId, "ringing", null);
        } else {
          sendSignal(senderId, "decline", { reason: "busy" });
        }
        break;

      case "ringing":
        // The other side is listening — now the offer can go.
        if (isCallerRef.current && pcRef.current && !offerSentRef.current) {
          offerSentRef.current = true;
          setCallState("ringing");
          try {
            const offer = await pcRef.current.createOffer();
            await pcRef.current.setLocalDescription(offer);
            await sendSignal(senderId, "offer", { sdp: offer, isVideo: callIsVideo });
          } catch (e) {
            console.error("Could not offer the call", e);
            toast.error("Could not start the call.");
            cleanupCall();
          }
        }
        break;

      case "offer":
        // Held until Accept is pressed, unless this side already accepted and
        // was waiting for it.
        if (pcRef.current && localStreamRef.current && !isCallerRef.current) {
          try {
            await pcRef.current.setRemoteDescription(new RTCSessionDescription(data?.sdp));
            await drainCandidates();
            const answer = await pcRef.current.createAnswer();
            await pcRef.current.setLocalDescription(answer);
            await sendSignal(senderId, "answer", { sdp: answer });
            setCallState("connected");
          } catch (e) {
            console.error("Could not answer the call", e);
            cleanupCall();
          }
          break;
        }
        if (callStateRef.current === "idle" || callStateRef.current === "incoming"
            || callStateRef.current === "ringing" || callStateRef.current === "connecting") {
          pendingOfferRef.current = { senderId, sdp: data?.sdp, isVideo: !!data?.isVideo };
          setActiveCallPartner({ id: senderId, name: senderName });
          setCallIsVideo(!!data?.isVideo);
          if (callStateRef.current === "idle") setCallState("incoming");
        }
        break;

      case "answer":
        if (pcRef.current) {
          await pcRef.current.setRemoteDescription(new RTCSessionDescription(data.sdp));
          await drainCandidates();
          setCallState("connected");
        }
        break;

      case "candidate":
        if (!data?.candidate) break;
        if (!pcRef.current || !pcRef.current.remoteDescription) {
          pendingCandidatesRef.current.push(data.candidate);
          break;
        }
        try {
          await pcRef.current.addIceCandidate(new RTCIceCandidate(data.candidate));
        } catch (e) {
          console.error("Error adding received ICE candidate", e);
        }
        break;

      case "decline": {
        const busy = data?.reason === "busy";
        if (callStateRef.current !== "idle") {
          toast.error(busy ? "They are on another call." : "Call declined.");
          if (isCallerRef.current) logCall(senderId, busy ? "MISSED" : "DECLINED", callIsVideo, 0);
        }
        cleanupCall();
        break;
      }

      case "hangup":
        if (callStateRef.current === "incoming") toast("Missed call", { icon: "📞" });
        cleanupCall();
        break;

      default:
        break;
    }
  }, [sendSignal, drainCandidates, cleanupCall, logCall, callIsVideo]);

  const handlerRef = useRef(handleSignal);
  useEffect(() => { handlerRef.current = handleSignal; }, [handleSignal]);

  // One connection for the life of the session, so the phone can ring on any
  // page. Signals are dispatched through a ref so re-subscribing is unnecessary.
  useEffect(() => {
    if (!tokenStore.access || !user) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(`${BASE}/ws`),
      connectHeaders: { Authorization: `Bearer ${tokenStore.access}` },
      reconnectDelay: 5000,
      onConnect: () => {
        clientRef.current = client;
        setConnected(true);
        client.subscribe(`/topic/calls/${user.id}`, (msg) => {
          try {
            handlerRef.current(JSON.parse(msg.body) as CallSignal);
          } catch (e) {
            console.error("Invalid calling signal payload", e);
          }
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false)
    });

    client.activate();
    return () => {
      client.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
  }, [user?.id]);

  // A call nobody picks up stops ringing after three quarters of a minute.
  useEffect(() => {
    if (callState !== "calling" && callState !== "ringing" && callState !== "incoming") return;
    const timer = window.setTimeout(() => {
      const partner = partnerRef.current;
      if (partner) {
        sendSignal(partner.id, "hangup", null);
        if (isCallerRef.current) logCall(partner.id, "MISSED", callIsVideo, 0);
      }
      toast.error("No answer.");
      cleanupCall();
    }, NO_ANSWER_MS);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [callState]);

  /**
   * Answering from anywhere else in the portal opens the conversation as well,
   * so the call and its chat are in the same place. The room is asked for by
   * partner, since a direct chat is found or created on demand.
   */
  const onAccept = async () => {
    const partner = partnerRef.current;
    try {
      await acceptCall();
    } catch (e: any) {
      toast.error(e?.message || "Could not answer the call");
      return;
    }
    if (!partner) return;
    try {
      const res = await api.post<{ id: number }>(`/communities/direct/${partner.id}`);
      navigate(`/chat?c=${res.data.id}`);
    } catch {
      navigate("/chat");
    }
  };

  const value = useMemo<CallApi>(() => ({
    callState, activeCallPartner, callIsVideo, localStream, remoteStream,
    startCall, acceptCall, rejectCall, hangUp, toggleTrack
  }), [callState, activeCallPartner, callIsVideo, localStream, remoteStream,
    startCall, acceptCall, rejectCall, hangUp, toggleTrack]);

  return (
    <CallContext.Provider value={value}>
      {children}
      {/* Above the routes, so a call covers whatever page is open. */}
      {callState !== "idle" && activeCallPartner && (
        <CallOverlay
          state={callState}
          partnerName={activeCallPartner.name}
          isVideo={callIsVideo}
          localStream={localStream}
          remoteStream={remoteStream}
          muted={muted}
          cameraOff={cameraOff}
          onAccept={onAccept}
          onReject={rejectCall}
          onHangUp={hangUp}
          onToggleMute={() => setMuted(!toggleTrack("audio"))}
          onToggleCamera={() => setCameraOff(!toggleTrack("video"))}
        />
      )}
      {/* Silent when the socket is down, but a call cannot arrive then, so the
          calling buttons say so rather than failing quietly. */}
      <span className="hidden" data-calls-connected={connected ? "yes" : "no"} />
    </CallContext.Provider>
  );
}

/**
 * The calling API. Outside a provider it returns a dormant version rather than
 * throwing, so a page that only lists chats still renders.
 */
export function useCalls(): CallApi {
  const ctx = useContext(CallContext);
  return ctx ?? DORMANT;
}

const DORMANT: CallApi = {
  callState: "idle",
  activeCallPartner: null,
  callIsVideo: false,
  localStream: null,
  remoteStream: null,
  startCall: async () => { toast.error("Calling is not available here."); },
  acceptCall: async () => {},
  rejectCall: () => {},
  hangUp: () => {},
  toggleTrack: () => false
};
