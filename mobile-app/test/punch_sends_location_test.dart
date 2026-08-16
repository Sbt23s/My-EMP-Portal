import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hr_portal_mobile/core/location/punch_location.dart';
import 'package:hr_portal_mobile/core/network/api_client.dart';
import 'package:hr_portal_mobile/core/storage/token_store.dart';
import 'package:hr_portal_mobile/features/attendance/attendance_screen.dart';
import 'package:hr_portal_mobile/models/attendance.dart';
import 'package:hr_portal_mobile/providers/app_providers.dart';
import 'package:hr_portal_mobile/repositories/work_repository.dart';

/// The wiring, not the pieces.
///
/// The pieces were already fine: the repository accepted latitude and longitude
/// from the first day, and the service that reads them is tested next door. The
/// bug was that the screen called `punchIn()` with neither — so the app sent no
/// coordinates at all, and the server filed every punch from a phone as a
/// geofence exception.
///
/// A test of either piece alone would have passed throughout. This is the one
/// that would have failed.

class _CapturingRepository extends WorkRepository {
  _CapturingRepository() : super(ApiClient(tokens: TokenStore()));

  double? lat;
  double? lng;
  int calls = 0;

  @override
  Future<AttendanceDay> punchIn({double? latitude, double? longitude}) async {
    calls++;
    lat = latitude;
    lng = longitude;
    return AttendanceDay.fromJson(const {'id': 1, 'workDate': '2026-08-14'});
  }

  @override
  Future<AttendanceDay> punchOut({double? latitude, double? longitude}) async {
    calls++;
    lat = latitude;
    lng = longitude;
    return AttendanceDay.fromJson(const {'id': 1, 'workDate': '2026-08-14'});
  }

  @override
  Future<AttendanceDay?> today() async => null;

  @override
  Future<List<AttendanceDay>> attendanceBetween(DateTime from, DateTime to) async => [];
}

class _FixedLocation extends PunchLocationService {
  const _FixedLocation(this.result);
  final PunchLocation result;

  @override
  Future<PunchLocation> current() async => result;
}

Future<void> _pumpAndPunch(
  WidgetTester tester, {
  required _CapturingRepository repo,
  required PunchLocation location,
}) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        workRepositoryProvider.overrideWithValue(repo),
        punchLocationServiceProvider.overrideWithValue(_FixedLocation(location)),
      ],
      child: const MaterialApp(home: AttendanceScreen()),
    ),
  );
  await tester.pumpAndSettle();

  final button = find.text('Punch in');
  expect(button, findsOneWidget, reason: 'the punch button should be on screen');
  await tester.tap(button);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('a punch carries the coordinates to the server', (tester) async {
    final repo = _CapturingRepository();
    await _pumpAndPunch(
      tester,
      repo: repo,
      location: const PunchLocation(
        outcome: LocationOutcome.fixed,
        latitude: 13.0827,
        longitude: 80.2707,
      ),
    );

    expect(repo.calls, 1);
    expect(repo.lat, 13.0827);
    expect(repo.lng, 80.2707);
  });

  testWidgets('no GPS still punches, and says why it has no location',
      (tester) async {
    // The rule: a punch is never blocked. Somebody at the gate at nine o'clock
    // marks their attendance whether or not the GPS cooperates.
    final repo = _CapturingRepository();
    await _pumpAndPunch(
      tester,
      repo: repo,
      location: const PunchLocation(outcome: LocationOutcome.serviceOff),
    );

    expect(repo.calls, 1, reason: 'the punch must go through anyway');
    expect(repo.lat, isNull);
    expect(repo.lng, isNull);

    // Confirmation first, caveat second — leading with the problem reads as a
    // failed punch and sends somebody to punch again.
    expect(find.textContaining('Punched in'), findsOneWidget);
    expect(find.textContaining('Turn on location'), findsOneWidget);
  });

  testWidgets('a successful punch says nothing about location', (tester) async {
    final repo = _CapturingRepository();
    await _pumpAndPunch(
      tester,
      repo: repo,
      location: const PunchLocation(
        outcome: LocationOutcome.fixed,
        latitude: 13.0,
        longitude: 80.0,
      ),
    );

    expect(find.text('Punched in'), findsOneWidget);
    expect(find.textContaining('saved without'), findsNothing);
  });
}
