/// A leave type the company offers.
class LeaveType {
  const LeaveType({
    required this.id,
    required this.code,
    required this.name,
    this.maxDaysPerYear,
    this.paid,
  });

  final int id;
  final String code;
  final String name;
  final num? maxDaysPerYear;
  final bool? paid;

  factory LeaveType.fromJson(Map<String, dynamic> json) => LeaveType(
    id: (json['id'] as num?)?.toInt() ?? 0,
    code: json['code']?.toString() ?? '',
    name: json['name']?.toString() ?? '',
    maxDaysPerYear: json['maxDaysPerYear'] as num?,
    paid: json['paid'] as bool?,
  );
}

/// What is left of an allowance this year.
class LeaveBalance {
  const LeaveBalance({
    required this.leaveTypeCode,
    required this.leaveTypeName,
    required this.allocated,
    required this.used,
    required this.available,
  });

  final String leaveTypeCode;
  final String leaveTypeName;
  final num allocated;
  final num used;
  final num available;

  factory LeaveBalance.fromJson(Map<String, dynamic> json) => LeaveBalance(
    leaveTypeCode: json['leaveTypeCode']?.toString() ?? '',
    leaveTypeName: json['leaveTypeName']?.toString() ?? '',
    allocated: json['allocated'] as num? ?? 0,
    used: json['used'] as num? ?? 0,
    available: json['available'] as num? ?? 0,
  );
}

/// A leave request and where it has got to.
class LeaveRequest {
  const LeaveRequest({
    required this.id,
    required this.fromDate,
    required this.toDate,
    required this.status,
    this.leaveTypeName,
    this.workingDays,
    this.reason,
    this.decidedAt,
    this.decidedByName,
    this.employeeName,
    this.canAct = false,
  });

  final int id;
  final DateTime fromDate;
  final DateTime toDate;
  final String status;
  final String? leaveTypeName;
  final num? workingDays;
  final String? reason;
  final DateTime? decidedAt;
  final String? decidedByName;
  final String? employeeName;

  /*
    Whether the person reading this may decide it -- the server's own answer.

    The approvals queue deliberately returns rows an approver may see but not
    act on: a Team Leader sees their whole team, an administrator sees
    everything, and the right to decide any one row is narrower than the right
    to look at it. The website gates its buttons on this flag; nothing here
    read it, so every visible row offered Approve and Reject.

    Defaults false: a row that arrives without the flag is not actionable
    until the server says it is, which fails the safe way.
  */
  final bool canAct;

  bool get isPending => status.toUpperCase() == 'PENDING';
  bool get isApproved => status.toUpperCase() == 'APPROVED';
  bool get isRejected => status.toUpperCase() == 'REJECTED';

  /// Only a pending request can be withdrawn — the server enforces this too.
  bool get canCancel => isPending;

  factory LeaveRequest.fromJson(Map<String, dynamic> json) => LeaveRequest(
    id: (json['id'] as num?)?.toInt() ?? 0,
    fromDate:
        DateTime.tryParse(json['fromDate']?.toString() ?? '') ?? DateTime.now(),
    toDate:
        DateTime.tryParse(json['toDate']?.toString() ?? '') ?? DateTime.now(),
    status: json['status']?.toString() ?? 'PENDING',
    leaveTypeName: json['leaveTypeName']?.toString(),
    workingDays: json['workingDays'] as num?,
    reason: json['reason']?.toString(),
    decidedAt: DateTime.tryParse(json['decidedAt']?.toString() ?? ''),
    decidedByName: json['decidedByName']?.toString(),
    employeeName: json['employeeName']?.toString(),
    canAct: json['canAct'] == true,
  );
}

/// A short permission: an hour or two off, not a whole day.
///
/// Mirrors `PermissionResponse`. Times arrive as plain strings ("14:30") rather
/// than timestamps, because a permission is bounded by the clock on the day it
/// is taken and not by an instant — so they are kept as strings here too rather
/// than parsed into a DateTime that would need a date attached to mean anything.
class PermissionRequestItem {
  const PermissionRequestItem({
    required this.id,
    required this.requestDate,
    required this.fromTime,
    required this.toTime,
    required this.status,
    this.hours,
    this.reason,
    this.priority,
    this.employeeName,
    this.requestedToName,
    this.decidedByName,
    this.decisionComment,
    this.userId,
    this.requestedTo,
  });

  final int id;
  final DateTime requestDate;
  final String fromTime;
  final String toTime;
  final String status;
  final double? hours;
  final String? reason;
  final String? priority;
  final String? employeeName;
  final String? requestedToName;
  final String? decidedByName;
  final String? decisionComment;

  /*
    Who asked, and who was asked, as ids.

    Names are for reading; only the ids can decide whether the person looking
    at this row may act on it -- two colleagues can share a name, and nobody
    may approve their own request.
  */
  final int? userId;
  final int? requestedTo;

  bool get isPending => status.toUpperCase() == 'PENDING';
  bool get isApproved => status.toUpperCase() == 'APPROVED';
  bool get isRejected => status.toUpperCase() == 'REJECTED';

  static String? _blank(dynamic v) {
    final t = v?.toString().trim();
    return (t == null || t.isEmpty) ? null : t;
  }

  static PermissionRequestItem fromJson(Map<String, dynamic> json) =>
      PermissionRequestItem(
        id: (json['id'] as num?)?.toInt() ?? 0,
        requestDate:
            DateTime.tryParse(json['requestDate']?.toString() ?? '') ?? DateTime.now(),
        fromTime: json['fromTime']?.toString() ?? '--:--',
        toTime: json['toTime']?.toString() ?? '--:--',
        // A row with no status is still a row. Treating it as pending is the
        // safe reading: it shows as awaiting a decision rather than silently
        // appearing approved.
        status: _blank(json['status']) ?? 'PENDING',
        hours: double.tryParse(json['hours']?.toString() ?? ''),
        reason: _blank(json['reason']),
        priority: _blank(json['priority']),
        employeeName: _blank(json['employeeName']),
        requestedToName: _blank(json['requestedToName']),
        decidedByName: _blank(json['decidedByName']),
        decisionComment: _blank(json['decisionComment']),
        userId: (json['userId'] as num?)?.toInt(),
        requestedTo: (json['requestedTo'] as num?)?.toInt(),
      );
}
