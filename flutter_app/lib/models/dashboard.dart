import 'leave.dart';

/// The employee's own dashboard, as `EmployeeDashboard` sends it.
///
/// There is no placeholder constructor here on purpose. The web client used to
/// invent numbers when this request failed — 480 minutes worked, one leave
/// request pending — on a card people read their own attendance from. A failed
/// load shows an error, not a plausible-looking lie.
class EmployeeDashboard {
  const EmployeeDashboard({
    required this.employeeName,
    required this.employeeCode,
    required this.punchedInToday,
    required this.leaveBalances,
    required this.pendingLeaveRequests,
    required this.myOpenTickets,
    required this.myAssets,
    this.punchInAt,
    this.punchOutAt,
    this.workedMinutesToday,
  });

  final String employeeName;
  final String employeeCode;
  final bool punchedInToday;
  final List<LeaveBalance> leaveBalances;
  final int pendingLeaveRequests;
  final int myOpenTickets;
  final int myAssets;
  final DateTime? punchInAt;
  final DateTime? punchOutAt;
  final int? workedMinutesToday;

  String get workedLabel {
    final m = workedMinutesToday;
    if (m == null || m <= 0) return '—';
    final h = m ~/ 60;
    return h > 0 ? '${h}h ${m % 60}m' : '${m}m';
  }

  factory EmployeeDashboard.fromJson(Map<String, dynamic> json) =>
      EmployeeDashboard(
        employeeName: json['employeeName']?.toString() ?? '',
        employeeCode: json['employeeCode']?.toString() ?? '',
        punchedInToday: json['punchedInToday'] as bool? ?? false,
        leaveBalances:
            (json['leaveBalances'] as List?)
                ?.whereType<Map<String, dynamic>>()
                .map(LeaveBalance.fromJson)
                .toList() ??
            const [],
        pendingLeaveRequests:
            (json['pendingLeaveRequests'] as num?)?.toInt() ?? 0,
        myOpenTickets: (json['myOpenTickets'] as num?)?.toInt() ?? 0,
        myAssets: (json['myAssets'] as num?)?.toInt() ?? 0,
        punchInAt: DateTime.tryParse(json['punchInAt']?.toString() ?? ''),
        punchOutAt: DateTime.tryParse(json['punchOutAt']?.toString() ?? ''),
        workedMinutesToday: (json['workedMinutesToday'] as num?)?.toInt(),
      );
}
