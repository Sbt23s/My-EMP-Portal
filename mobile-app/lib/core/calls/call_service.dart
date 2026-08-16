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
/// speak the same six messages — calling, offer, answer, candidate, hangup,
/// busy. A call between a phone and a desk is therefore one protocol rather than
/// a bridge between two.
///
/// ## What will and will not connect
///
/// The ICE configuration is STUN-only, matching the web client exactly. STUN
/// tells each side its own public address, which is enough when at least one end
/// is behind a friendly NAT — two devices on the same office wifi, or one on
/// wifi and one on a home connection.
///
/// It is **not** enough when both ends are on mobile data. Carrier NAT is
/// symmetric: the address STUN reports is valid only for the STUN server, and
/// the other phone cannot use it. Those calls will ring, be answered, and carry
/// no media.
///
/// That is not a limitation of this code — the web portal has it today, for the
/// same reason. The fix is a TURN server to relay when no direct path exists
/// (see `setup-turn.sh`), and [iceServers] reads its configuration from the
/// backend so that adding one needs no change here and no new build.
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

  /// The offer that arrived with an incoming call, held until it is accepted.
  Map<String, dynamic>? _pendingOffer;

  /// Candidates that arrived before the remote description was set.
  ///
  /// WebRTC rejects a candidate added before the description it belongs to, and
  /// the two race on every call — the caller's candidates routinely arrive
  /// before the callee has finished answering. Dropping them makes a call that
  /// connects on a fast network and silently fails on a slow one.
  final List<RTCIceCandidate> _earlyCandidates = [];

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
      _state = CallState.connected;
      _emit();
    };

    pc.onConnectionState = (s) {
      // A connection that drops mid-call must not leave the call screen up over
      // nothing. This is also where a STUN-only call between two mobile
      // networks ends up: negotiated, then failed.
      if (s == RTCPeerConnectionState.RTCPeerConnectionStateFailed ||
          s == RTCPeerConnectionState.RTCPeerConnectionStateClosed ||
          s == RTCPeerConnectionState.RTCPeerConnectionStateDisconnected) {
        hangUp(notify: false);
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

  /// Ring somebody.
  Future<void> call(CallPeer to, {required bool video}) async {
    if (isBusy) return;

    _peer = to;
    _isVideo = video;
    _state = CallState.outgoing;
    _emit();

    try {
      await _openMedia(video: video);
      await _openPeerConnection();

      final offer = await _pc!.createOffer();
      await _pc!.setLocalDescription(offer);

      // "calling" first, so their phone rings while the offer is still being
      // built — and so the server raises its notification for it.
      await _send(to.id, 'calling', {'video': video});
      await _send(to.id, 'offer', {
        'sdp': offer.sdp,
        'type': offer.type,
        'video': video,
      });
    } catch (_) {
      await hangUp();
    }
  }

  /// Pick up.
  Future<void> accept() async {
    final offer = _pendingOffer;
    final from = _peer;
    if (offer == null || from == null) return;

    _state = CallState.connecting;
    _emit();

    try {
      await _openMedia(video: _isVideo);
      await _openPeerConnection();

      await _pc!.setRemoteDescription(
        RTCSessionDescription(offer['sdp'] as String?, offer['type'] as String?),
      );

      // Anything that arrived early can go in now that there is a description
      // for it to attach to.
      for (final c in _earlyCandidates) {
        await _pc!.addCandidate(c);
      }
      _earlyCandidates.clear();

      final answer = await _pc!.createAnswer();
      await _pc!.setLocalDescription(answer);
      await _send(from.id, 'answer', {'sdp': answer.sdp, 'type': answer.type});

      _pendingOffer = null;
    } catch (_) {
      await hangUp();
    }
  }

  /// Refuse, or end.
  Future<void> hangUp({bool notify = true}) async {
    final to = _peer?.id;
    if (notify && to != null) await _send(to, 'hangup', null);

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
    _state = CallState.idle;
    _muted = false;
    _speakerOn = false;
    _emit();
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
        if (isBusy) {
          await _send(fromId, 'busy', null);
          return;
        }
        _peer = CallPeer(id: fromId, name: fromName);
        _isVideo = data['video'] == true;
        _state = CallState.incoming;
        _emit();

      case 'offer':
        // The offer can arrive before or after "calling" depending on timing, so
        // this sets up the ringing state as well rather than assuming it exists.
        if (isBusy && _peer?.id != fromId) {
          await _send(fromId, 'busy', null);
          return;
        }
        _peer = CallPeer(id: fromId, name: fromName);
        _isVideo = data['video'] == true || _isVideo;
        _pendingOffer = data;
        if (_state == CallState.idle) _state = CallState.incoming;
        _emit();

      case 'answer':
        if (_pc == null) return;
        await _pc!.setRemoteDescription(
          RTCSessionDescription(data['sdp'] as String?, data['type'] as String?),
        );
        for (final c in _earlyCandidates) {
          await _pc!.addCandidate(c);
        }
        _earlyCandidates.clear();
        _state = CallState.connecting;
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

      case 'hangup':
      case 'busy':
        await hangUp(notify: false);
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
