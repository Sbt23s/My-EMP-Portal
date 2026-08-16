import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:hr_portal_mobile/core/calls/call_service.dart';

/// The call protocol must be exactly what the web client speaks, or a phone
/// and a browser cannot call each other.
///
/// The two functions under test are the ones that disagreed. The web sends the
/// whole `RTCSessionDescription` nested under `data.sdp` (`{sdp, type}`) and
/// names the video flag `isVideo`; this app used to send flat strings and call
/// it `video`. A browser calling a phone then hung at "calling" forever, and a
/// video call arrived as voice. These tests pin both, so a future "cleanup"
/// cannot quietly reintroduce the mismatch.
void main() {
  group('CallService.sessionDescription', () {
    test('reads the web shape — whole description nested under data.sdp', () {
      final desc = CallService.sessionDescription({
        'sdp': {'sdp': 'v=0\r\n...', 'type': 'offer'},
        'isVideo': true,
      });
      expect(desc, isNotNull);
      expect(desc!.sdp, 'v=0\r\n...');
      expect(desc.type, 'offer');
    });

    test('reads the flat shape older mobile builds sent', () {
      final desc = CallService.sessionDescription({
        'sdp': 'v=0\r\n...',
        'type': 'answer',
      });
      expect(desc, isNotNull);
      expect(desc!.sdp, 'v=0\r\n...');
      expect(desc.type, 'answer');
    });

    test('returns null for a missing or empty sdp instead of throwing', () {
      expect(CallService.sessionDescription({}), isNull);
      expect(CallService.sessionDescription({'sdp': ''}), isNull);
      expect(CallService.sessionDescription({'sdp': 42}), isNull);
    });
  });

  group('CallService.readVideo', () {
    test('understands the web flag isVideo', () {
      expect(CallService.readVideo({'isVideo': true}), isTrue);
      expect(CallService.readVideo({'isVideo': false}), isFalse);
      // A string true — the STOMP frame can arrive as one.
      expect(CallService.readVideo({'isVideo': 'true'}), isTrue);
    });

    test('understands the older mobile flag video', () {
      expect(CallService.readVideo({'video': true}), isTrue);
      expect(CallService.readVideo({'video': false}), isFalse);
    });

    test('an absent flag is a voice call, never a silent video call', () {
      expect(CallService.readVideo({}), isFalse);
    });
  });
}
