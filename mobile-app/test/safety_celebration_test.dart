import 'package:flutter_test/flutter_test.dart';
import 'package:hr_portal_mobile/models/celebration.dart';
import 'package:hr_portal_mobile/models/safety.dart';

/// The new parity models parse the server's shapes.
///
/// These were added with the Safety and My Team screens, and like the rest of
/// the suite they test parsing in isolation: a field renamed on the server
/// turns into a parse failure here, not a blank screen in the field.
void main() {
  group('SafetyIncident', () {
    test('parses a full row', () {
      final incident = SafetyIncident.fromJson({
        'id': 7,
        'referenceCode': 'SFT-2026-0007',
        'reportedBy': 42,
        'reportedByName': 'Priya R',
        'incidentType': 'MINOR_INJURY',
        'description': 'Slipped near the loading bay.',
        'zone': 'Loading bay',
        'anonymous': false,
        'status': 'INVESTIGATING',
        'severity': 'MEDIUM',
        'occurredAt': '2026-08-10T09:30:00',
        'resolutionNotes': 'CCTV pulled; floor mat ordered.',
      });

      expect(incident.id, 7);
      expect(incident.referenceCode, 'SFT-2026-0007');
      expect(incident.incidentType, 'MINOR_INJURY');
      expect(incident.resolved, isFalse);
      expect(incident.occurredAt, isNotNull);
    });

    test('treats RESOLVED and CLOSED as resolved', () {
      expect(
        SafetyIncident.fromJson({'id': 1, 'status': 'RESOLVED'}).resolved,
        isTrue,
      );
      expect(
        SafetyIncident.fromJson({'id': 1, 'status': 'CLOSED'}).resolved,
        isTrue,
      );
      expect(
        SafetyIncident.fromJson({'id': 1, 'status': 'OPEN'}).resolved,
        isFalse,
      );
    });

    test('keeps anonymous reports anonymous', () {
      final incident = SafetyIncident.fromJson({
        'id': 3,
        'anonymous': true,
        'reportedByName': 'Someone',
      });
      expect(incident.anonymous, isTrue);
      expect(incident.reportedByName, 'Someone');
    });
  });

  group('Celebration', () {
    test('parses a birthday', () {
      final celebration = Celebration.fromJson({
        'userId': 12,
        'name': 'Karthik',
        'employeeCode': 'pix-e012',
        'team': 'Engineering',
        'type': 'BIRTHDAY',
        'date': '2026-09-01',
        'daysUntil': 16,
        'years': 27,
      });

      expect(celebration.userId, 12);
      expect(celebration.name, 'Karthik');
      expect(celebration.isBirthday, isTrue);
      expect(celebration.daysUntil, 16);
      expect(celebration.isToday, isFalse);
    });

    test('flags today', () {
      final celebration = Celebration.fromJson({
        'userId': 5,
        'name': 'Meena',
        'type': 'ANNIVERSARY',
        'daysUntil': 0,
        'years': 3,
      });
      expect(celebration.isToday, isTrue);
      expect(celebration.isBirthday, isFalse);
    });
  });
}
