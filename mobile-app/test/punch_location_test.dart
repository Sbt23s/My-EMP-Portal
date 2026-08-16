import 'package:flutter_test/flutter_test.dart';
import 'package:hr_portal_mobile/core/location/punch_location.dart';

/// The rule these guard: **a punch is never blocked by GPS.**
///
/// Every branch has to end in a punch going through, and every branch that ends
/// without coordinates has to say something a person can act on. "Could not get
/// your location" is the failure mode being tested against — it is true in all
/// four cases and useful in none of them.
void main() {
  group('PunchLocation', () {
    test('a fix carries coordinates and says nothing', () {
      const l = PunchLocation(
        outcome: LocationOutcome.fixed,
        latitude: 13.0827,
        longitude: 80.2707,
      );
      expect(l.hasFix, isTrue);
      // Nothing to warn about. A message that appears on every single punch
      // trains people to dismiss it unread, and then the one that matters is
      // dismissed too.
      expect(l.warning, isNull);
    });

    test('every failure has no coordinates but a message', () {
      for (final outcome in LocationOutcome.values) {
        if (outcome == LocationOutcome.fixed) continue;
        final l = PunchLocation(outcome: outcome);
        expect(l.hasFix, isFalse, reason: '$outcome must carry no coordinates');
        expect(
          l.warning,
          isNotNull,
          reason: '$outcome must tell the person what happened',
        );
        // Says the punch is safe. Without it the sentence reads as a failure
        // and somebody punches again, producing a duplicate the server rejects.
        expect(
          l.warning!.toLowerCase(),
          anyOf(contains('saved'), contains('settings')),
          reason: '$outcome must not read as a failed punch',
        );
      }
    });

    test('each failure names a different action', () {
      // The whole reason this is an enum and not a bool: turning on location,
      // granting a permission, and opening system settings are three different
      // things to do, and one message cannot ask for all three.
      expect(
        const PunchLocation(outcome: LocationOutcome.serviceOff).warning,
        contains('Turn on location'),
      );
      expect(
        const PunchLocation(outcome: LocationOutcome.deniedForever).warning,
        contains('Settings'),
      );
      expect(
        const PunchLocation(outcome: LocationOutcome.timedOut).warning,
        contains('outdoors'),
      );
    });

    test('a half-read position does not count as a fix', () {
      // Latitude with no longitude is not a location, and sending one without
      // the other would have the server treat the punch as located when it
      // cannot place it.
      expect(
        const PunchLocation(outcome: LocationOutcome.fixed, latitude: 13.0).hasFix,
        isFalse,
      );
      expect(
        const PunchLocation(outcome: LocationOutcome.fixed, longitude: 80.0).hasFix,
        isFalse,
      );
    });

    test('the messages are distinct, so the reason is actually conveyed', () {
      final messages = LocationOutcome.values
          .where((o) => o != LocationOutcome.fixed)
          .map((o) => PunchLocation(outcome: o).warning)
          .toSet();
      expect(messages.length, LocationOutcome.values.length - 1);
    });
  });
}
