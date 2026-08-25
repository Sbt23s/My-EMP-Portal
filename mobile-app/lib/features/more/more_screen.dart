import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

import '../../core/config/app_config.dart';

import '../../models/work_items.dart';
import '../../providers/app_providers.dart';
import '../../providers/modules_provider.dart';
import 'audit_screen.dart';
import 'communities_screen.dart';
import 'employees_screen.dart';
import 'my_team_screen.dart';
import '../../themes/app_theme.dart';
import '../../widgets/states.dart';
import '../approvals/approvals_screen.dart';
import 'calendar_screen.dart';
import 'raise_ticket_sheet.dart';
import 'ticket_detail_sheet.dart';
import 'complaints_screen.dart';
import 'submit_claim_sheet.dart';
import 'team_attendance_screen.dart';
import 'teams_screen.dart';
import 'work_reports_screen.dart';
import '../chat/chat_screen.dart';
import '../hr/hr_screens.dart';
import '../leave/permissions_screen.dart';

final payslipsProvider = FutureProvider.autoDispose<List<Payslip>>(
  (ref) => ref.watch(workRepositoryProvider).myPayslips(),
);
final tasksProvider = FutureProvider.autoDispose<List<TaskItem>>(
  (ref) => ref.watch(workRepositoryProvider).myTasks(),
);
final ticketsProvider = FutureProvider.autoDispose<List<Ticket>>(
  (ref) => ref.watch(workRepositoryProvider).myTickets(),
);
final assetsProvider = FutureProvider.autoDispose<List<AssetItem>>(
  (ref) => ref.watch(workRepositoryProvider).myAssets(),
);
final claimsProvider = FutureProvider.autoDispose<List<ExpenseClaim>>(
  (ref) => ref.watch(workRepositoryProvider).myClaims(),
);

/// The rest of the modules, one row each.
///
/// A hub rather than more tabs: five destinations is already the most a bottom
/// bar carries comfortably, and these are visited occasionally rather than daily.
class MoreScreen extends ConsumerWidget {
  const MoreScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final modules = ref.watch(modulesProvider);

    /*
     * Gated on the same module codes the web sidebar uses.
     *
     * Built as a list first so the empty case can be told apart from a list that
     * simply has not been filtered yet — a hub with nothing in it needs to say
     * why, and it cannot do that from inside a children: [] literal.
     */
    final user = ref.watch(currentUserProvider);
    // Who may look at other people's attendance. The same permission the web
    // sidebar uses for its Employee Attendance link — and the server checks it
    // again, so a hidden row is a courtesy, never the control.
    final canSeeTeam = user?.can('ATTENDANCE_TEAM') ?? false;

    final entries = <Widget>[
      // Only for someone who may actually decide. The server checks this as
      // well — hiding the row is a courtesy, not the control.
      if ((user?.can('LEAVE_APPROVE') ?? false) &&
          modules.has('LEAVE'))
        _Entry(
          icon: Icons.fact_check_outlined,
          title: 'Approvals',
          subtitle: 'Leave requests and who is in today',
          onTap: () => _open(context, const ApprovalsScreen()),
        ),
      if (modules.has('PAYROLL'))
        _Entry(
          icon: Icons.receipt_long_rounded,
          title: 'Payslips',
          subtitle: 'Your monthly pay',
          onTap: () => _open(context, const PayslipsScreen()),
        ),
      // Sits under LEAVE, as it does on the web: a short permission is time off,
      // just not a whole day of it.
      if (modules.has('LEAVE'))
        _Entry(
          icon: Icons.schedule_rounded,
          title: 'Permission',
          subtitle: 'An hour or two off, without a leave day',
          onTap: () => _open(context, const PermissionsScreen()),
        ),
      if (modules.has('REPORTS'))
        _Entry(
          icon: Icons.description_outlined,
          title: 'Work reports',
          subtitle: 'What you did, day by day',
          onTap: () => _open(context, const WorkReportsScreen()),
        ),
      if (modules.has('TASKS'))
        _Entry(
          icon: Icons.checklist_rounded,
          title: 'Tasks',
          subtitle: 'What is assigned to you',
          onTap: () => _open(context, const TasksScreen()),
        ),
      if (modules.has('HELPDESK'))
        _Entry(
          icon: Icons.support_agent_rounded,
          title: 'Supports',
          subtitle: 'Raise and track requests',
          onTap: () => _open(context, const TicketsScreen()),
        ),
      if (modules.has('ASSETS'))
        _Entry(
          icon: Icons.inventory_2_outlined,
          title: 'My assets',
          subtitle: 'Equipment issued to you',
          onTap: () => _open(context, const AssetsScreen()),
        ),
      if (modules.has('EXPENSES'))
        _Entry(
          icon: Icons.receipt_rounded,
          title: 'Claims',
          subtitle: 'Travel and expenses',
          onTap: () => _open(context, const ClaimsScreen()),
        ),
      if (modules.has('ATTENDANCE') && canSeeTeam)
        _Entry(
          icon: Icons.groups_outlined,
          title: 'Team attendance',
          subtitle: 'Who was in, day by day',
          onTap: () => _open(context, const TeamAttendanceScreen()),
        ),
      if (modules.has('HELPDESK'))
        _Entry(
          icon: Icons.forum_outlined,
          title: 'Complaints',
          subtitle: 'Raise something with HR',
          onTap: () => _open(context, const ComplaintsScreen()),
        ),
      /*
        Teams is the company-wide view of every team, which is an HR and
        executive question rather than a team leader's. A leader's own people
        are under "My team" below, which is the view that answers what they
        actually need -- and USER_MANAGE is what separates the two, because a
        leader does not hold it.
      */
      if (modules.has('TEAMS') && (user?.can('USER_MANAGE') ?? false))
        _Entry(
          icon: Icons.groups_2_outlined,
          title: 'Teams',
          subtitle: 'Who is on which team',
          onTap: () => _open(context, const TeamsScreen()),
        ),
      if (modules.has('CHAT'))
        _Entry(
          icon: Icons.forum_outlined,
          title: 'Chat',
          subtitle: 'Channels and direct messages',
          onTap: () => _open(context, const ChatScreen()),
        ),


      // My team: the people around you, who is celebrating, who is off.
      if (modules.has('TEAMS'))
        _Entry(
          icon: Icons.groups_2_outlined,
          title: 'My team',
          subtitle: 'Celebrations, on-leave and the team chat',
          onTap: () => _open(context, const MyTeamScreen()),
        ),
      // The directory: who works here, and how to reach them.
      /*
        The directory is for the people who administer it. ATTENDANCE_TEAM and
        REPORT_VIEW were in this list, and both are held by team leaders, so
        the whole company directory was reachable by every leader. USER_MANAGE
        alone leaves it with HR, the administrator and the CTO.
      */
      if (user?.can('USER_MANAGE') ?? false)
        _Entry(
          icon: Icons.badge_outlined,
          title: 'Employees',
          subtitle: 'The company directory',
          onTap: () => _open(context, const EmployeesScreen()),
        ),
      // Community groups: create them, put people in them.
      if (user?.canAny(const ['ORG_MANAGE', 'COMMUNITY_MANAGE']) ?? false)
        _Entry(
          icon: Icons.groups_outlined,
          title: 'Communities',
          subtitle: 'Groups, members and announcements',
          onTap: () => _open(context, const CommunitiesScreen()),
        ),
      // The security trail — who did what, and from where.
      if (user?.canAny(const ['USER_MANAGE', 'EMPLOYEE_MANAGE']) ?? false)
        _Entry(
          icon: Icons.history_rounded,
          title: 'Audit log',
          subtitle: 'The portal’s security trail',
          onTap: () => _open(context, const AuditScreen()),
        ),
      // Gated on the same permissions the web router uses, so a phone shows
      // exactly what that person can already reach in a browser — no more. The
      // server checks each of them again regardless; this only decides what is
      // worth putting on screen.
      if (user?.can('REPORT_VIEW') ?? false)
        _Entry(
          icon: Icons.insert_chart_outlined_rounded,
          title: 'Team reports',
          subtitle: 'Attendance, leave and payroll by month, year or range',
          onTap: () => _open(context, const TeamReportsScreen()),
        ),
      if ((user?.can('PAYROLL_RUN') ?? false) && modules.has('PAYROLL'))
        _Entry(
          icon: Icons.account_balance_wallet_outlined,
          title: 'Payroll',
          subtitle: 'Salaries across the company',
          onTap: () => _open(context, const PayrollScreen()),
        ),
      if ((user?.can('PAYROLL_RUN') ?? false) && modules.has('PAYROLL'))
        _Entry(
          icon: Icons.play_circle_outline_rounded,
          title: 'Payroll runs',
          subtitle: 'Generate, confirm and approve monthly payroll',
          onTap: () => _open(context, const PayrollRunsScreen()),
        ),
      if ((user?.can('PAYROLL_RUN') ?? false) && modules.has('PAYROLL'))
        _Entry(
          icon: Icons.mark_email_read_outlined,
          title: 'Payslip requests',
          subtitle: 'Approve or reject employee payslip requests',
          onTap: () => _open(context, const PayrollRequestsScreen()),
        ),
      if ((user?.can('ORG_MANAGE') ?? false) && modules.has('LEAVE'))
        _Entry(
          icon: Icons.policy_outlined,
          title: 'Leave policies',
          subtitle: 'Allowances and the holiday calendar',
          onTap: () => _open(context, const LeavePoliciesScreen()),
        ),
      if (user?.can('USER_MANAGE') ?? false)
        _Entry(
          icon: Icons.how_to_reg_outlined,
          title: 'Onboarding',
          subtitle: 'New joiners and their checklists',
          onTap: () => _open(context, const OnboardingScreen()),
        ),
      if (modules.has('CALENDAR'))
        _Entry(
          icon: Icons.event_outlined,
          title: 'Calendar',
          subtitle: 'Holidays, birthdays and meetings',
          onTap: () => _open(context, const CalendarScreen()),
        ),
    ];

    return Scaffold(
      appBar: AppBar(title: const Text('More')),
      body: entries.isEmpty
          ? const _NothingHere()
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                // Staggered, so the hub assembles rather than appearing all at
                // once. Fast enough not to be waited on: the last row is in
                // place inside a third of a second.
                for (var i = 0; i < entries.length; i++)
                  entries[i]
                      .animate()
                      .fadeIn(delay: (i * 40).ms, duration: 220.ms)
                      .slideY(begin: 0.08, end: 0, curve: Curves.easeOutCubic),
              ],
            ),
    );
  }

  void _open(BuildContext context, Widget screen) {
    Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => screen));
  }
}

/// The hub with every module switched off.
///
/// Reachable, and worth saying out loud: an empty scroll view is the same shape
/// as a list that failed to load.
class _NothingHere extends StatelessWidget {
  const _NothingHere();

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.grid_view_rounded, size: 40, color: scheme.outline),
            const SizedBox(height: 14),
            Text(
              'Nothing here yet',
              style: Theme.of(context)
                  .textTheme
                  .titleSmall
                  ?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 6),
            Text(
              'None of these modules is switched on for your company.',
              textAlign: TextAlign.center,
              style: Theme.of(context)
                  .textTheme
                  .bodySmall
                  ?.copyWith(color: scheme.onSurfaceVariant),
            ),
          ],
        ),
      ),
    );
  }
}

class _Entry extends StatelessWidget {
  const _Entry({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Card(
        child: ListTile(
          onTap: onTap,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
          leading: Container(
            padding: const EdgeInsets.all(9),
            decoration: BoxDecoration(
              color: scheme.primary.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(icon, size: 20, color: scheme.primary),
          ),
          title: Text(
            title,
            style: const TextStyle(fontWeight: FontWeight.w600),
          ),
          subtitle: Text(subtitle),
          trailing: const Icon(Icons.chevron_right_rounded),
        ),
      ),
    );
  }
}

/// A list screen that handles loading, failure and emptiness the same way every
/// time, so five screens do not each invent their own.
class _ListScaffold<T> extends ConsumerWidget {
  const _ListScaffold({
    required this.title,
    required this.provider,
    required this.itemBuilder,
    required this.emptyIcon,
    required this.emptyTitle,
    this.emptyDescription,
    this.floatingActionButton,
    super.key,
  });

  final String title;
  final ProviderListenable<AsyncValue<List<T>>> provider;
  final Widget Function(BuildContext, T) itemBuilder;
  final IconData emptyIcon;
  final String emptyTitle;
  final String? emptyDescription;
  final Widget? floatingActionButton;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(provider);
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      floatingActionButton: floatingActionButton,
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(provider as ProviderOrFamily),
        child: async.when(
          loading: () => const LoadingList(),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              SizedBox(
                height: MediaQuery.sizeOf(context).height * 0.65,
                child: ErrorState(
                  message: e.toString(),
                  onRetry: () => ref.invalidate(provider as ProviderOrFamily),
                ),
              ),
            ],
          ),
          data: (items) => items.isEmpty
              ? ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: [
                    SizedBox(
                      height: MediaQuery.sizeOf(context).height * 0.65,
                      child: EmptyState(
                        icon: emptyIcon,
                        title: emptyTitle,
                        description: emptyDescription,
                      ),
                    ),
                  ],
                )
              : ListView.separated(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.all(16),
                  itemCount: items.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, i) => itemBuilder(context, items[i]),
                ),
        ),
      ),
    );
  }
}

class PayslipsScreen extends ConsumerWidget {
  const PayslipsScreen({super.key});

  /// Fetch the PDF and hand it to whatever the phone opens PDFs with.
  ///
  /// `view` and `download` are the same request; the difference is only what
  /// is said afterwards. The website offers both because a browser can show a
  /// PDF inline, and a phone opens it in a reader either way -- so the two
  /// buttons are kept, matching the site, rather than pretending one of them
  /// does something different.
  static Future<void> _openPdf(
    BuildContext context,
    WidgetRef ref,
    Payslip p, {
    required bool save,
  }) async {
    final messenger = ScaffoldMessenger.of(context);
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(
        content: Text(save ? 'Downloading payslip…' : 'Opening payslip…'),
        duration: const Duration(seconds: 2),
      ));
    try {
      final dio = ref.read(apiClientProvider).raw;
      final res = await dio.get<List<int>>(
        '${AppConfig.apiBaseUrl}/payroll/payslip/${p.id}/pdf',
        options: Options(responseType: ResponseType.bytes),
      );
      final dir = save
          ? await getApplicationDocumentsDirectory()
          : await getTemporaryDirectory();
      // Named by period rather than id, because the file ends up in a folder
      // with other downloads and "payslip-412.pdf" tells nobody anything.
      final safe = p.periodLabel.replaceAll(RegExp(r'[^A-Za-z0-9]+'), '-');
      final file = File('${dir.path}/Payslip-$safe.pdf');
      await file.writeAsBytes(res.data ?? const []);

      final opened = await OpenFilex.open(file.path);
      if (opened.type != ResultType.done) {
        messenger
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(
            content: Text('Saved to ${file.path}'),
            duration: const Duration(seconds: 5),
          ));
      } else if (save) {
        messenger
          ..hideCurrentSnackBar()
          ..showSnackBar(const SnackBar(content: Text('Payslip downloaded')));
      }
    } catch (e) {
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(
          content: Text('Could not open that payslip. Try again.'),
        ));
    }
  }

  /// Email a payslip to the address on the employee's own profile.
  ///
  /// The address is never typed here: the server reads it from the record, so
  /// a payslip cannot be sent to the wrong person by a mistyped character.
  static Future<void> _email(
    BuildContext context,
    WidgetRef ref,
    Payslip p,
  ) async {
    final messenger = ScaffoldMessenger.of(context);
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(const SnackBar(content: Text('Sending…')));
    try {
      await ref.read(apiClientProvider).post('/payroll/payslip/${p.id}/email');
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(
          content: Text('Payslip emailed to you'),
        ));
    } catch (e) {
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(
          // The server says why -- email not configured, no address on file --
          // and that is more useful than a generic failure.
          content: Text('$e'),
          duration: const Duration(seconds: 5),
        ));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final money = NumberFormat.currency(
      locale: 'en_IN',
      symbol: '₹',
      decimalDigits: 0,
    );
    return _ListScaffold<Payslip>(
      title: 'Payslips',
      provider: payslipsProvider,
      emptyIcon: Icons.receipt_long_outlined,
      emptyTitle: 'No payslips yet',
      emptyDescription: 'They appear here once payroll has been run.',
      itemBuilder: (context, p) => Card(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ListTile(
              title: Text(
                p.periodLabel,
                style: const TextStyle(fontWeight: FontWeight.w600),
              ),
              subtitle: Text(
                p.payDate != null
                    ? 'Paid ${DateFormat('d MMM yyyy').format(p.payDate!)}'
                    : 'Not yet paid',
              ),
              trailing: Text(
                money.format(p.takeHome),
                style: const TextStyle(
                  fontWeight: FontWeight.w700,
                  fontFeatures: [FontFeature.tabularFigures()],
                ),
              ),
            ),
            /*
              The same three actions the website offers on a payslip row. They
              were missing entirely here, so the app could show that a payslip
              existed and give no way to read it.

              Wrapped rather than in a Row: at the smallest phone width three
              labelled buttons do not fit on one line, and Wrap drops the last
              one onto a second line instead of overflowing.
            */
            Padding(
              padding: const EdgeInsets.fromLTRB(8, 0, 8, 8),
              child: Wrap(
                alignment: WrapAlignment.end,
                spacing: 4,
                children: [
                  TextButton.icon(
                    onPressed: () => _openPdf(context, ref, p, save: false),
                    icon: const Icon(Icons.visibility_outlined, size: 18),
                    label: const Text('View'),
                  ),
                  TextButton.icon(
                    onPressed: () => _openPdf(context, ref, p, save: true),
                    icon: const Icon(Icons.download_rounded, size: 18),
                    label: const Text('Download'),
                  ),
                  TextButton.icon(
                    onPressed: () => _email(context, ref, p),
                    icon: const Icon(Icons.mail_outline_rounded, size: 18),
                    label: const Text('Email'),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class TasksScreen extends ConsumerWidget {
  const TasksScreen({super.key});

  /// Report progress, or mark the task done.
  ///
  /// The list reloads from the server afterwards rather than being edited in
  /// place — if the server rejected the change, the row must not keep showing
  /// the new figure.
  Future<void> _updateProgress(
    BuildContext context,
    WidgetRef ref,
    TaskItem task,
  ) async {
    var value = (task.progress ?? 0).toDouble();

    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (sheetContext) => StatefulBuilder(
        builder: (builderContext, setSheetState) => Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                task.title,
                style: Theme.of(
                  builderContext,
                ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 20),
              Text('${value.round()}% done', textAlign: TextAlign.center),
              Slider(
                value: value,
                max: 100,
                divisions: 20,
                label: '${value.round()}%',
                onChanged: (v) => setSheetState(() => value = v),
              ),
              const SizedBox(height: 8),
              FilledButton(
                onPressed: () => Navigator.pop(sheetContext, true),
                child: const Text('Save progress'),
              ),
              const SizedBox(height: 8),
              OutlinedButton(
                onPressed: () => Navigator.pop(sheetContext, false),
                child: const Text('Cancel'),
              ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );

    if (saved != true || !context.mounted) return;

    try {
      final repo = ref.read(workRepositoryProvider);
      final percent = value.round();
      // 100% means finished; the server has its own endpoint for that.
      if (percent >= 100) {
        await repo.completeTask(task.id);
      } else {
        await repo.updateTaskProgress(task.id, percent);
      }
      ref.invalidate(tasksProvider);
      if (!context.mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Progress saved')));
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(e.toString()),
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return _ListScaffold<TaskItem>(
      title: 'Tasks',
      provider: tasksProvider,
      emptyIcon: Icons.checklist_rounded,
      emptyTitle: 'Nothing assigned',
      emptyDescription: 'Tasks given to you will show up here.',
      itemBuilder: (context, t) {
        final scheme = Theme.of(context).colorScheme;
        final colour = t.isDone
            ? AppTheme.success(context)
            : t.isOverdue
            ? AppTheme.danger(context)
            : scheme.primary;
        return Card(
          child: InkWell(
            borderRadius: BorderRadius.circular(14),
            // Finished tasks are not editable — the server would refuse anyway.
            onTap: t.isDone ? null : () => _updateProgress(context, ref, t),
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 4,
                    height: 42,
                    decoration: BoxDecoration(
                      color: colour,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          t.title,
                          style: const TextStyle(fontWeight: FontWeight.w600),
                        ),
                        if (t.dueDate != null) ...[
                          const SizedBox(height: 3),
                          Text(
                            '${t.isOverdue ? 'Overdue · ' : 'Due '}'
                            '${DateFormat('d MMM').format(t.dueDate!)}',
                            style: TextStyle(
                              fontSize: 12,
                              color: t.isOverdue
                                  ? AppTheme.danger(context)
                                  : scheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                        if (t.progress != null) ...[
                          const SizedBox(height: 8),
                          ClipRRect(
                            borderRadius: BorderRadius.circular(4),
                            child: LinearProgressIndicator(
                              value: (t.progress! / 100).clamp(0.0, 1.0),
                              minHeight: 5,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(width: 10),
                  Text(
                    t.status,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: colour,
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class TicketsScreen extends ConsumerWidget {
  const TicketsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return _ListScaffold<Ticket>(
      title: 'Supports',
      provider: ticketsProvider,
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final raised = await showModalBottomSheet<bool>(
            context: context,
            isScrollControlled: true,
            useSafeArea: true,
            builder: (_) => const RaiseTicketSheet(),
          );
          if (raised == true) ref.invalidate(ticketsProvider);
        },
        icon: const Icon(Icons.add_rounded),
        label: const Text('New ticket'),
      ),
      emptyIcon: Icons.support_agent_rounded,
      emptyTitle: 'No tickets',
      emptyDescription: 'Anything you raise will be tracked here.',
      itemBuilder: (context, t) {
        final colour = t.isClosed
            ? AppTheme.success(context)
            : AppTheme.warning(context);
        return Card(
          child: ListTile(
            // The row was not tappable, so a ticket could be raised and never
            // read again from the phone -- whatever HR replied was only
            // visible on the website.
            onTap: () async {
              await showModalBottomSheet<void>(
                context: context,
                isScrollControlled: true,
                useSafeArea: true,
                builder: (_) => TicketDetailSheet(ticket: t),
              );
            },
            title: Text(
              t.displayTitle,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            subtitle: Text(
              [
                if (t.referenceCode != null) t.referenceCode!,
                if (t.createdAt != null)
                  DateFormat('d MMM').format(t.createdAt!),
                if (t.assignedToName != null) 'with ${t.assignedToName}',
              ].join(' · '),
            ),
            trailing: Container(
              padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
              decoration: BoxDecoration(
                color: colour.withValues(alpha: 0.14),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Text(
                t.status,
                style: TextStyle(
                  color: colour,
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

class AssetsScreen extends StatelessWidget {
  const AssetsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return _ListScaffold<AssetItem>(
      title: 'My assets',
      provider: assetsProvider,
      emptyIcon: Icons.inventory_2_outlined,
      emptyTitle: 'Nothing issued to you',
      emptyDescription: 'Equipment allocated to you appears here.',
      itemBuilder: (context, a) => Card(
        child: ListTile(
          leading: const Icon(Icons.devices_other_rounded),
          title: Text(
            a.displayName,
            style: const TextStyle(fontWeight: FontWeight.w600),
          ),
          subtitle: Text(
            [
              if (a.assetCode != null) a.assetCode!,
              if (a.category != null) a.category!,
              if (a.serialNumber != null) 'SN ${a.serialNumber}',
            ].join(' · '),
          ),
          trailing: a.allocatedAt == null
              ? null
              : Text(
                  DateFormat('d MMM yy').format(a.allocatedAt!),
                  style: const TextStyle(fontSize: 11),
                ),
        ),
      ),
    );
  }
}

class ClaimsScreen extends ConsumerWidget {
  const ClaimsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final money = NumberFormat.currency(
      locale: 'en_IN',
      symbol: '₹',
      decimalDigits: 0,
    );
    return _ListScaffold<ExpenseClaim>(
      title: 'Claims',
      provider: claimsProvider,
      emptyIcon: Icons.receipt_rounded,
      emptyTitle: 'No claims',
      emptyDescription: 'Travel and expense claims appear here.',
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final submitted = await showModalBottomSheet<bool>(
            context: context,
            isScrollControlled: true,
            useSafeArea: true,
            builder: (_) => const SubmitClaimSheet(),
          );
          // Only refetch when something was actually created — a dismissed
          // sheet should not cost a request.
          if (submitted == true) ref.invalidate(claimsProvider);
        },
        icon: const Icon(Icons.add_rounded),
        label: const Text('Claim'),
      ),
      itemBuilder: (context, c) => Card(
        child: ListTile(
          title: Text(
            [
              c.location,
              c.category,
            ].where((v) => v != null && v.isNotEmpty).join(' · '),
            style: const TextStyle(fontWeight: FontWeight.w600),
          ),
          subtitle: Text(
            [
              if (c.date != null) DateFormat('d MMM yyyy').format(c.date!),
              if (c.totalKm != null) '${c.totalKm} km',
              c.status,
            ].join(' · '),
          ),
          trailing: c.totalAmount == null
              ? null
              : Text(
                  money.format(c.totalAmount),
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontFeatures: [FontFeature.tabularFigures()],
                  ),
                ),
        ),
      ),
    );
  }
}
