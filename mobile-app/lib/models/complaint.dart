/// A complaint or a need, raised with HR.
///
/// Mirrors `ComplaintResponse`. Only the fields a phone shows are kept — the
/// handler's id, the update timestamp and the team are on the server's record
/// and add nothing to a list somebody scrolls.
class Complaint {
  const Complaint({
    required this.id,
    required this.subject,
    required this.status,
    required this.kind,
    required this.priority,
    this.referenceCode,
    this.category,
    this.description,
    this.hrResponse,
    this.requestedToName,
    this.handledByName,
    this.raisedBy,
    this.requestedTo,
    this.raisedByName,
    this.raisedByCode,
    this.createdAt,
    this.resolvedAt,
  });

  final int id;
  final String subject;

  /// OPEN | IN_REVIEW | RESOLVED | REJECTED
  final String status;

  /// COMPLAINT | NEED
  final String kind;

  /// LOW | MEDIUM | HIGH
  final String priority;

  final String? referenceCode;
  final String? category;
  final String? description;

  /// What HR wrote back, once they have.
  final String? hrResponse;
  final String? requestedToName;
  final String? handledByName;

  /*
    Who raised it and who it was addressed to, as ids.

    Names are for reading; the ids are what decides whether the person looking
    at this may act on it. Judging your own complaint is not review, so the
    reviewer screen needs to tell "sent to me" from "raised by me", and a name
    cannot do that -- two people can share one.
  */
  final int? raisedBy;
  final int? requestedTo;
  final String? raisedByName;
  final String? raisedByCode;
  final DateTime? createdAt;
  final DateTime? resolvedAt;

  bool get isClosed => status == 'RESOLVED' || status == 'REJECTED';

  static Complaint fromJson(Map<String, dynamic> json) => Complaint(
        id: (json['id'] as num?)?.toInt() ?? 0,
        subject: _text(json['subject']) ?? 'Untitled',
        // Defaulted rather than left null: every branch that reads status
        // expects one of the four, and "" would fall through all of them and
        // render a row with no state at all.
        status: (_text(json['status']) ?? 'OPEN').toUpperCase(),
        kind: (_text(json['kind']) ?? 'COMPLAINT').toUpperCase(),
        priority: (_text(json['priority']) ?? 'MEDIUM').toUpperCase(),
        referenceCode: _text(json['referenceCode']),
        category: _text(json['category']),
        description: _text(json['description']),
        hrResponse: _text(json['hrResponse']),
        requestedToName: _text(json['requestedToName']),
        handledByName: _text(json['handledByName']),
        raisedBy: (json['raisedBy'] as num?)?.toInt(),
        requestedTo: (json['requestedTo'] as num?)?.toInt(),
        raisedByName: _text(json['raisedByName']),
        raisedByCode: _text(json['raisedByCode']),
        createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? ''),
        resolvedAt: DateTime.tryParse(json['resolvedAt']?.toString() ?? ''),
      );

  static String? _text(dynamic value) {
    final t = value?.toString().trim();
    return (t == null || t.isEmpty) ? null : t;
  }
}

/// Somebody a complaint can be addressed to. From `/complaints/recipients`.
class ComplaintRecipient {
  const ComplaintRecipient({required this.id, required this.name, this.role});

  final int id;
  final String name;
  final String? role;

  static ComplaintRecipient fromJson(Map<String, dynamic> json) =>
      ComplaintRecipient(
        id: (json['id'] as num?)?.toInt() ?? 0,
        // The endpoint returns users, so the field is `name`; falling back to
        // the username keeps a row usable rather than showing a blank option.
        name: json['name']?.toString().trim().isNotEmpty == true
            ? json['name'].toString().trim()
            : (json['username']?.toString() ?? 'Unknown'),
        role: json['role']?.toString(),
      );
}
