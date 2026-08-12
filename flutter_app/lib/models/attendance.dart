/// One day's attendance, as `AttendanceResponse` sends it.
class AttendanceDay {
  const AttendanceDay({
    required this.id,
    required this.workDate,
    this.punchInAt,
    this.punchOutAt,
    this.workedMinutes,
    this.lateMinutes,
    this.status,
    this.faceVerified,
  });

  final int id;
  final DateTime workDate;
  final DateTime? punchInAt;
  final DateTime? punchOutAt;
  final int? workedMinutes;
  final int? lateMinutes;
  final String? status;
  final bool? faceVerified;

  bool get isPunchedIn => punchInAt != null && punchOutAt == null;
  bool get isComplete => punchInAt != null && punchOutAt != null;

  /// "7h 45m", or "—" when nothing has been recorded.
  String get workedLabel {
    final m = workedMinutes;
    if (m == null || m <= 0) return '—';
    final h = m ~/ 60;
    final mins = m % 60;
    return h > 0 ? '${h}h ${mins}m' : '${mins}m';
  }

  factory AttendanceDay.fromJson(Map<String, dynamic> json) => AttendanceDay(
    id: (json['id'] as num?)?.toInt() ?? 0,
    workDate: _date(json['workDate']) ?? DateTime.now(),
    punchInAt: _date(json['punchInAt']),
    punchOutAt: _date(json['punchOutAt']),
    workedMinutes: (json['workedMinutes'] as num?)?.toInt(),
    lateMinutes: (json['lateMinutes'] as num?)?.toInt(),
    status: json['status']?.toString(),
    faceVerified: json['faceVerified'] as bool?,
  );

  /// The backend sends ISO strings; a malformed one should not crash a list.
  static DateTime? _date(dynamic v) {
    if (v == null) return null;
    return DateTime.tryParse(v.toString());
  }
}
