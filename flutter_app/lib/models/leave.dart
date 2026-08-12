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
  );
}
