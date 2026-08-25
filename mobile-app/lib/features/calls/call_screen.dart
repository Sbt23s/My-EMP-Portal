import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';

import '../../core/calls/call_service.dart';
import '../../providers/call_provider.dart';

/// The call, whichever end of it you are on.
///
/// One screen for ringing, answering and talking rather than three: the states
/// share the same furniture — who it is, what is happening, and the buttons —
/// and pushing a new route at each transition made the call flicker at exactly
/// the moment somebody is trying to hear it.
class CallScreen extends ConsumerStatefulWidget {
  const CallScreen({super.key});

  @override
  ConsumerState<CallScreen> createState() => _CallScreenState();
}

class _CallScreenState extends ConsumerState<CallScreen> {
  final _local = RTCVideoRenderer();
  final _remote = RTCVideoRenderer();
  bool _ready = false;

  // Track what is currently bound so we only re-attach on real changes.
  MediaStream? _boundLocal;
  MediaStream? _boundRemote;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    await _local.initialize();
    await _remote.initialize();
    if (mounted) setState(() => _ready = true);
  }

  @override
  void dispose() {
    _local.dispose();
    _remote.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(callChangesProvider);
    final calls = ref.watch(callServiceProvider);

    // Bind renderers via post-frame callback so the RTCVideoView has time to
    // register its native view before we hand it a stream. Doing it in the
    // same frame as the widget creation causes a race on some Android devices
    // where the native surface is not yet ready.
    if (_ready) {
      final local = calls.localStream;
      final remote = calls.remoteStream;
      if (local != null && local != _boundLocal) {
        _boundLocal = local;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) _local.srcObject = local;
        });
      }
      if (remote != null && remote != _boundRemote) {
        _boundRemote = remote;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) _remote.srcObject = remote;
        });
      }
      if (local == null && _boundLocal != null) {
        _boundLocal = null;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) _local.srcObject = null;
        });
      }
      if (remote == null && _boundRemote != null) {
        _boundRemote = null;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) _remote.srcObject = null;
        });
      }
    }

    final peer = calls.peer;
    if (peer == null) return const SizedBox.shrink();

    final video = calls.isVideo;
    final connected = calls.state == CallState.connected;
    final isConnecting = calls.state == CallState.connecting;

    // Show remote video as soon as it's available (connecting OR connected).
    // This gives the user faster visual feedback that the call is working.
    final hasRemoteVideo = video && _ready && calls.remoteStream != null;

    return Scaffold(
      backgroundColor: const Color(0xFF101014),
      body: Stack(
        children: [
          // Remote video — show as soon as the remote stream arrives
          // (connecting or connected). If not yet available, show waiting screen.
          if (hasRemoteVideo)
            Positioned.fill(
              child: RTCVideoView(
                _remote,
                objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover,
              ),
            )
          else
            Positioned.fill(
              child: _Waiting(
                peer: peer,
                state: calls.state,
                isVideo: video,
              ),
            ),

          // Your own camera — small PIP in the corner, shown as soon as the
          // local stream is available (not just when connected). This matches
          // the web: you see yourself immediately while the other person's
          // camera is still starting.
          if (video && _ready && calls.localStream != null)
            Positioned(
              right: 16,
              top: MediaQuery.of(context).padding.top + 16,
              width: 104,
              height: 150,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: Container(
                  decoration: BoxDecoration(
                    color: Colors.black26,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: RTCVideoView(
                    _local,
                    mirror: true,
                    objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover,
                  ),
                ),
              ),
            ),

          // Caller name — top of screen, always visible during video.
          if (video && (connected || isConnecting))
            Positioned(
              left: 0,
              right: 0,
              top: MediaQuery.of(context).padding.top + 20,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Column(
                  children: [
                    Text(
                      peer.name,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 18,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    if (isConnecting)
                      const Padding(
                        padding: EdgeInsets.only(top: 4),
                        child: Text(
                          'Connecting…',
                          style: TextStyle(color: Colors.white60, fontSize: 13),
                        ),
                      ),
                  ],
                ),
              ),
            ),

          // Call timer — shown only when connected.
          if (connected)
            Positioned(
              left: 0,
              right: 0,
              top: MediaQuery.of(context).padding.top + (video ? 60 : 20),
              child: const Center(
                child: _CallTimer(),
              ),
            ),

          // Controls at the bottom.
          Positioned(
            left: 0,
            right: 0,
            bottom: MediaQuery.of(context).padding.bottom + 36,
            child: _Controls(calls: calls),
          ),
        ],
      ),
    );
  }
}

class _Waiting extends StatelessWidget {
  const _Waiting({required this.peer, required this.state, this.isVideo = false});

  final CallPeer peer;
  final CallState state;
  final bool isVideo;

  @override
  Widget build(BuildContext context) {
    final label = switch (state) {
      CallState.outgoing => 'Calling…',
      CallState.incoming => 'Incoming call',
      CallState.connecting => 'Connecting…',
      CallState.connected => 'Connected',
      CallState.idle => '',
    };

    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        // Pulsing ring around the avatar for outgoing/incoming states.
        if (state == CallState.outgoing || state == CallState.incoming)
          _PulsingAvatar(name: peer.name)
        else
          CircleAvatar(
            radius: 56,
            backgroundColor: Colors.white12,
            child: Text(
              peer.name.characters.first.toUpperCase(),
              style: const TextStyle(
                color: Colors.white,
                fontSize: 40,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        const SizedBox(height: 22),
        Text(
          peer.name,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 24,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 8),
        Text(label, style: const TextStyle(color: Colors.white60, fontSize: 15)),
        if (state == CallState.outgoing) ...[
          const SizedBox(height: 10),
          Icon(
            isVideo ? Icons.videocam_rounded : Icons.call_rounded,
            color: Colors.white38,
            size: 22,
          ),
        ],
      ],
    );
  }
}

/// Avatar with expanding pulse rings to indicate ringing.
class _PulsingAvatar extends StatefulWidget {
  const _PulsingAvatar({required this.name});
  final String name;

  @override
  State<_PulsingAvatar> createState() => _PulsingAvatarState();
}

class _PulsingAvatarState extends State<_PulsingAvatar>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late final Animation<double> _scale;
  late final Animation<double> _opacity;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    );
    _scale = Tween<double>(begin: 1.0, end: 1.5).animate(
      CurvedAnimation(parent: _ctrl, curve: Curves.easeOut),
    );
    _opacity = Tween<double>(begin: 0.6, end: 0.0).animate(
      CurvedAnimation(parent: _ctrl, curve: Curves.easeOut),
    );
    _ctrl.repeat();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 130,
      height: 130,
      child: Stack(
        alignment: Alignment.center,
        children: [
          // Pulse ring
          AnimatedBuilder(
            animation: _ctrl,
            builder: (context, child) {
              return Transform.scale(
                scale: _scale.value,
                child: Container(
                  width: 112,
                  height: 112,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: Colors.greenAccent.withValues(alpha: _opacity.value),
                      width: 2,
                    ),
                  ),
                ),
              );
            },
          ),
          // Avatar
          CircleAvatar(
            radius: 56,
            backgroundColor: Colors.white12,
            child: Text(
              widget.name.characters.first.toUpperCase(),
              style: const TextStyle(
                color: Colors.white,
                fontSize: 40,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Live call duration timer.
class _CallTimer extends StatefulWidget {
  const _CallTimer();

  @override
  State<_CallTimer> createState() => _CallTimerState();
}

class _CallTimerState extends State<_CallTimer> {
  Timer? _ticker;
  int _seconds = 0;

  @override
  void initState() {
    super.initState();
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() => _seconds++);
    });
  }

  @override
  void dispose() {
    _ticker?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final m = (_seconds ~/ 60).toString().padLeft(2, '0');
    final s = (_seconds % 60).toString().padLeft(2, '0');
    return Text(
      '$m:$s',
      style: const TextStyle(
        color: Colors.white70,
        fontSize: 14,
        fontWeight: FontWeight.w500,
        fontFeatures: [FontFeature.tabularFigures()],
      ),
    );
  }
}

class _Controls extends ConsumerWidget {
  const _Controls({required this.calls});
  final CallService calls;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // --- Incoming: Decline + Answer ---
    if (calls.state == CallState.incoming) {
      return Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          _RoundButton(
            icon: Icons.call_end_rounded,
            colour: Colors.red,
            label: 'Decline',
            onTap: calls.decline,
          ),
          _RoundButton(
            icon: calls.isVideo ? Icons.videocam_rounded : Icons.call_rounded,
            colour: Colors.green,
            label: 'Answer',
            onTap: () => calls.accept(),
          ),
        ],
      );
    }

    // --- Outgoing: single Cancel button (like the web "end call" while ringing) ---
    if (calls.state == CallState.outgoing) {
      return Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          _RoundButton(
            icon: Icons.call_end_rounded,
            colour: Colors.red,
            label: 'Cancel',
            onTap: () => calls.hangUp(),
          ),
        ],
      );
    }

    // --- Connecting: End button only (media is opening, controls not useful yet) ---
    if (calls.state == CallState.connecting) {
      return Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          _RoundButton(
            icon: Icons.call_end_rounded,
            colour: Colors.red,
            label: 'End',
            onTap: () => calls.hangUp(),
          ),
        ],
      );
    }

    // --- Connected: full in-call controls ---
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: [
        _RoundButton(
          icon: calls.isMuted ? Icons.mic_off_rounded : Icons.mic_rounded,
          colour: calls.isMuted ? Colors.white24 : Colors.white10,
          label: calls.isMuted ? 'Muted' : 'Mute',
          onTap: calls.toggleMute,
        ),
        if (calls.isVideo)
          _RoundButton(
            icon: Icons.cameraswitch_rounded,
            colour: Colors.white10,
            label: 'Flip',
            onTap: calls.switchCamera,
          )
        else
          _RoundButton(
            icon: calls.isSpeakerOn
                ? Icons.volume_up_rounded
                : Icons.volume_down_rounded,
            colour: calls.isSpeakerOn ? Colors.white24 : Colors.white10,
            label: 'Speaker',
            onTap: calls.toggleSpeaker,
          ),
        _RoundButton(
          icon: Icons.call_end_rounded,
          colour: Colors.red,
          label: 'End',
          onTap: () => calls.hangUp(),
        ),
      ],
    );
  }
}

class _RoundButton extends StatelessWidget {
  const _RoundButton({
    required this.icon,
    required this.colour,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final Color colour;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Material(
          color: colour,
          shape: const CircleBorder(),
          child: InkWell(
            customBorder: const CircleBorder(),
            onTap: onTap,
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: Icon(icon, color: Colors.white, size: 26),
            ),
          ),
        ),
        const SizedBox(height: 8),
        Text(label, style: const TextStyle(color: Colors.white70, fontSize: 12)),
      ],
    );
  }
}
