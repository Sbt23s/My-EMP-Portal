import 'package:intl/intl.dart';

import '../core/error/failures.dart';
import '../core/network/api_client.dart';
import '../core/network/api_envelope.dart';
import '../models/attendance.dart';
import '../models/dashboard.dart';
import '../models/leave.dart';
import '../models/work_items.dart';
import '../models/complaint.dart';
import '../models/directory_person.dart';
import '../models/work_report.dart';

/// Everything an employee does about their own working day: attendance, leave
/// and the dashboard that summarises both.
///
/// One repository rather than three, because these endpoints are read together
/// on almost every screen and splitting them would only add indirection.
class WorkRepository {
  WorkRepository(this._api);

  final ApiClient _api;

  static final DateFormat _apiDate = DateFormat('yyyy-MM-dd');

  // ---- dashboard ---------------------------------------------------------

  /// GET /dashboard/me
  Future<EmployeeDashboard> myDashboard() async {
    final data = await _api.get('/dashboard/me');
    if (data is! Map<String, dynamic>) throw StateError('dashboard');
    return EmployeeDashboard.fromJson(data);
  }

  // ---- attendance --------------------------------------------------------

  /// GET /attendance/today — null before the first punch of the day.
  Future<AttendanceDay?> today() async {
    final data = await _api.get('/attendance/today');
    if (data is Map<String, dynamic> && data.isNotEmpty) {
      return AttendanceDay.fromJson(data);
    }
    return null;
  }

  /// GET /attendance/me?from&to
  ///
  /// Both dates are required by the server; omitting them used to come back as
  /// a 500 and now correctly comes back as a 400, so they are always sent.
  Future<List<AttendanceDay>> attendanceBetween(
    DateTime from,
    DateTime to,
  ) async {
    final data = await _api.get(
      '/attendance/me',
      query: {'from': _apiDate.format(from), 'to': _apiDate.format(to)},
    );
    return ApiEnvelope.listOf(data).map(AttendanceDay.fromJson).toList();
  }

  /// POST /attendance/punch-in. Coordinates are optional on the server.
  Future<AttendanceDay> punchIn({double? latitude, double? longitude}) async {
    final data = await _api.post(
      '/attendance/punch-in',
      body: {
        if (latitude != null) 'latitude': latitude,
        if (longitude != null) 'longitude': longitude,
        'mode': 'MOBILE',
      },
    );
    if (data is! Map<String, dynamic>) throw StateError('punch-in');
    return AttendanceDay.fromJson(data);
  }

  /// POST /attendance/punch-out
  Future<AttendanceDay> punchOut({double? latitude, double? longitude}) async {
    final data = await _api.post(
      '/attendance/punch-out',
      body: {
        if (latitude != null) 'latitude': latitude,
        if (longitude != null) 'longitude': longitude,
        'mode': 'MOBILE',
      },
    );
    if (data is! Map<String, dynamic>) throw StateError('punch-out');
    return AttendanceDay.fromJson(data);
  }

  // ---- leave -------------------------------------------------------------

  /// GET /leave/types
  Future<List<LeaveType>> leaveTypes() async {
    final data = await _api.get('/leave/types');
    return ApiEnvelope.listOf(data).map(LeaveType.fromJson).toList();
  }

  /// GET /leave/balances
  Future<List<LeaveBalance>> leaveBalances() async {
    final data = await _api.get('/leave/balances');
    return ApiEnvelope.listOf(data).map(LeaveBalance.fromJson).toList();
  }

  /// GET /leave/me — paged.
  Future<Paged<LeaveRequest>> myLeave({int page = 0, int size = 20}) async {
    final data = await _api.get(
      '/leave/me',
      query: {'page': page, 'size': size},
    );
    return Paged.from<LeaveRequest>(data, LeaveRequest.fromJson);
  }

  /// GET /leave/approvers — who this person may send a request to.
  Future<List<Map<String, dynamic>>> approvers() async {
    final data = await _api.get('/leave/approvers');
    return ApiEnvelope.listOf(data);
  }

  /// POST /leave/apply
  Future<LeaveRequest> applyForLeave({
    required int leaveTypeId,
    required DateTime fromDate,
    required DateTime toDate,
    String? reason,
    int? requestedTo,
  }) async {
    final data = await _api.post(
      '/leave/apply',
      body: {
        'leaveTypeId': leaveTypeId,
        'fromDate': _apiDate.format(fromDate),
        'toDate': _apiDate.format(toDate),
        if (reason != null && reason.trim().isNotEmpty) 'reason': reason.trim(),
        if (requestedTo != null) 'requestedTo': requestedTo,
      },
    );
    if (data is! Map<String, dynamic>) throw StateError('apply');
    return LeaveRequest.fromJson(data);
  }

  /// POST /leave/{id}/cancel
  Future<void> cancelLeave(int id) => _api.post('/leave/$id/cancel');

  // ---- approvals (managers) ----------------------------------------------

  /// GET /leave/pending — waiting on this approver. Needs LEAVE_APPROVE, so the
  /// screen is only offered to someone who holds it.
  Future<List<LeaveRequest>> pendingApprovals() async {
    final data = await _api.get('/leave/pending');
    return ApiEnvelope.listOf(data).map(LeaveRequest.fromJson).toList();
  }

  /// POST /leave/{id}/decision — decision is APPROVED or REJECTED.
  Future<void> decideLeave(int id, {required bool approve, String? comment}) =>
      _api.post(
        '/leave/$id/decision',
        body: {
          'decision': approve ? 'APPROVED' : 'REJECTED',
          if (comment != null && comment.trim().isNotEmpty)
            'comment': comment.trim(),
        },
      );

  /// GET /attendance/my-team-today — who has punched in, and who has not.
  Future<List<Map<String, dynamic>>> teamToday() async {
    final data = await _api.get('/attendance/my-team-today');
    return ApiEnvelope.listOf(data);
  }

  // ---- payslips ----------------------------------------------------------

  /// GET /payroll/payslip/list
  Future<List<Payslip>> myPayslips() async {
    final data = await _api.get('/payroll/payslip/list');
    return ApiEnvelope.listOf(data).map(Payslip.fromJson).toList();
  }

  // ---- tasks -------------------------------------------------------------

  /// GET /tasks/me
  Future<List<TaskItem>> myTasks() async {
    final data = await _api.get('/tasks/me');
    return ApiEnvelope.listOf(data).map(TaskItem.fromJson).toList();
  }

  /// POST /tasks/{id}/progress — 0 to 100.
  Future<void> updateTaskProgress(int id, int percent) => _api.post(
    '/tasks/$id/progress',
    body: {'progress': percent.clamp(0, 100)},
  );

  /// POST /tasks/{id}/complete
  Future<void> completeTask(int id) => _api.post('/tasks/$id/complete');

  // ---- helpdesk ----------------------------------------------------------

  /// GET /tickets — the caller's own, paged on the server.
  Future<List<Ticket>> myTickets() async {
    final data = await _api.get('/tickets', query: {'size': 50});
    return ApiEnvelope.listOf(data).map(Ticket.fromJson).toList();
  }

  /// POST /tickets
  Future<Ticket> raiseTicket({
    required String subject,
    required String description,
    String? type,
    String? priority,
  }) async {
    final data = await _api.post(
      '/tickets',
      body: {
        'subject': subject.trim(),
        'description': description.trim(),
        if (type != null) 'type': type,
        if (priority != null) 'priority': priority,
      },
    );
    if (data is! Map<String, dynamic>) throw StateError('ticket');
    return Ticket.fromJson(data);
  }

  // ---- claims ------------------------------------------------------------

  /// GET /ta-expenses/me
  Future<List<ExpenseClaim>> myClaims() async {
    final data = await _api.get('/ta-expenses/me');
    return ApiEnvelope.listOf(data).map(ExpenseClaim.fromJson).toList();
  }

  /// POST /ta-expenses
  ///
  /// Travel-allowance claims, the pair to myClaims() above.
  ///
  /// The server treats only [date] as required. Everything else is sent only
  /// when the person filled it in, so a blank field stays absent rather than
  /// arriving as a zero the approver would read as a real measurement.
  Future<ExpenseClaim> submitClaim({
    required DateTime date,
    String? location,
    String? category,
    int? totalKm,
    num? totalAmount,
    String? remarks,
  }) async {
    final data = await _api.post(
      '/ta-expenses',
      body: {
        // Date only — the server parses this as a LocalDate and rejects a
        // full timestamp.
        'date': date.toIso8601String().split('T').first,
        if (location != null && location.trim().isNotEmpty)
          'location': location.trim(),
        if (category != null && category.trim().isNotEmpty)
          'category': category.trim(),
        if (totalKm != null) 'totalKm': totalKm,
        if (totalAmount != null) 'totalAmount': totalAmount,
        if (totalAmount != null) 'grossTotal': totalAmount,
        if (remarks != null && remarks.trim().isNotEmpty)
          'remarks': remarks.trim(),
      },
    );
    if (data is! Map<String, dynamic>) throw StateError('claim');
    return ExpenseClaim.fromJson(data);
  }

  // ---- calendar ----------------------------------------------------------

  /// GET /calendar/events?from=&to=
  ///
  /// Both bounds are required by the server, and it refuses a full timestamp,
  /// so the dates are trimmed to their day here rather than at each call site.
  Future<List<CalendarEvent>> calendarEvents({
    required DateTime from,
    required DateTime to,
  }) async {
    final data = await _api.get(
      '/calendar/events',
      query: {
        'from': from.toIso8601String().split('T').first,
        'to': to.toIso8601String().split('T').first,
      },
    );
    return ApiEnvelope.listOf(data).map(CalendarEvent.fromJson).toList();
  }

  // ---- assets ------------------------------------------------------------

  /// GET /assets/my-assets
  Future<List<AssetItem>> myAssets() async {
    final data = await _api.get('/assets/my-assets');
    return ApiEnvelope.listOf(data).map(AssetItem.fromJson).toList();
  }

  /// POST /assets/{id}/acknowledge — confirms the person has the thing.
  Future<void> acknowledgeAsset(int id) => _api.post('/assets/$id/acknowledge');

  // ---- notifications -----------------------------------------------------

  /// GET /notifications
  ///
  /// An error is thrown rather than swallowed. The web client used to answer a
  /// failed request with three invented notifications, so the bell showed unread
  /// items that did not exist.
  Future<List<AppNotification>> notifications() async {
    final data = await _api.get('/notifications', query: {'size': 30});
    return ApiEnvelope.listOf(data).map(AppNotification.fromJson).toList();
  }

  /// GET /notifications/unread-count
  Future<int> unreadCount() async {
    final data = await _api.get('/notifications/unread-count');
    if (data is Map<String, dynamic>) {
      return (data['count'] as num?)?.toInt() ?? 0;
    }
    return 0;
  }

  Future<void> markNotificationRead(int id) =>
      _api.post('/notifications/$id/read');

  Future<void> markAllNotificationsRead() =>
      _api.post('/notifications/mark-all-read');

  // ---- work reports ------------------------------------------------------

  /// GET /work-reports/me
  Future<List<WorkReport>> myWorkReports() async {
    final data = await _api.get('/work-reports/me');
    return ApiEnvelope.listOf(data).map(WorkReport.fromJson).toList();
  }

  /// GET /work-reports/team — what the people reporting to you filed.
  ///
  /// Team leads and HR only; the server decides that, and a refusal surfaces as
  /// a ForbiddenFailure rather than an empty list, so the screen can say "not
  /// yours to see" instead of "nobody filed anything".
  Future<List<WorkReport>> teamWorkReports() async {
    final data = await _api.get('/work-reports/team');
    return ApiEnvelope.listOf(data).map(WorkReport.fromJson).toList();
  }

  /// POST /work-reports
  Future<WorkReport> submitWorkReport({
    required DateTime workDate,
    required String projectName,
    required double workHours,
    String? taskDescription,
  }) async {
    final data = await _api.post(
      '/work-reports',
      body: {
        'workDate': _apiDate.format(workDate),
        'projectName': projectName.trim(),
        // Sent as a number, not a string: the server binds it to a BigDecimal
        // and a quoted value is rejected as a validation error whose message
        // does not mention quoting.
        'workHours': workHours,
        if (taskDescription != null && taskDescription.trim().isNotEmpty)
          'taskDescription': taskDescription.trim(),
      },
    );
    if (data is! Map<String, dynamic>) throw const ParseFailure();
    return WorkReport.fromJson(data);
  }

  /// DELETE /work-reports/{id}
  Future<void> deleteWorkReport(int id) => _api.delete('/work-reports/$id');

  // ---- directory ---------------------------------------------------------

  /// GET /users — colleagues, for the directory and the team screens.
  ///
  /// A page size, not everything: the web asks for a thousand because a desktop
  /// table can hold them, but a phone scrolling two hundred rows it will never
  /// read costs a slow request on a connection that is often the worst part of
  /// somebody's day. Search narrows on the server.
  Future<List<DirectoryPerson>> directory({String? query, int size = 200}) async {
    final data = await _api.get('/users', query: {
      'size': size,
      'status': 'ACTIVE',
      if (query != null && query.trim().isNotEmpty) 'q': query.trim(),
    });
    return ApiEnvelope.listOf(data).map(DirectoryPerson.fromJson).toList();
  }

  /// GET /attendance/team?date= — who was in on a given day.
  ///
  /// Team leads and HR. The server decides whose rows come back; this does not
  /// filter, so a lead sees their team and HR sees the company, exactly as on
  /// the web.
  Future<List<TeamAttendanceRow>> teamAttendance(DateTime date) async {
    final data = await _api.get(
      '/attendance/team',
      query: {'date': _apiDate.format(date)},
    );
    return ApiEnvelope.listOf(data).map(TeamAttendanceRow.fromJson).toList();
  }

  // ---- complaints --------------------------------------------------------

  /// GET /complaints/mine
  Future<List<Complaint>> myComplaints() async {
    final data = await _api.get('/complaints/mine');
    return ApiEnvelope.listOf(data).map(Complaint.fromJson).toList();
  }

  /// GET /complaints/recipients — the HR people this can be addressed to.
  Future<List<ComplaintRecipient>> complaintRecipients() async {
    final data = await _api.get('/complaints/recipients');
    return ApiEnvelope.listOf(data).map(ComplaintRecipient.fromJson).toList();
  }

  /// POST /complaints
  Future<Complaint> raiseComplaint({
    required String subject,
    required String description,
    String kind = 'COMPLAINT',
    String priority = 'MEDIUM',
    String? category,
    int? requestedTo,
  }) async {
    final data = await _api.post(
      '/complaints',
      body: {
        'kind': kind,
        'subject': subject.trim(),
        'description': description.trim(),
        'priority': priority,
        if (category != null && category.trim().isNotEmpty)
          'category': category.trim(),
        // Omitted rather than sent as null: absent means "any HR can pick this
        // up", which is the server's own default and the right one when nobody
        // was chosen.
        if (requestedTo != null) 'requestedTo': requestedTo,
      },
    );
    if (data is! Map<String, dynamic>) throw const ParseFailure();
    return Complaint.fromJson(data);
  }
}
