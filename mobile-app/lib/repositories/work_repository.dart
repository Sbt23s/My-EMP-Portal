import 'dart:io';

import 'package:dio/dio.dart';
import 'package:intl/intl.dart';

import '../core/error/failures.dart';
import '../core/network/api_client.dart';
import '../core/network/api_envelope.dart';
import '../models/attendance.dart';
import '../models/chat.dart';
import '../models/dashboard.dart';
import '../models/leave.dart';
import '../models/work_items.dart';
import '../models/complaint.dart';
import '../models/directory_person.dart';
import '../models/work_report.dart';
import '../models/safety.dart';
import '../models/celebration.dart';
import '../models/my_team.dart';

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
    String? category,
    String? attachments,
  }) async {
    final data = await _api.post(
      '/tickets',
      body: {
        'subject': subject.trim(),
        'description': description.trim(),
        if (type != null) 'type': type,
        if (priority != null) 'priority': priority,
        if (category != null && category.trim().isNotEmpty) 'category': category.trim(),
        if (attachments != null && attachments.isNotEmpty) 'attachments': attachments,
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
    int? startingKm,
    int? endingKm,
    int? hillsKm,
    int? plainsKm,
    num? busFare,
    num? others,
    String? petrolSlipPath,
    String? photos,
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
        if (startingKm != null) 'startingKm': startingKm,
        if (endingKm != null) 'endingKm': endingKm,
        if (hillsKm != null) 'hillsKm': hillsKm,
        if (plainsKm != null) 'plainsKm': plainsKm,
        if (busFare != null) 'busFare': busFare,
        if (others != null) 'others': others,
        if (petrolSlipPath != null && petrolSlipPath.isNotEmpty)
          'petrolSlipPath': petrolSlipPath,
        if (photos != null && photos.isNotEmpty) 'photos': photos,
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

  // ---- chat ---------------------------------------------------------------

  /// GET /communities/me — the conversations this person is in.
  Future<List<ChatChannel>> myChannels() async {
    final data = await _api.get('/communities/me');
    return ApiEnvelope.listOf(data).map(ChatChannel.fromJson).toList();
  }

  /// GET /communities/contacts — people a private chat can be started with.
  Future<List<Map<String, dynamic>>> chatContacts() async {
    final data = await _api.get('/communities/contacts');
    return ApiEnvelope.listOf(data).toList();
  }

  /// POST /communities/direct/{userId} — find or create the 1:1 conversation.
  Future<ChatChannel> openDirect(int userId) async {
    final data = await _api.post('/communities/direct/$userId');
    if (data is! Map<String, dynamic>) throw const ParseFailure();
    return ChatChannel.fromJson(data);
  }

  /// GET /communities/{id}/messages
  Future<List<ChatMessage>> messages(int channelId) async {
    final data = await _api.get('/communities/$channelId/messages');
    return ApiEnvelope.listOf(data).map(ChatMessage.fromJson).toList();
  }

  /// POST /communities/{id}/messages
  ///
  /// Answers nothing — the endpoint returns 200 with an empty body, so the
  /// caller reloads rather than appending a message it invented.
  ///
  /// The extras mirror the web composer: [parentId] makes this a reply,
  /// [pollOptions] (two or more labels) turns it into a poll, and
  /// [requiresAck] asks readers of an announcement to confirm they have seen it.
  Future<void> sendMessage(
    int channelId,
    String content, {
    int? parentId,
    List<String>? pollOptions,
    bool requiresAck = false,
  }) =>
      _api.post('/communities/$channelId/messages', body: {
        'content': content,
        if (parentId != null) 'parentId': parentId,
        if (pollOptions != null && pollOptions.isNotEmpty)
          'pollOptions': pollOptions,
        if (requiresAck) 'requiresAck': true,
      });

  /// POST /communities/{id}/attachments — files, with an optional caption.
  ///
  /// One request carries every file; the saved message (with the paths attached)
  /// arrives back through the room's live channel, which is why the caller
  /// simply reloads after this completes.
  Future<void> sendChatAttachments(
    int channelId,
    List<File> files, {
    String? caption,
  }) async {
    final form = FormData();
    for (final f in files) {
      form.files.add(MapEntry(
        'files',
        await MultipartFile.fromFile(f.path, filename: f.uri.pathSegments.last),
      ));
    }
    if (caption != null && caption.trim().isNotEmpty) {
      form.fields.add(MapEntry('caption', caption.trim()));
    }
    await _api.upload('/communities/$channelId/attachments', form);
  }

  /// POST /communities/{id}/voice — a voice note.
  Future<void> sendChatVoice(int channelId, File audio) async {
    final form = FormData();
    form.files.add(MapEntry(
      'file',
      await MultipartFile.fromFile(audio.path, filename: 'voice.m4a'),
    ));
    await _api.upload('/communities/$channelId/voice', form);
  }

  /// POST /communities/messages/{id}/read
  Future<void> markMessageRead(int messageId) =>
      _api.post('/communities/messages/$messageId/read');

  /// DELETE /communities/messages/{id} — soft-delete; the room keeps a tombstone.
  Future<void> deleteMessage(int messageId) =>
      _api.delete('/communities/messages/$messageId');

  /// POST /communities/messages/{id}/vote — a poll answer.
  Future<void> votePoll(int messageId, int optionIndex) =>
      _api.post('/communities/messages/$messageId/vote',
          body: {'optionIndex': optionIndex});

  /// POST /communities/messages/{id}/acknowledge — "I have read this" on an
  /// announcement that asks for it.
  Future<void> acknowledgeMessage(int messageId) =>
      _api.post('/communities/messages/$messageId/acknowledge');

  /// GET /communities/{id}/messages/search?q= — the room's messages containing
  /// the query.
  Future<List<ChatMessage>> searchMessages(int channelId, String query) async {
    final data = await _api.get(
      '/communities/$channelId/messages/search',
      query: {'q': query.trim()},
    );
    return ApiEnvelope.listOf(data).map(ChatMessage.fromJson).toList();
  }

  /// GET /communities/{id}/messages/pinned — the room's pinned messages.
  Future<List<ChatMessage>> pinnedMessages(int channelId) async {
    final data = await _api.get('/communities/$channelId/messages/pinned');
    return ApiEnvelope.listOf(data).map(ChatMessage.fromJson).toList();
  }

  // ---- short permissions --------------------------------------------------

  /// GET /leave/permissions/me
  Future<List<PermissionRequestItem>> myPermissions() async {
    final data = await _api.get('/leave/permissions/me');
    return ApiEnvelope.listOf(data).map(PermissionRequestItem.fromJson).toList();
  }

  /// GET /leave/permissions/approvers — who this person may send one to.
  Future<List<Map<String, dynamic>>> permissionApprovers() async {
    final data = await _api.get('/leave/permissions/approvers');
    return ApiEnvelope.listOf(data).toList();
  }

  /// POST /leave/permissions
  Future<void> applyForPermission({
    required DateTime requestDate,
    required String fromTime,
    required String toTime,
    String? reason,
    int? requestedTo,
    String priority = 'MEDIUM',
  }) =>
      _api.post('/leave/permissions', body: {
        'requestDate': _apiDate.format(requestDate),
        'fromTime': fromTime,
        'toTime': toTime,
        if (reason != null && reason.trim().isNotEmpty) 'reason': reason.trim(),
        if (requestedTo != null) 'requestedTo': requestedTo,
        'priority': priority,
      });

  /// POST /leave/permissions/{id}/cancel
  Future<void> cancelPermission(int id) =>
      _api.post('/leave/permissions/$id/cancel');

  /// GET /leave/permissions/for-me — permission requests sent to this approver.
  ///
  /// Team leads and HR only; the server checks LEAVE_APPROVE. A refusal arrives
  /// as a ForbiddenFailure rather than an empty list, so the screen can say "not
  /// yours to decide" instead of "nobody asked for anything".
  Future<List<PermissionRequestItem>> permissionsForMe() async {
    final data = await _api.get('/leave/permissions/for-me');
    return ApiEnvelope.listOf(data).map(PermissionRequestItem.fromJson).toList();
  }

  /// POST /leave/permissions/{id}/decision
  ///
  /// The endpoint reads either {status} or {approve}; both are sent, because a
  /// decision that silently does nothing is the worst outcome here and the cost
  /// of the second field is nothing.
  Future<void> decidePermission(int id, {required bool approve, String? comment}) =>
      _api.post('/leave/permissions/$id/decision', body: {
        'status': approve ? 'APPROVED' : 'REJECTED',
        'approve': approve,
        if (comment != null && comment.trim().isNotEmpty) 'comment': comment.trim(),
      });

  /// POST /communities/messages/{id}/reactions — adds the emoji, or removes it
  /// when it is already there. The server decides which; there is no separate
  /// "unreact" call to get out of step with.
  Future<void> reactToMessage(int messageId, String emoji) =>
      _api.post('/communities/messages/$messageId/reactions',
          body: {'emoji': emoji});

  /// POST /communities/messages/{id}/pin
  Future<void> pinMessage(int messageId, {required bool pinned}) =>
      _api.post('/communities/messages/$messageId/pin', body: {'pinned': pinned});

  // ---- leave policies (HR) -----------------------------------------------

  /// GET /leave/types — the kinds of leave and how many days each carries.
  Future<List<LeaveType>> allLeaveTypes() => leaveTypes();

  /// GET /org/holidays — the company calendar for a year.
  Future<List<Map<String, dynamic>>> holidays(int year) async {
    final data = await _api.get('/org/holidays', query: {'year': year});
    return ApiEnvelope.listOf(data).toList();
  }

  // ---- payroll (HR) -------------------------------------------------------

  /// GET /payroll/salaries — every employee's standing salary.
  Future<List<Map<String, dynamic>>> salaries() async {
    final data = await _api.get('/payroll/salaries');
    return ApiEnvelope.listOf(data).toList();
  }

  /// GET /payroll/salary-months — the months payroll has been run for.
  ///
  /// The server requires the month and year to return, so both are passed.
  /// (Earlier versions of this method sent neither, which the server answered
  /// with 400 — the screen never called it, but the method was a trap for
  /// whoever wired it up later.)
  Future<List<Map<String, dynamic>>> salaryMonths({
    required int month,
    required int year,
  }) async {
    final data = await _api.get('/payroll/salary-months', query: {
      'month': '$month',
      'year': '$year',
    });
    return ApiEnvelope.listOf(data).toList();
  }

  // ---- onboarding (HR) ----------------------------------------------------

  /// GET /onboarding/employees — ids of people still being onboarded.
  Future<List<int>> onboardingEmployeeIds() async {
    final data = await _api.get('/onboarding/employees');
    // A bare list of numbers, not of objects — so it is read directly rather
    // than through listOf, which types its elements as maps.
    final raw = data is List ? data : const [];
    return raw
        .map((e) => e is num ? e.toInt() : int.tryParse('$e') ?? 0)
        .where((id) => id > 0)
        .toList();
  }

  /// GET /onboarding/{userId} — that person's checklist.
  Future<List<Map<String, dynamic>>> onboardingTasks(int userId) async {
    final data = await _api.get('/onboarding/$userId');
    return ApiEnvelope.listOf(data).toList();
  }

  /// POST /onboarding/{userId}/tasks/{taskId}/complete
  Future<void> completeOnboardingTask(int userId, int taskId) =>
      _api.post('/onboarding/$userId/tasks/$taskId/complete');

  // ---- safety incidents ---------------------------------------------------

  /// POST /safety-incidents — report a safety incident.
  Future<Map<String, dynamic>> reportSafetyIncident({
    required String incidentType,
    required String description,
    String? zone,
    bool anonymous = false,
    String? occurredAt,
    String? severity,
  }) async {
    final data = await _api.post('/safety-incidents', body: {
      'incidentType': incidentType,
      'description': description,
      if (zone != null && zone.trim().isNotEmpty) 'zone': zone.trim(),
      'anonymous': anonymous,
      if (occurredAt != null) 'occurredAt': occurredAt,
      if (severity != null) 'severity': severity,
    });
    if (data is! Map<String, dynamic>) throw const ParseFailure();
    return data;
  }

  /// GET /safety-incidents/mine — the caller's own reports, paged on the server.
  Future<List<SafetyIncident>> mySafetyIncidents({int size = 50}) async {
    final data = await _api.get('/safety-incidents/mine', query: {'size': size});
    return ApiEnvelope.listOf(data).map(SafetyIncident.fromJson).toList();
  }

  /// GET /safety-incidents — staff view of every report (REPORT_VIEW).
  Future<List<SafetyIncident>> allSafetyIncidents({String? status, int size = 100}) async {
    final data = await _api.get('/safety-incidents', query: {
      'size': size,
      if (status != null && status.trim().isNotEmpty) 'status': status.trim(),
    });
    return ApiEnvelope.listOf(data).map(SafetyIncident.fromJson).toList();
  }

  /// POST /safety-incidents/{id}/resolve — staff resolution of one report.
  Future<void> resolveSafetyIncident(int id, {required String status, String? notes}) =>
      _api.post('/safety-incidents/$id/resolve', body: {
        'status': status,
        if (notes != null && notes.trim().isNotEmpty) 'resolutionNotes': notes.trim(),
      });

  // ---- my team ------------------------------------------------------------

  /// GET /users/my-team — this person's team and its active members.
  Future<MyTeam> myTeam() async {
    final data = await _api.get('/users/my-team');
    if (data is! Map<String, dynamic>) throw const ParseFailure();
    return MyTeam.fromJson(data);
  }

  /// GET /dashboard/celebrations — upcoming birthdays and anniversaries.
  Future<List<Celebration>> celebrations() async {
    final data = await _api.get('/dashboard/celebrations');
    return ApiEnvelope.listOf(data).map(Celebration.fromJson).toList();
  }

  /// GET /leave/on-leave — who is off today.
  Future<List<LeaveRequest>> onLeaveToday() async {
    final data = await _api.get('/leave/on-leave');
    return ApiEnvelope.listOf(data).map(LeaveRequest.fromJson).toList();
  }

  /// POST /communities/team — find or create this person's team channel.
  Future<ChatChannel> openTeamChat() async {
    final data = await _api.post('/communities/team');
    if (data is! Map<String, dynamic>) throw const ParseFailure();
    return ChatChannel.fromJson(data);
  }

  // ---- communities management (ORG_MANAGE / COMMUNITY_MANAGE) -------------

  /// GET /communities — every group (management view; direct chats filtered out).
  Future<List<ChatChannel>> communityGroups() async {
    final data = await _api.get('/communities');
    return ApiEnvelope.listOf(data).map(ChatChannel.fromJson).toList();
  }

  /// POST /communities — create a community group.
  Future<ChatChannel> createCommunityGroup({
    required String name,
    String? description,
    bool isAnnouncement = false,
  }) async {
    final data = await _api.post('/communities', body: {
      'name': name.trim(),
      if (description != null && description.trim().isNotEmpty)
        'description': description.trim(),
      'isAnnouncement': isAnnouncement,
    });
    if (data is! Map<String, dynamic>) throw const ParseFailure();
    return ChatChannel.fromJson(data);
  }

  /// GET /communities/{id}/members — who is in a group.
  Future<List<DirectoryPerson>> communityMembers(int id) async {
    final data = await _api.get('/communities/$id/members');
    return ApiEnvelope.listOf(data).map(DirectoryPerson.fromJson).toList();
  }

  /// POST /communities/{id}/members — add a person to a group.
  Future<void> addCommunityMember(int id, int userId) =>
      _api.post('/communities/$id/members', body: {'userId': userId});

  /// DELETE /communities/{id}/members/{userId} — remove a person from a group.
  Future<void> removeCommunityMember(int id, int userId) =>
      _api.delete('/communities/$id/members/$userId');

  /// DELETE /communities/{id} — delete a group.
  Future<void> deleteCommunity(int id) => _api.delete('/communities/$id');

  // ---- audit log (USER_MANAGE / EMPLOYEE_MANAGE) --------------------------

  /// GET /audit?page&size — the security trail, newest first.
  Future<List<Map<String, dynamic>>> auditLog({int page = 0, int size = 50}) async {
    final data = await _api.get('/audit', query: {'page': page, 'size': size});
    return ApiEnvelope.listOf(data).toList();
  }

  /// GET /audit/summary — counts by category for the top of the audit screen.
  Future<Map<String, dynamic>> auditSummary() async {
    final data = await _api.get('/audit/summary');
    if (data is! Map<String, dynamic>) return const {};
    return data;
  }

  // ---- AI assistant (chatbot) ---------------------------------------------

  /// POST /chatbot/chat — one turn of the assistant conversation.
  Future<String> chatbotChat({
    required String message,
    String? lang,
    List<Map<String, String>> history = const [],
  }) async {
    final data = await _api.post('/chatbot/chat', body: {
      'message': message,
      if (lang != null && lang.trim().isNotEmpty) 'lang': lang.trim(),
      'history': history,
    });
    if (data is! Map<String, dynamic>) throw const ParseFailure();
    return data['reply']?.toString() ?? '';
  }

  /// GET /chatbot/config — assistant name, enabled flag, language.
  Future<Map<String, dynamic>> chatbotConfig() async {
    final data = await _api.get('/chatbot/config');
    if (data is! Map<String, dynamic>) throw const ParseFailure();
    return data;
  }
}
