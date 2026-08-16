import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hr_portal_mobile/features/more/complaints_screen.dart';
import 'package:hr_portal_mobile/models/complaint.dart';
import 'package:hr_portal_mobile/models/directory_person.dart';

void main() {
  group('DirectoryPerson', () {
    test('reads a full row', () {
      final p = DirectoryPerson.fromJson({
        'id': 4,
        'name': 'Priya Raman',
        'employeeCode': 'EMP0004',
        'email': 'priya@example.com',
        'designationTitle': 'Engineering',
        'roles': ['IT_EMP'],
      });
      expect(p.name, 'Priya Raman');
      expect(p.team, 'Engineering');
      expect(p.initials, 'PR');
      expect(p.isActive, isTrue);
    });

    test('a row with no name falls back instead of crashing the list', () {
      // The exact bug that emptied the web Users table: name.charAt(0) on null.
      final p = DirectoryPerson.fromJson({'id': 1, 'username': 'jdoe'});
      expect(p.name, 'jdoe');
      expect(p.initials, 'J');
    });

    test('a row with nothing at all still renders', () {
      final p = DirectoryPerson.fromJson({});
      expect(p.name, 'Unnamed');
      expect(p.initials, 'U');
      expect(p.team, 'No team');
    });

    test('a blank designation is "No team", not an empty heading', () {
      final p = DirectoryPerson.fromJson({'id': 2, 'name': 'A', 'designationTitle': '  '});
      expect(p.team, 'No team');
    });

    test('a single-word name gives one initial', () {
      final p = DirectoryPerson.fromJson({'id': 3, 'name': 'Madonna'});
      expect(p.initials, 'M');
    });

    test('offboarded is not active', () {
      final p = DirectoryPerson.fromJson({'id': 5, 'name': 'X', 'profileStatus': 'OFFBOARDED'});
      expect(p.isActive, isFalse);
    });
  });

  group('TeamAttendanceRow', () {
    test('reads a punched-in day', () {
      final r = TeamAttendanceRow.fromJson({
        'userId': 9,
        'employeeName': 'Arun',
        'punchInAt': '2026-08-14T09:12:00',
        'punchOutAt': '2026-08-14T18:03:00',
        'workedMinutes': 531,
        'lateMinutes': 12,
      });
      expect(r.punchedIn, isTrue);
      expect(r.isLate, isTrue);
      expect(r.workedLabel, '8h 51m');
    });

    test('no punch reads as absent, with no worked time invented', () {
      final r = TeamAttendanceRow.fromJson({'userId': 9, 'employeeName': 'Arun'});
      expect(r.punchedIn, isFalse);
      expect(r.isLate, isFalse);
      expect(r.workedLabel, '—');
    });

    test('minutes under an hour still pad correctly', () {
      final r = TeamAttendanceRow.fromJson({'userId': 1, 'workedMinutes': 65});
      expect(r.workedLabel, '1h 05m');
    });
  });

  group('Complaint', () {
    test('reads a resolved complaint with a reply', () {
      final c = Complaint.fromJson({
        'id': 3,
        'referenceCode': 'CMP-0003',
        'subject': 'Laptop is failing',
        'status': 'RESOLVED',
        'kind': 'NEED',
        'priority': 'HIGH',
        'hrResponse': 'Replacement ordered.',
        'handledByName': 'Divya',
      });
      expect(c.isClosed, isTrue);
      expect(c.hrResponse, 'Replacement ordered.');
    });

    test('missing status and kind default rather than render as nothing', () {
      // Every branch that reads these expects one of the known values; an empty
      // string falls through all of them and shows a row with no state at all.
      final c = Complaint.fromJson({'id': 1, 'subject': 'X'});
      expect(c.status, 'OPEN');
      expect(c.kind, 'COMPLAINT');
      expect(c.priority, 'MEDIUM');
      expect(c.isClosed, isFalse);
    });

    test('an open or in-review complaint is not closed', () {
      expect(Complaint.fromJson({'status': 'IN_REVIEW'}).isClosed, isFalse);
      expect(Complaint.fromJson({'status': 'REJECTED'}).isClosed, isTrue);
    });

    test('blank strings are absent, so no empty reply box is drawn', () {
      final c = Complaint.fromJson({'id': 1, 'subject': 'X', 'hrResponse': '   '});
      expect(c.hrResponse, isNull);
    });
  });

  group('RaiseComplaintSheet', () {
    Future<FormState> pump(WidgetTester tester) async {
      await tester.pumpWidget(
        ProviderScope(
          /*
           * The recipients list is stubbed, not fetched.
           *
           * Left alone, building this sheet calls the real endpoint: the test
           * then fails at teardown with a pending timer, and — worse — a unit
           * test run on a laptop with no network would fail for a reason that
           * has nothing to do with the form it is testing.
           *
           * Empty is also the case worth pinning: the "address to" dropdown is
           * hidden when nobody is returned, and the form must still be
           * submittable without it.
           */
          overrides: [
            complaintRecipientsProvider
                .overrideWith((ref) async => <ComplaintRecipient>[]),
          ],
          child: const MaterialApp(home: Scaffold(body: RaiseComplaintSheet())),
        ),
      );
      await tester.pumpAndSettle();
      return tester.state<FormState>(find.byType(Form));
    }

    testWidgets('will not submit empty', (tester) async {
      final form = await pump(tester);
      expect(form.validate(), isFalse);
      await tester.pumpAndSettle();
      expect(find.text('A short subject, please'), findsOneWidget);
      expect(find.text('Please describe it'), findsOneWidget);
    });

    testWidgets('a subject alone is not enough', (tester) async {
      final form = await pump(tester);
      await tester.enterText(find.widgetWithText(TextFormField, 'Subject'), 'Broken chair');
      expect(form.validate(), isFalse);
      await tester.pumpAndSettle();
      expect(find.text('Please describe it'), findsOneWidget);
    });

    testWidgets('accepts a subject and a description', (tester) async {
      final form = await pump(tester);
      await tester.enterText(find.widgetWithText(TextFormField, 'Subject'), 'Broken chair');
      await tester.enterText(
          find.widgetWithText(TextFormField, 'What happened'), 'The back is cracked.');
      expect(form.validate(), isTrue);
    });

    testWidgets('whitespace is not a description', (tester) async {
      final form = await pump(tester);
      await tester.enterText(find.widgetWithText(TextFormField, 'Subject'), '   ');
      await tester.enterText(find.widgetWithText(TextFormField, 'What happened'), '   ');
      expect(form.validate(), isFalse);
    });
  });
}
