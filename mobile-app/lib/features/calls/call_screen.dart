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

    // Renderers are attached on every build rather than once: the streams
    // arrive after this screen is already up, and a renderer bound before the
    // stream exists shows black for the whole call.
    if (_ready) {
      if (_local.srcObject != calls.localStream) {
        _local.srcObject = calls.localStream;
      }
      if (_remote.srcObject != calls.remoteStream) {
        _remote.srcObject = calls.remoteStream;
      }
    }

    final peer = calls.peer;
    if (peer == null) return const SizedBox.shrink();

    final video = calls.isVideo;
    final connected = calls.state == CallState.connected;

    return Scaffold(
      backgroundColor: const Color(0xFF101014),
      body: Stack(
        children: [
          if (video && connected && _ready)
            Positioned.fill(
              child: RTCVideoView(
                _remote,
                objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover,
              ),
            )
          else
            Positioned.fill(child: _Waiting(peer: peer, state: calls.state)),

          // Your own camera, small and in the corner, only once there is
          // something to show.
          if (video && _ready && calls.localStream != null)
            Positioned(
              right: 16,
              top: MediaQuery.of(context).padding.top + 16,
              width: 104,
              height: 150,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: RTCVideoView(
                  _local,
                  mirror: true,
                  objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover,
                ),
              ),
            ),

          if (video && connected)
            Positioned(
              left: 0,
              right: 0,
              top: MediaQuery.of(context).padding.top + 20,
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
                ],
              ),
            ),

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
  const _Waiting({required this.peer, required this.state});

  final CallPeer peer;
  final CallState state;

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
      ],
    );
  }
}

class _Controls extends ConsumerWidget {
  const _Controls({required this.calls});
  final CallService calls;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Ringing: refuse or answer. Everything else: the in-call row.
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
