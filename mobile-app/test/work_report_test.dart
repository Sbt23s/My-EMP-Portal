import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hr_portal_mobile/features/more/work_reports_screen.dart';
import 'package:hr_portal_mobile/models/work_report.dart';

void main() {
  group('WorkReport.fromJson', () {
    test('reads a full row', () {
      final r = WorkReport.fromJson({
        'id': 7,
        'workDate': '2026-08-14',
        'projectName': 'HR Portal',
        'workHours': 7.5,
        'taskDescription': 'Branding',
        'employeeName': 'Priya',
        'attachments': 'a.png,b.png',
      });
      expect(r.id, 7);
      expect(r.workDate.day, 14);
      expect(r.workHours, 7.5);
      expect(r.attachmentCount, 2);
    });

    test('hours arrive as a string from the BigDecimal column', () {
      // Jackson serialises BigDecimal as a bare number, but a row that has been
      // through a string-typed path anywhere would come back quoted. Parsing
      // only the num case would silently read every such row as zero hours.
      final r = WorkReport.fromJson({'id': 1, 'workHours': '8.00'});
      expect(r.workHours, 8);
    });

    test('a row with nothing in it renders instead of throwing', () {
      // One bad row must not take the whole list down.
      final r = WorkReport.fromJson({});
      expect(r.projectName, 'Untitled');
      expect(r.workHours, 0);
      expect(r.taskDescription, isNull);
      expect(r.attachmentCount, 0);
    });

    test('blank strings count as absent, not as content', () {
      final r = WorkReport.fromJson({
        'id': 2,
        'taskDescription': '   ',
        'attachments': '',
      });
      expect(r.taskDescription, isNull);
      expect(r.attachmentCount, 0);
    });

    test('a trailing comma does not invent an attachment', () {
      final r = WorkReport.fromJson({'id': 3, 'attachments': 'only.png,'});
      expect(r.attachmentCount, 1);
    });
  });

  group('SubmitWorkReportSheet', () {
    Future<FormState> pump(WidgetTester tester) async {
      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(home: Scaffold(body: SubmitWorkReportSheet())),
        ),
      );
      await tester.pumpAndSettle();
      return tester.state<FormState>(find.byType(Form));
    }

    testWidgets('will not save without a project', (tester) async {
      // Validated rather than submitted: tapping Save reaches the network, and
      // a test that needs a server is a test that fails for the wrong reason.
      final form = await pump(tester);
      expect(form.validate(), isFalse);
      await tester.pumpAndSettle();
      expect(find.text('Which project was this for?'), findsOneWidget);
    });

    testWidgets('rejects hours that are not a number', (tester) async {
      final form = await pump(tester);
      await tester.enterText(find.widgetWithText(TextFormField, 'Project'), 'HR Portal');
      await tester.enterText(find.widgetWithText(TextFormField, 'Hours'), 'seven');
      expect(form.validate(), isFalse);
      await tester.pumpAndSettle();
      expect(find.text('Enter a number, like 7.5'), findsOneWidget);
    });

    testWidgets('rejects zero and more than a day', (tester) async {
      final form = await pump(tester);
      await tester.enterText(find.widgetWithText(TextFormField, 'Project'), 'HR Portal');

      await tester.enterText(find.widgetWithText(TextFormField, 'Hours'), '0');
      expect(form.validate(), isFalse);
      await tester.pumpAndSettle();
      expect(find.text('Must be more than zero'), findsOneWidget);

      await tester.enterText(find.widgetWithText(TextFormField, 'Hours'), '25');
      expect(form.validate(), isFalse);
      await tester.pumpAndSettle();
      expect(find.text("That's more than a day"), findsOneWidget);
    });

    testWidgets('accepts a valid entry with no description', (tester) async {
      // The description is optional on the server, so it must be optional here.
      final form = await pump(tester);
      await tester.enterText(find.widgetWithText(TextFormField, 'Project'), 'HR Portal');
      await tester.enterText(find.widgetWithText(TextFormField, 'Hours'), '7.5');
      expect(form.validate(), isTrue);
    });
  });
}
