/// An upcoming birthday or work anniversary, for the team celebrations widget.
///
/// Mirrors `Celebration` in the dashboard module. `daysUntil == 0` means today.
class Celebration {
  const Celebration({
    required this.userId,
    required this.name,
    this.employeeCode,
    this.team,
    this.photoPath,
    this.type,
    this.date,
    this.daysUntil = -1,
    this.years,
  });

  final int userId;
  final String name;
  final String? employeeCode;
  final String? team;
  final String? photoPath;

  /// BIRTHDAY | ANNIVERSARY
  final String? type;
  final DateTime? date;
  final int daysUntil;
  final int? years;

  bool get isBirthday => type?.toUpperCase() == 'BIRTHDAY';
  bool get isToday => daysUntil == 0;

  static Celebration fromJson(Map<String, dynamic> json) => Celebration(
        userId: (json['userId'] as num?)?.toInt() ?? 0,
        name: json['name']?.toString() ?? 'Someone',
        employeeCode: _s(json['employeeCode']),
        team: _s(json['team']),
        photoPath: _s(json['photoPath']),
        type: _s(json['type']),
        date: json['date'] == null ? null : DateTime.tryParse('${json['date']}'),
        daysUntil: (json['daysUntil'] as num?)?.toInt() ?? -1,
        years: (json['years'] as num?)?.toInt(),
      );

  static String? _s(dynamic v) {
    final t = v?.toString().trim();
    return (t == null || t.isEmpty) ? null : t;
  }
}
