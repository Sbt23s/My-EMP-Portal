/// A safety incident, as the portal records it.
///
/// Mirrors `SafetyIncidentResponse`. Only the fields a phone shows are read;
/// the payload also carries a site id that no screen renders.
class SafetyIncident {
  const SafetyIncident({
    required this.id,
    this.referenceCode,
    this.reportedBy,
    this.reportedByName,
    this.incidentType,
    this.description,
    this.zone,
    this.anonymous = false,
    this.status,
    this.severity,
    this.occurredAt,
    this.resolvedBy,
    this.resolvedByName,
    this.resolutionNotes,
    this.resolvedAt,
    this.createdAt,
  });

  final int id;
  final String? referenceCode;
  final int? reportedBy;
  final String? reportedByName;
  final String? incidentType;
  final String? description;
  final String? zone;
  final bool anonymous;
  final String? status;
  final String? severity;
  final DateTime? occurredAt;
  final int? resolvedBy;
  final String? resolvedByName;
  final String? resolutionNotes;
  final DateTime? resolvedAt;
  final DateTime? createdAt;

  bool get resolved => (status ?? '').toUpperCase() == 'RESOLVED' ||
      (status ?? '').toUpperCase() == 'CLOSED';

  static SafetyIncident fromJson(Map<String, dynamic> json) => SafetyIncident(
        id: (json['id'] as num?)?.toInt() ?? 0,
        referenceCode: _s(json['referenceCode']),
        reportedBy: (json['reportedBy'] as num?)?.toInt(),
        reportedByName: _s(json['reportedByName']),
        incidentType: _s(json['incidentType']),
        description: _s(json['description']),
        zone: _s(json['zone']),
        anonymous: json['anonymous'] == true,
        status: _s(json['status']),
        severity: _s(json['severity']),
        occurredAt: _dt(json['occurredAt']),
        resolvedBy: (json['resolvedBy'] as num?)?.toInt(),
        resolvedByName: _s(json['resolvedByName']),
        resolutionNotes: _s(json['resolutionNotes']),
        resolvedAt: _dt(json['resolvedAt']),
        createdAt: _dt(json['createdAt']),
      );

  static String? _s(dynamic v) {
    final t = v?.toString().trim();
    return (t == null || t.isEmpty) ? null : t;
  }

  static DateTime? _dt(dynamic v) => v == null ? null : DateTime.tryParse('$v');
}
