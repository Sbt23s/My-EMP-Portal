/// A day's work, as the portal records it.
///
/// Mirrors `WorkReportResponse` on the server. Every field except the id and the
/// date is treated as optional here even where the server marks it required —
/// a row written by an older build, or one whose project was later cleared, must
/// render rather than throw in the middle of a list.
class WorkReport {
  const WorkReport({
    required this.id,
    required this.workDate,
    required this.projectName,
    required this.workHours,
    this.taskDescription,
    this.employeeName,
    this.employeeCode,
    this.attachments,
  });

  final int id;
  final DateTime workDate;
  final String projectName;
  final double workHours;
  final String? taskDescription;

  /// Set on the team view, null on your own rows — where it would only repeat
  /// the name in the app bar.
  final String? employeeName;
  final String? employeeCode;

  /// Comma-separated paths, or null when nothing is attached.
  final String? attachments;

  int get attachmentCount {
    final raw = attachments;
    if (raw == null || raw.trim().isEmpty) return 0;
    return raw.split(',').where((p) => p.trim().isNotEmpty).length;
  }

  static WorkReport fromJson(Map<String, dynamic> json) {
    return WorkReport(
      id: (json['id'] as num?)?.toInt() ?? 0,
      // A row with no date cannot be placed on a timeline, and throwing would
      // take the whole list down. Today is wrong but visible, and a visible
      // wrong row is something somebody can report.
      workDate:
          DateTime.tryParse(json['workDate']?.toString() ?? '') ?? DateTime.now(),
      projectName: json['projectName']?.toString() ?? 'Untitled',
      workHours: double.tryParse(json['workHours']?.toString() ?? '') ?? 0,
      taskDescription: _blankToNull(json['taskDescription']),
      employeeName: _blankToNull(json['employeeName']),
      employeeCode: _blankToNull(json['employeeCode']),
      attachments: _blankToNull(json['attachments']),
    );
  }

  static String? _blankToNull(dynamic value) {
    final text = value?.toString().trim();
    return (text == null || text.isEmpty) ? null : text;
  }
}
