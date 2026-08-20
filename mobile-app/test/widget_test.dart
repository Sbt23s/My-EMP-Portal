import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hr_portal_mobile/features/auth/login_screen.dart';
import 'package:hr_portal_mobile/features/more/submit_claim_sheet.dart';
import 'package:hr_portal_mobile/models/auth_user.dart';
import 'package:hr_portal_mobile/models/work_items.dart';

void main() {
  group('AuthUser', () {
    test('parses a login payload', () {
      final user = AuthUser.fromJson(const {
        'id': 6,
        'name': 'System Admin',
        'username': 'admin',
        'employeeCode': 'ADM0001',
        'roles': ['SUPER_ADMIN'],
        'permissions': ['USER_MANAGE', 'REPORT_VIEW'],
      });

      expect(user.id, 6);
      expect(user.username, 'admin');
      expect(user.hasRole('SUPER_ADMIN'), isTrue);
      expect(user.can('USER_MANAGE'), isTrue);
      expect(user.can('PAYROLL_RUN'), isFalse);
    });

    test('survives nulls and missing fields', () {
      // The server marks most of these optional; a null must not throw.
      final user = AuthUser.fromJson(const {
        'id': 1,
        'name': null,
        'roles': null,
      });

      expect(user.name, '');
      expect(user.roles, isEmpty);
      expect(user.permissions, isEmpty);
      expect(user.initials, '?');
    });

    test('builds initials from first and last name', () {
      expect(
        AuthUser.fromJson(const {'id': 1, 'name': 'Arun Kumar'}).initials,
        'AK',
      );
      expect(AuthUser.fromJson(const {'id': 1, 'name': 'Priya'}).initials, 'P');
    });
  });

  group('LoginScreen', () {
    testWidgets('refuses to submit an empty form', (tester) async {
      await tester.pumpWidget(
        const ProviderScope(child: MaterialApp(home: LoginScreen())),
      );

      await tester.tap(find.widgetWithText(FilledButton, 'Sign in'));
      await tester.pump();

      // Validation stops it before any request is attempted.
      expect(find.text('Enter your username'), findsOneWidget);
      expect(find.text('Enter your password'), findsOneWidget);
    });

    testWidgets('hides the password until asked', (tester) async {
      await tester.pumpWidget(
        const ProviderScope(child: MaterialApp(home: LoginScreen())),
      );

      final field = tester.widget<TextField>(
        find.descendant(
          of: find.ancestor(
            of: find.text('Password'),
            matching: find.byType(TextFormField),
          ),
          matching: find.byType(TextField),
        ),
      );
      expect(field.obscureText, isTrue);
    });
  });

  group('CalendarEvent', () {
    test('reads a meeting', () {
      final e = CalendarEvent.fromJson(const {
        'id': 12,
        'type': 'MEETING',
        'title': 'Sprint review',
        'date': '2026-08-14',
        'startTime': '10:00',
        'endTime': '11:00',
        'location': 'Room 2',
        'audienceTeam': 'Engineering',
      });

      expect(e.id, 12);
      expect(e.type, 'MEETING');
      expect(e.date, DateTime(2026, 8, 14));
      expect(e.audienceTeam, 'Engineering');
    });

    test('reads a birthday, which has no row of its own', () {
      // The server derives these from employee records, so id and audience
      // are both null. Neither may throw.
      final e = CalendarEvent.fromJson(const {
        'id': null,
        'type': 'BIRTHDAY',
        'title': 'Priya',
        'date': '2026-08-20',
        'audienceTeam': null,
      });

      expect(e.id, isNull);
      expect(e.audienceTeam, isNull);
      expect(e.title, 'Priya');
    });

    test('survives an entry with nothing but a type', () {
      final e = CalendarEvent.fromJson(const {'type': 'OTHER'});

      expect(e.title, '');
      expect(e.date, isNull);
      expect(e.endDate, isNull);
    });
  });

  group('SubmitClaimSheet', () {
    Future<void> pumpSheet(WidgetTester tester) async {
      await tester.binding.setSurfaceSize(const Size(800, 1200));
      // Awaited: pumpWidget is a guarded API, and calling pumpAndSettle before
      // it finished threw "Guarded function conflict" -- which failed all four
      // claim-sheet tests for a reason that had nothing to do with the sheet.
      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(home: Scaffold(body: SingleChildScrollView(child: SubmitClaimSheet()))),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('will not submit without a destination', (tester) async {
      await pumpSheet(tester);

      await tester.ensureVisible(find.widgetWithText(FilledButton, 'Submit claim'));
      await tester.tap(find.widgetWithText(FilledButton, 'Submit claim'));
      await tester.pump();

      // Validation stops it before any request is attempted, which matters
      // here because the repository would otherwise be reached with no
      // configured server and throw an unrelated error.
      expect(
        find.text('Where was this expense?'),
        findsOneWidget,
      );
    });

    testWidgets('rejects a non-numeric starting km', (tester) async {
      await pumpSheet(tester);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Location / city *'),
        'Coimbatore',
      );
      await tester.ensureVisible(find.widgetWithText(TextFormField, 'Starting KM'));
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Starting KM'),
        'abc',
      );
      await tester.ensureVisible(find.widgetWithText(FilledButton, 'Submit claim'));
      await tester.tap(find.widgetWithText(FilledButton, 'Submit claim'));
      await tester.pump();

      expect(find.text('Numbers only'), findsOneWidget);
    });

    testWidgets('rejects a zero starting km', (tester) async {
      await pumpSheet(tester);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Location / city *'),
        'Coimbatore',
      );
      await tester.ensureVisible(find.widgetWithText(TextFormField, 'Starting KM'));
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Starting KM'),
        '0',
      );
      await tester.ensureVisible(find.widgetWithText(FilledButton, 'Submit claim'));
      await tester.tap(find.widgetWithText(FilledButton, 'Submit claim'));
      await tester.pump();

      expect(find.text('Must be more than zero'), findsOneWidget);
    });

    testWidgets('accepts a blank amount, since it is optional', (tester) async {
      await pumpSheet(tester);

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Location / city *'),
        'Coimbatore',
      );
      // Validating the form directly rather than tapping the button. A tap
      // would pass validation and then go on to the network, which no test
      // here has a server for — the failure would be about the missing server,
      // not about the field being optional.
      final form = tester.state<FormState>(find.byType(Form));
      expect(form.validate(), isTrue);
      expect(find.text('Numbers only'), findsNothing);
    });
  });
}
