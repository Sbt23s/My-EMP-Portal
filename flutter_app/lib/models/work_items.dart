/// Models for the rest of the employee's world: pay, tasks, tickets, claims,
/// assets and notifications.
///
/// All parsing is defensive. These endpoints return a lot of optional fields and
/// a null where a String was expected should never take down a list.
library;

class Payslip {
  const Payslip({
    required this.id,
    required this.payMonth,
    required this.payYear,
    this.grossSalary,
    this.totalDeductions,
    this.netPay,
    this.payDate,
    this.pdfPath,
  });

  final int id;
  final int payMonth;
  final int payYear;
  final num? grossSalary;
  final num? totalDeductions;
  final num? netPay;
  final DateTime? payDate;
  final String? pdfPath;

  static const _months = [
    '',
    'January',
    'February',
    'March',
    'April',
    'May',
    'June',
    'July',
    'August',
    'September',
    'October',
    'November',
    'December',
  ];

  String get periodLabel {
    final m = (payMonth >= 1 && payMonth <= 12) ? _months[payMonth] : '';
    return m.isEmpty ? '$payYear' : '$m $payYear';
  }

  /// The server sends gross and deductions; net is derived when absent.
  num get takeHome => netPay ?? ((grossSalary ?? 0) - (totalDeductions ?? 0));

  factory Payslip.fromJson(Map<String, dynamic> json) => Payslip(
    id: (json['id'] as num?)?.toInt() ?? 0,
    payMonth: (json['payMonth'] as num?)?.toInt() ?? 0,
    payYear: (json['payYear'] as num?)?.toInt() ?? 0,
    grossSalary: json['grossSalary'] as num?,
    totalDeductions: json['totalDeductions'] as num?,
    netPay: json['netPay'] as num? ?? json['netSalary'] as num?,
    payDate: DateTime.tryParse(json['payDate']?.toString() ?? ''),
    pdfPath: json['pdfPath']?.toString(),
  );
}

class TaskItem {
  const TaskItem({
    required this.id,
    required this.title,
    required this.status,
    this.description,
    this.priority,
    this.progress,
    this.dueDate,
    this.assignerName,
  });

  final int id;
  final String title;
  final String status;
  final String? description;
  final String? priority;
  final int? progress;
  final DateTime? dueDate;
  final String? assignerName;

  bool get isDone =>
      status.toUpperCase() == 'COMPLETED' || status.toUpperCase() == 'DONE';

  /// Past its date and not finished. Drives the red marker in the list.
  bool get isOverdue {
    final due = dueDate;
    if (due == null || isDone) return false;
    final today = DateTime.now();
    return due.isBefore(DateTime(today.year, today.month, today.day));
  }

  factory TaskItem.fromJson(Map<String, dynamic> json) => TaskItem(
    id: (json['id'] as num?)?.toInt() ?? 0,
    title: json['title']?.toString() ?? 'Untitled task',
    status: json['status']?.toString() ?? 'PENDING',
    description: json['description']?.toString(),
    priority: json['priority']?.toString(),
    progress: (json['progress'] as num?)?.toInt(),
    dueDate: DateTime.tryParse(json['dueDate']?.toString() ?? ''),
    assignerName: json['assignerName']?.toString(),
  );
}

class Ticket {
  const Ticket({
    required this.id,
    required this.status,
    this.subject,
    this.description,
    this.type,
    this.priority,
    this.referenceCode,
    this.createdAt,
    this.assignedToName,
  });

  final int id;
  final String status;
  final String? subject;
  final String? description;
  final String? type;
  final String? priority;
  final String? referenceCode;
  final DateTime? createdAt;
  final String? assignedToName;

  bool get isClosed =>
      status.toUpperCase() == 'CLOSED' || status.toUpperCase() == 'RESOLVED';

  String get displayTitle {
    final s = subject?.trim();
    if (s != null && s.isNotEmpty) return s;
    final d = description?.trim();
    if (d != null && d.isNotEmpty) {
      return d.length > 60 ? '${d.substring(0, 60)}…' : d;
    }
    return 'Ticket #$id';
  }

  factory Ticket.fromJson(Map<String, dynamic> json) => Ticket(
    id: (json['id'] as num?)?.toInt() ?? 0,
    status: json['status']?.toString() ?? 'OPEN',
    subject: json['subject']?.toString() ?? json['title']?.toString(),
    description: json['description']?.toString(),
    type: json['type']?.toString(),
    priority: json['priority']?.toString(),
    referenceCode: json['referenceCode']?.toString(),
    createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? ''),
    assignedToName: json['assignedToName']?.toString(),
  );
}

class ExpenseClaim {
  const ExpenseClaim({
    required this.id,
    required this.status,
    this.date,
    this.location,
    this.category,
    this.totalKm,
    this.totalAmount,
  });

  final int id;
  final String status;
  final DateTime? date;
  final String? location;
  final String? category;
  final int? totalKm;
  final num? totalAmount;

  factory ExpenseClaim.fromJson(Map<String, dynamic> json) => ExpenseClaim(
    id: (json['id'] as num?)?.toInt() ?? 0,
    status: json['status']?.toString() ?? 'PENDING',
    date: DateTime.tryParse(json['date']?.toString() ?? ''),
    location: json['location']?.toString(),
    category: json['category']?.toString(),
    totalKm: (json['totalKm'] as num?)?.toInt(),
    totalAmount: json['totalAmount'] as num?,
  );
}

class AssetItem {
  const AssetItem({
    required this.id,
    required this.status,
    this.assetCode,
    this.category,
    this.brand,
    this.model,
    this.serialNumber,
    this.allocatedAt,
    this.acknowledged,
  });

  final int id;
  final String status;
  final String? assetCode;
  final String? category;
  final String? brand;
  final String? model;
  final String? serialNumber;
  final DateTime? allocatedAt;
  final bool? acknowledged;

  String get displayName {
    final parts = [
      brand,
      model,
    ].where((p) => p != null && p.isNotEmpty).cast<String>();
    return parts.isEmpty ? (assetCode ?? 'Asset #$id') : parts.join(' ');
  }

  factory AssetItem.fromJson(Map<String, dynamic> json) => AssetItem(
    id: (json['id'] as num?)?.toInt() ?? 0,
    status: json['status']?.toString() ?? '',
    assetCode: json['assetCode']?.toString(),
    category: json['category']?.toString(),
    brand: json['brand']?.toString(),
    model: json['model']?.toString(),
    serialNumber: json['serialNumber']?.toString(),
    allocatedAt: DateTime.tryParse(json['allocatedAt']?.toString() ?? ''),
    acknowledged: json['acknowledged'] as bool?,
  );
}

class AppNotification {
  const AppNotification({
    required this.id,
    required this.title,
    required this.read,
    this.body,
    this.type,
    this.link,
    this.createdAt,
  });

  final int id;
  final String title;
  final bool read;
  final String? body;
  final String? type;
  final String? link;
  final DateTime? createdAt;

  factory AppNotification.fromJson(Map<String, dynamic> json) =>
      AppNotification(
        id: (json['id'] as num?)?.toInt() ?? 0,
        title: json['title']?.toString() ?? '',
        read: json['read'] as bool? ?? false,
        body: json['body']?.toString(),
        type: json['type']?.toString(),
        link: json['link']?.toString(),
        createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? ''),
      );
}

/// One entry on the company calendar.
///
/// Birthdays and anniversaries arrive with a null [id] — the server derives
/// them from employee records rather than storing a row, so there is nothing
/// to edit or delete.
class CalendarEvent {
  const CalendarEvent({
    required this.type,
    required this.title,
    this.id,
    this.description,
    this.date,
    this.endDate,
    this.startTime,
    this.endTime,
    this.location,
    this.audienceTeam,
  });

  final int? id;
  final String type;
  final String title;
  final String? description;
  final DateTime? date;
  final DateTime? endDate;
  final String? startTime;
  final String? endTime;
  final String? location;

  /// Null means the whole company is invited.
  final String? audienceTeam;

  factory CalendarEvent.fromJson(Map<String, dynamic> json) => CalendarEvent(
    id: (json['id'] as num?)?.toInt(),
    type: json['type']?.toString() ?? 'OTHER',
    title: json['title']?.toString() ?? '',
    description: json['description']?.toString(),
    date: DateTime.tryParse(json['date']?.toString() ?? ''),
    endDate: DateTime.tryParse(json['endDate']?.toString() ?? ''),
    startTime: json['startTime']?.toString(),
    endTime: json['endTime']?.toString(),
    location: json['location']?.toString(),
    audienceTeam: json['audienceTeam']?.toString(),
  );
}
