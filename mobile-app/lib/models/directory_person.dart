/// A colleague, as the employee directory lists them.
///
/// A trimmed `UserSummary`: the server's row also carries a plaintext password
/// (the technical-admin screens read it) and bank and document fields. None of
/// that is parsed here — a model that does not hold a secret cannot leak one to
/// a log, a crash report or a screenshot.
class DirectoryPerson {
  const DirectoryPerson({
    required this.id,
    required this.name,
    this.employeeCode,
    this.email,
    this.phone,
    this.designationTitle,
    this.photoPath,
    this.profileStatus,
    this.roles = const [],
  });

  final int id;
  final String name;
  final String? employeeCode;
  final String? email;
  final String? phone;
  final String? designationTitle;
  final String? photoPath;
  final String? profileStatus;
  final List<String> roles;

  bool get isActive => (profileStatus ?? 'ACTIVE').toUpperCase() != 'OFFBOARDED';

  /// The team as this portal means it: the designation people are grouped by.
  String get team {
    final t = designationTitle?.trim();
    return (t == null || t.isEmpty) ? 'No team' : t;
  }

  /// One or two letters for the avatar.
  String get initials {
    final parts = name.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
    if (parts.isEmpty) return '?';
    if (parts.length == 1) return parts.first.substring(0, 1).toUpperCase();
    return (parts.first.substring(0, 1) + parts.last.substring(0, 1)).toUpperCase();
  }

  static DirectoryPerson fromJson(Map<String, dynamic> json) {
    final rawRoles = json['roles'];
    return DirectoryPerson(
      id: (json['id'] as num?)?.toInt() ?? 0,
      // Falls back to the username, then to a placeholder. A row with no name
      // is a broken account, and `name.charAt(0)` on null is how a whole list
      // disappears behind one bad row.
      name: _text(json['name']) ?? _text(json['username']) ?? 'Unnamed',
      employeeCode: _text(json['employeeCode']),
      email: _text(json['email']),
      phone: _text(json['phone']),
      designationTitle: _text(json['designationTitle']),
      photoPath: _text(json['photoPath']),
      profileStatus: _text(json['profileStatus']),
      roles: rawRoles is List
          ? rawRoles.map((r) => r.toString()).where((r) => r.isNotEmpty).toList()
          : const [],
    );
  }

  static String? _text(dynamic value) {
    final t = value?.toString().trim();
    return (t == null || t.isEmpty) ? null : t;
  }
}

/// One person's attendance on one day, as the team view reports it.
class TeamAttendanceRow {
  const TeamAttendanceRow({
    required this.userId,
    this.employeeName,
    this.employeeCode,
    this.punchInAt,
    this.punchOutAt,
    this.status,
    this.workedMinutes,
    this.lateMinutes,
    this.mode,
    this.withinGeofence,
    this.inLatitude,
    this.inLongitude,
  });

  final int userId;
  final String? employeeName;
  final String? employeeCode;
  final DateTime? punchInAt;
  final DateTime? punchOutAt;
  final String? status;
  final int? workedMinutes;
  final int? lateMinutes;

  /// Office, work-from-home or field. Decides whether a location means anything.
  final String? mode;

  /// Whether a field punch landed inside the site it was supposed to.
  final bool? withinGeofence;

  /// Where the punch-in happened. Null for an office punch, which is not
  /// geofenced, and null on older records taken before locations were kept.
  final double? inLatitude;
  final double? inLongitude;

  bool get hasLocation => inLatitude != null && inLongitude != null;

  /// The same row with a name attached.
  ///
  /// The attendance endpoint returns a userId and no person, so names come
  /// from the directory and are joined on afterwards. Existing values win --
  /// if the server ever starts sending a name, it is the better one.
  TeamAttendanceRow withPerson({String? name, String? code}) =>
      TeamAttendanceRow(
        userId: userId,
        employeeName: employeeName ?? name,
        employeeCode: employeeCode ?? code,
        punchInAt: punchInAt,
        punchOutAt: punchOutAt,
        status: status,
        workedMinutes: workedMinutes,
        lateMinutes: lateMinutes,
        mode: mode,
        withinGeofence: withinGeofence,
        inLatitude: inLatitude,
        inLongitude: inLongitude,
      );

  bool get punchedIn => punchInAt != null;
  bool get isLate => (lateMinutes ?? 0) > 0;

  String get workedLabel {
    final m = workedMinutes;
    if (m == null || m <= 0) return '—';
    return '${m ~/ 60}h ${(m % 60).toString().padLeft(2, '0')}m';
  }

  static TeamAttendanceRow fromJson(Map<String, dynamic> json) =>
      TeamAttendanceRow(
        userId: (json['userId'] as num?)?.toInt() ?? 0,
        employeeName: json['employeeName']?.toString(),
        employeeCode: json['employeeCode']?.toString(),
        punchInAt: DateTime.tryParse(json['punchInAt']?.toString() ?? ''),
        punchOutAt: DateTime.tryParse(json['punchOutAt']?.toString() ?? ''),
        status: json['status']?.toString(),
        workedMinutes: (json['workedMinutes'] as num?)?.toInt(),
        mode: json['mode']?.toString(),
        withinGeofence: json['withinGeofence'] as bool?,
        inLatitude: (json['inLatitude'] as num?)?.toDouble(),
        inLongitude: (json['inLongitude'] as num?)?.toDouble(),
        lateMinutes: (json['lateMinutes'] as num?)?.toInt(),
      );
}
