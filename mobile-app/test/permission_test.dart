import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hr_portal_mobile/features/leave/permissions_screen.dart';
import 'package:hr_portal_mobile/models/leave.dart';

void main() {
  group('PermissionRequestItem.fromJson', () {
    test('reads a decided request', () {
      final p = PermissionRequestItem.fromJson({
        'id': 5,
        'requestDate': '2026-08-15',
        'fromTime': '14:30',
        'toTime': '16:00',
        'hours': 1.5,
        'status': 'APPROVED',
        'decidedByName': 'Priya',
        'decisionComment': 'Fine',
      });
      expect(p.isApproved, isTrue);
      expect(p.isPending, isFalse);
      expect(p.hours, 1.5);
      expect(p.fromTime, '14:30');
    });

    test('a row with no status reads as pending, not approved', () {
      // The safe direction. Defaulting to approved would show somebody time off
      // that nobody has agreed to.
      final p = PermissionRequestItem.fromJson({'id': 1});
      expect(p.isPending, isTrue);
      expect(p.isApproved, isFalse);
    });

    test('an empty payload renders rather than throwing', () {
      final p = PermissionRequestItem.fromJson({});
      expect(p.fromTime, '--:--');
      expect(p.reason, isNull);
    });

    test('status is matched regardless of case', () {
      expect(
        PermissionRequestItem.fromJson({'id': 1, 'status': 'approved'}).isApproved,
        isTrue,
      );
    });
  });

  group('RequestPermissionSheet', () {
    Future<FormState> pump(WidgetTester tester) async {
      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(home: Scaffold(body: RequestPermissionSheet())),
        ),
      );
      await tester.pumpAndSettle();
      return tester.state<FormState>(find.byType(Form));
    }

    testWidgets('will not send without a reason', (tester) async {
      final form = await pump(tester);
      expect(form.validate(), isFalse);
      await tester.pumpAndSettle();
      expect(
        find.text('Say why — whoever approves it will ask otherwise'),
        findsOneWidget,
      );
    });

    testWidgets('accepts a reason', (tester) async {
      final form = await pump(tester);
      await tester.enterText(find.byType(TextFormField), 'Dentist');
      expect(form.validate(), isTrue);
    });

    testWidgets('the default window is shown in 24-hour form', (tester) async {
      // The server parses "14:30". TimeOfDay.format() is localised and would
      // send "2:30 PM" on a phone set to twelve-hour time, which it rejects.
      await pump(tester);
      expect(find.text('10:00'), findsOneWidget);
      expect(find.text('12:00'), findsOneWidget);
    });
  });
}
