import 'dart:async';
import 'dart:convert';

import 'package:flutter_webrtc/flutter_webrtc.dart';

import '../network/api_client.dart';
import '../realtime/realtime_service.dart';

/// Where a call is.
enum CallState {
  idle,

  /// We rang somebody and are waiting for them to pick up.
  outgoing,

  /// Somebody is ringing us.
  incoming,

  /// Negotiating — accepted, media not flowing yet.
  connecting,

  /// Audio or video is passing.
  connected,
}

/// Who is on the other end.
class CallPeer {
  const CallPeer({required this.id, required this.name});
  final int id;
  final String name;
}

/// Voice and video, over the same WebRTC the browser uses.
///
/// Signalling goes through the STOMP socket that is already open: the server
/// routes `POST /calls/signal` to `/topic/calls/{recipientId}`, and both clients
/// speak the same seven messages — calling, ringing, offer, answer, candidate,
/// decline, hangup.
///
/// ## The handshake — why the offer waits for "ringing"
///
/// The web client orders the call so that a phone which is not listening still
/// gets a working call when it starts listening. The caller opens its own
/// devices, says "calling", and then *waits*. Only when the other side answers
/// with "ringing" — which it cannot do until it is subscribed — does the offer
/// go out. Sending the offer alongside "calling" rings nobody and wastes an SDP
/// the callee was not ready for. This file previously did exactly that, which
/// is why a browser calling a phone rang forever.
///
/// The SDP is serialised the way the web serialises it — the whole
/// RTCSessionDescription (`{sdp, type}`) nested under `data.sdp` — and both
/// forms are accepted on the way in, so a phone and a browser read each other.
///
/// ## What will and will not connect
///
/// The ICE configuration is read from the backend when it offers one (see
/// `GET /calls/ice-servers`) and falls back to STUN-only otherwise, matching
/// the web client exactly. STUN tells each side its own public address, which
/// is enough when at least one end is behind a friendly NAT — two devices on
/// the same office wifi, or one on wifi and one on a home connection.
///
/// It is **not** enough when both ends are on mobile data. Carrier NAT is
/// symmetric: the address STUN reports is valid only for the STUN server, and
/// the other phone cannot use it. The fix is a TURN server to relay when no
/// direct path exists (`setup-turn.sh`), and the backend's ice-servers endpoint
/// returns its configuration so that adding one needs no app release.
class CallService {
  CallService({required ApiClient api, required RealtimeService realtime})
      : _api = api,
        _realtime = realtime;

  final ApiClient _api;
  final RealtimeService _realtime;

  RTCPeerConnection? _pc;
  MediaStream? _localStream;
  MediaStream? _remoteStream;
  StreamSubscription<RealtimeEvent>? _signals;

  CallState _state = CallState.idle;
  CallPeer? _peer;
  bool _isVideo = false;
  bool _muted = false;
  bool _speakerOn = false;

  /// True on the side that placed the call — the side that offers.
  bool _isCaller = false;

  /// The offer that arrived with an incoming call, held until it is accepted.
  Map<String, dynamic>? _pendingOffer;

  /// Candidates that arrived before the remote description was set.
  ///
  /// WebRTC rejects a candidate added before the description it belongs to, and
  /// the two race on every call — the caller's candidates routinely arrive
  /// before the callee has finished answering. Dropping them makes a call that
  /// connects on a fast network and silently fails on a slow one.
  final List<RTCIceCandidate> _earlyCandidates = [];

  /// Guards against sending a second offer if "ringing" arrives twice.
  bool _offerSent = false;

  /// When the conversation started, so a call log can say how long it ran.
  DateTime? _connectedAt;

  /// How long a call may ring before it is given up on as unanswered. The same
  /// 45 seconds the web client uses — one number, so the two cannot disagree.
  static const _noAnswer = Duration(seconds: 45);

  Timer? _ringTimer;

  final _changes = StreamController<void>.broadcast();

  /// Emits whenever anything below changes, so one listener redraws the UI.
  Stream<void> get changes => _changes.stream;

  CallState get state => _state;
  CallPeer? get peer => _peer;
  bool get isVideo => _isVideo;
  bool get isMuted => _muted;
  bool get isSpeakerOn => _speakerOn;
  MediaStream? get localStream => _localStream;
  MediaStream? get remoteStream => _remoteStream;
  bool get isBusy => _state != CallState.idle;

  void _emit() {
    if (!_changes.isClosed) _changes.add(null);
  }

  /// Start listening for calls addressed to this person.
  void bind(int myUserId) {
    final topic = '/topic/calls/$myUserId';
    _realtime.subscribe(topic);
    _signals?.cancel();
    _signals = _realtime.events.listen((event) {
      if (event.topic != topic) return;
      _onSignal(event.body);
    });
  }

  Future<void> _send(int to, String type, Map<String, dynamic>? data) async {
    try {
      await _api.post('/calls/signal', body: {
        'recipientId': to,
        'type': type,
        if (data != null) 'data': data,
      });
    } catch (_) {
      // A signal that cannot be delivered is not worth an error on screen — the
      // call will simply not progress, and hangup below cleans up regardless.
    }
  }

  /// STUN and, when the backend offers them, TURN.
  ///
  /// Read from the server rather than hard-coded so that standing up a TURN
  /// server is a deployment change and not an app release. Falls back to the
  /// same public STUN pair the web client uses, which is what exists today.
  Future<Map<String, dynamic>> _iceServers() async {
    try {
      final data = await _api.get('/calls/ice-servers');
      if (data is Map<String, dynamic> && data['iceServers'] is List) {
        return {'iceServers': data['iceServers']};
      }
    } catch (_) {
      // No endpoint yet. Expected — it does not exist until a TURN server does.
    }
    return {
      'iceServers': [
        {'urls': 'stun:stun.l.google.com:19302'},
        {'urls': 'stun:stun1.l.google.com:19302'},
      ],
    };
  }

  Future<void> _openPeerConnection() async {
    final pc = await createPeerConnection(await _iceServers());
    _pc = pc;

    for (final track in _localStream!.getTracks()) {
      await pc.addTrack(track, _localStream!);
    }

    pc.onIceCandidate = (candidate) {
      final to = _peer?.id;
      if (to == null) return;
      _send(to, 'candidate', {'candidate': candidate.toMap()});
    };

    pc.onTrack = (event) {
      if (event.streams.isEmpty) return;
      _remoteStream = event.streams.first;
      _connectedAt ??= DateTime.now();
      _state = CallState.connected;
      _ringTimer?.cancel();
      _emit();
    };

    pc.onConnectionState = (s) {
      // A connection that drops mid-call must not leave the call screen up over
      // nothing. Only failed and closed end it: disconnected is transient on a
      // phone (one bar, a tunnel, a handover) and WebRTC recovers on its own.
      if (s == RTCPeerConnectionState.RTCPeerConnectionStateFailed ||
          s == RTCPeerConnectionState.RTCPeerConnectionStateClosed) {
        if (_state != CallState.idle) hangUp(notify: false);
      }
    };
  }

  Future<void> _openMedia({required bool video}) async {
    _localStream = await navigator.mediaDevices.getUserMedia({
      'audio': true,
      'video': video
          ? {
              'facingMode': 'user',
              // Modest, deliberately. A phone will happily capture 1080p and
              // then fail to send it on a site with one bar of signal.
              'width': {'ideal': 640},
              'height': {'ideal': 480},
              'frameRate': {'ideal': 24},
            }
          : false,
    });
  }

  /// The elapsed call time, for the call log.
  int get _elapsedSeconds =>
      _connectedAt == null ? 0 : DateTime.now().difference(_connectedAt!).inSeconds;

  /// Ring somebody.
  ///
  /// The offer deliberately does **not** go out here: it waits for "ringing",
  /// exactly as the web client does, so a callee who is not listening yet still
  /// receives a working call.
  Future<void> call(CallPeer to, {required bool video}) async {
    if (isBusy) return;

    _peer = to;
    _isVideo = video;
    _isCaller = true;
    _offerSent = false;
    _state = CallState.outgoing;
    _emit();
    _startRingTimer();

    try {
      await _openMedia(video: video);
      await _openPeerConnection();

      // "calling" first, so their phone rings while the offer is still being
      // built — and so the server raises its notification for it.
      await _send(to.id, 'calling', {'isVideo': video});
    } catch (_) {
      await hangUp(notify: false);
    }
  }

  /// The other side is listening — now the offer can go.
  Future<void> _onRinging() async {
    if (!_isCaller || _pc == null || _offerSent) return;
    _offerSent = true;
    try {
      final offer = await _pc!.createOffer();
      await _pc!.setLocalDescription(offer);
      await _send(_peer!.id, 'offer', {
        'sdp': offer.toMap(),
        'isVideo': _isVideo,
      });
    } catch (_) {
      await hangUp(notify: false);
    }
  }

  /// Pick up.
  Future<void> accept() async {
    final from = _peer;
    if (from == null) return;

    _ringTimer?.cancel();
    _state = CallState.connecting;
    _emit();

    try {
      await _openMedia(video: _isVideo);
      await _openPeerConnection();

      // The offer may have landed while the camera was opening, so it is read
      // again after setup rather than captured before — the web client re-reads
      // for the same reason. If it still has not arrived, say "ringing" and let
      // the arriving offer be answered in the offer case below; the caller
      // re-sends on ringing, so nothing is lost.
      final offer = _pendingOffer;
      if (offer == null) {
        await _send(from.id, 'ringing', null);
        return;
      }
      await _answer(offer);
    } catch (_) {
      await hangUp(notify: false);
    }
  }

  /// Answer the held offer on the callee side.
  Future<void> _answer(Map<String, dynamic> offer) async {
    final from = _peer;
    if (from == null || _pc == null) return;

    final desc = sessionDescription(offer);
    if (desc == null) {
      await hangUp(notify: false);
      return;
    }

    await _pc!.setRemoteDescription(desc);
    for (final c in _earlyCandidates) {
      await _pc!.addCandidate(c);
    }
    _earlyCandidates.clear();

    final answer = await _pc!.createAnswer();
    await _pc!.setLocalDescription(answer);
    await _send(from.id, 'answer', {'sdp': answer.toMap()});

    _pendingOffer = null;
    _connectedAt ??= DateTime.now();
    _state = CallState.connecting;
    _emit();
  }

  /// Refuse, or end.
  Future<void> hangUp({bool notify = true}) async {
    final to = _peer?.id;
    final wasIncoming = !_isCaller && _state == CallState.incoming;
    final wasConnected = _state == CallState.connected;

    if (notify && to != null) {
      await _send(to, 'hangup', null);
      await _logCall(
        to,
        outcome: wasConnected ? 'ENDED' : (wasIncoming ? 'DECLINED' : 'MISSED'),
        seconds: _elapsedSeconds,
      );
    }

    _ringTimer?.cancel();
    await _pc?.close();
    _pc = null;

    // Tracks are stopped explicitly. Disposing the stream alone can leave the
    // camera light on, which is the single most alarming thing an app can do.
    for (final track in _localStream?.getTracks() ?? const <MediaStreamTrack>[]) {
      await track.stop();
    }
    await _localStream?.dispose();
    _localStream = null;
    _remoteStream = null;

    _pendingOffer = null;
    _earlyCandidates.clear();
    _peer = null;
    _isCaller = false;
    _offerSent = false;
    _connectedAt = null;
    _state = CallState.idle;
    _muted = false;
    _speakerOn = false;
    _emit();
  }

  /// Decline an incoming call without letting it ring out.
  Future<void> decline() async {
    final to = _peer?.id;
    if (to != null) {
      await _send(to, 'decline', null);
      await _logCall(to, outcome: 'DECLINED', seconds: 0);
    }
    await hangUp(notify: false);
  }

  Future<void> toggleMute() async {
    final track = _localStream?.getAudioTracks().firstOrNull;
    if (track == null) return;
    _muted = !_muted;
    track.enabled = !_muted;
    _emit();
  }

  Future<void> toggleSpeaker() async {
    _speakerOn = !_speakerOn;
    await Helper.setSpeakerphoneOn(_speakerOn);
    _emit();
  }

  Future<void> switchCamera() async {
    final track = _localStream?.getVideoTracks().firstOrNull;
    if (track != null) await Helper.switchCamera(track);
  }

  void _startRingTimer() {
    _ringTimer?.cancel();
    _ringTimer = Timer(_noAnswer, () async {
      if (_state != CallState.outgoing && _state != CallState.incoming) return;
      // Nobody picked up. The caller logs it as missed; the callee simply stops
      // ringing. Either way the screen clears.
      final to = _peer?.id;
      if (_state == CallState.outgoing && to != null) {
        await _logCall(to, outcome: 'MISSED', seconds: 0);
      }
      await hangUp(notify: false);
    });
  }

  Future<void> _onSignal(Map<String, dynamic> body) async {
    final type = body['type']?.toString();
    final fromId = (body['senderId'] as num?)?.toInt();
    final fromName = body['senderName']?.toString() ?? 'Someone';
    if (type == null || fromId == null) return;

    final data = body['data'] is Map<String, dynamic>
        ? body['data'] as Map<String, dynamic>
        : (body['data'] is String
            ? _tryDecode(body['data'] as String)
            : <String, dynamic>{});

    switch (type) {
      case 'calling':
        // Already on a call: tell them rather than letting it ring unanswered.
        // The web client reads "decline" with a busy reason — say it the same
        // way, because the browser does not understand a bare "busy".
        if (isBusy) {
          await _send(fromId, 'decline', {'reason': 'busy'});
          return;
        }
        _isCaller = false;
        _offerSent = false;
        _peer = CallPeer(id: fromId, name: fromName);
        _isVideo = readVideo(data);
        _pendingOffer = null;
        _earlyCandidates.clear();
        _state = CallState.incoming;
        _emit();
        // Saying "ringing" is also what tells the caller to send the offer, so
        // it must only be said once this side is genuinely listening.
        await _send(fromId, 'ringing', null);
        // A call that is never answered stops ringing — on this side too.
        _startRingTimer();

      case 'ringing':
        // The other side is listening — send the offer now.
        await _onRinging();

      case 'offer':
        // If this side already accepted and was waiting for the offer, answer
        // immediately. Otherwise hold it until Accept is pressed.
        if (_pc != null && _localStream != null && !_isCaller) {
          try {
            await _answer(data);
          } catch (_) {
            await hangUp(notify: false);
          }
          break;
        }
        _peer = CallPeer(id: fromId, name: fromName);
        _isVideo = readVideo(data) || _isVideo;
        _pendingOffer = data;
        if (_state == CallState.idle) {
          _state = CallState.incoming;
          _emit();
          await _send(fromId, 'ringing', null);
        }

      case 'answer':
        if (_pc == null) return;
        final desc = sessionDescription(data);
        if (desc == null) return;
        await _pc!.setRemoteDescription(desc);
        for (final c in _earlyCandidates) {
          await _pc!.addCandidate(c);
        }
        _earlyCandidates.clear();
        _connectedAt ??= DateTime.now();
        _state = CallState.connecting;
        _ringTimer?.cancel();
        _emit();

      case 'candidate':
        final raw = data['candidate'];
        if (raw is! Map) return;
        final candidate = RTCIceCandidate(
          raw['candidate'] as String?,
          raw['sdpMid'] as String?,
          (raw['sdpMLineIndex'] as num?)?.toInt(),
        );
        final pc = _pc;
        // Held rather than dropped when there is no description yet — see the
        // note on _earlyCandidates.
        if (pc == null || (await pc.getRemoteDescription()) == null) {
          _earlyCandidates.add(candidate);
        } else {
          await pc.addCandidate(candidate);
        }

      case 'decline':
        if (_state != CallState.idle) {
          final busy = data['reason'] == 'busy';
          await _logCall(
            fromId,
            outcome: busy ? 'MISSED' : 'DECLINED',
            seconds: 0,
          );
        }
        await hangUp(notify: false);

      case 'hangup':
        if (_state == CallState.incoming) {
          final to = _peer?.id;
          if (to != null) await _logCall(to, outcome: 'MISSED', seconds: 0);
        }
        await hangUp(notify: false);
    }
  }

  /// The web sends the SDP as the whole description (`{sdp, type}`) under
  /// `data.sdp`; older mobile builds sent the flat strings. Accept both.
  ///
  /// Public (static, pure) because a phone and a browser disagreeing here is
  /// exactly the bug this file fixed, and a unit test should be able to pin it.
  static RTCSessionDescription? sessionDescription(Map<String, dynamic> data) {
    final raw = data['sdp'];
    if (raw is Map) {
      return RTCSessionDescription(
        raw['sdp']?.toString(),
        raw['type']?.toString(),
      );
    }
    if (raw is String && raw.isNotEmpty) {
      return RTCSessionDescription(raw, data['type']?.toString());
    }
    return null;
  }

  /// The web names the flag `isVideo`; older mobile builds called it `video`.
  /// Accept both so a phone and a browser always agree on what kind of call it
  /// is — a video call arriving as voice was the symptom of the disagreement.
  ///
  /// Public (static, pure) for the same reason as [sessionDescription].
  static bool readVideo(Map<String, dynamic> data) {
    final v = data['isVideo'] ?? data['video'];
    return v == true || v == 'true';
  }

  Future<void> _logCall(int to,
      {required String outcome, required int seconds}) async {
    try {
      await _api.post('/calls/log', body: {
        'recipientId': to,
        'outcome': outcome,
        'video': _isVideo,
        'seconds': seconds,
      });
    } catch (_) {
      // A call log is a courtesy; failing to write it must not surface as a
      // failed call to somebody who has just hung up.
    }
  }

  static Map<String, dynamic> _tryDecode(String raw) {
    try {
      final decoded = jsonDecode(raw);
      return decoded is Map<String, dynamic> ? decoded : <String, dynamic>{};
    } catch (_) {
      return <String, dynamic>{};
    }
  }

  Future<void> dispose() async {
    await _signals?.cancel();
    await hangUp(notify: false);
    await _changes.close();
  }
}
