import 'package:flutter_test/flutter_test.dart';
import 'package:hr_portal_mobile/core/config/app_config.dart';
import 'package:hr_portal_mobile/repositories/face_repository.dart';

void main() {
  group('FaceVerdict', () {
    test('reads a match', () {
      final v = FaceVerdict.fromJson({
        'match': true,
        'score': 0.412,
        'message': 'ok',
      });
      expect(v.match, isTrue);
      expect(v.score, 0.412);
      // Kept whole so it can travel with the punch — a dispute months later
      // has something to read rather than a bare yes.
      expect(v.raw, isNotNull);
    });

    test('anything that is not exactly true is not a match', () {
      // The server refuses a face punch unless the caller says verified=true,
      // so a loose reading here would assert a check that did not pass.
      for (final value in [false, null, 'true', 1, 'yes']) {
        expect(
          FaceVerdict.fromJson({'match': value}).match,
          isFalse,
          reason: '$value must not count as a match',
        );
      }
    });

    test('a score sent as a string still parses', () {
      expect(FaceVerdict.fromJson({'match': true, 'score': '0.38'}).score, 0.38);
    });

    test('a missing score is null, not zero', () {
      // Zero is a real score and a very good one — distance, not confidence.
      // Defaulting to it would report a perfect match for a service that said
      // nothing at all.
      expect(FaceVerdict.fromJson({'match': true}).score, isNull);
    });
  });

  group('face service address', () {
    test('is derived from the API address, not written out twice', () {
      // Two hard-coded addresses is how the app came to be aimed at a Render
      // server the portal had stopped running on. This one cannot drift.
      expect(AppConfig.faceServiceBaseUrl, 'https://pixoushrportal.pixous.info/analytics');
      expect(
        AppConfig.faceServiceBaseUrl.startsWith(
          AppConfig.apiBaseUrl.replaceAll('/api', ''),
        ),
        isTrue,
      );
    });

    test('sits beside /api rather than under it', () {
      // The face service is a different application behind the same nginx.
      expect(AppConfig.faceServiceBaseUrl, isNot(contains('/api/')));
      expect(AppConfig.faceServiceBaseUrl, endsWith('/analytics'));
    });
  });
}
