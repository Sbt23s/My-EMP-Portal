/// The signed-in person, as `LoginResponse.AuthUser` sends them.
///
/// Only `id`, `name`, `username` and the two lists are ever guaranteed; every
/// other field is optional on the server and optional here. Parsing is
/// defensive on purpose — a null where a String was expected should not take
/// down the login screen.
class AuthUser {
  const AuthUser({
    required this.id,
    required this.name,
    required this.username,
    required this.roles,
    required this.permissions,
    this.employeeCode,
    this.email,
    this.phone,
    this.industry,
    this.photoPath,
    this.companyName,
    this.designation,
    this.team,
  });

  final int id;
  final String name;
  final String username;
  final List<String> roles;
  final List<String> permissions;
  final String? employeeCode;
  final String? email;
  final String? phone;
  final String? industry;
  final String? photoPath;
  final String? companyName;
  final String? designation;
  final String? team;

  factory AuthUser.fromJson(Map<String, dynamic> json) => AuthUser(
    id: (json['id'] as num?)?.toInt() ?? 0,
    name: json['name']?.toString() ?? '',
    username: json['username']?.toString() ?? '',
    roles: _strings(json['roles']),
    permissions: _strings(json['permissions']),
    employeeCode: json['employeeCode']?.toString(),
    email: json['email']?.toString(),
    phone: json['phone']?.toString(),
    industry: json['industry']?.toString(),
    photoPath: json['photoPath']?.toString(),
    companyName: json['companyName']?.toString(),
    designation: json['designation']?.toString() ?? json['designationTitle']?.toString(),
    team: json['team']?.toString() ?? json['teamName']?.toString(),
  );

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'username': username,
    'roles': roles,
    'permissions': permissions,
    'employeeCode': employeeCode,
    'email': email,
    'phone': phone,
    'industry': industry,
    'photoPath': photoPath,
    'companyName': companyName,
    'designation': designation,
    'team': team,
  };

  /// Authorisation is decided by the server; these only decide what to draw. A
  /// hidden button is a courtesy, never the control itself.
  bool can(String permission) => permissions.contains(permission);
  bool canAny(List<String> any) => any.any(permissions.contains);

  /// Whether this person holds the role, treating COMPANY_ADMIN as SUPER_ADMIN.
  ///
  /// A tenant company has no SUPER_ADMIN of its own — its top administrator is
  /// COMPANY_ADMIN, the same job under the name the platform grew up with. They
  /// hold an identical permission set, and the web client aliases the two in one
  /// place for exactly this reason. Asked literally here, a company's own
  /// administrator would be treated as an ordinary employee by every screen that
  /// checks the role by name.
  bool hasRole(String role) {
    if (roles.contains(role)) return true;
    return role == 'SUPER_ADMIN' && roles.contains('COMPANY_ADMIN');
  }

  /// The person who runs this company, whichever of the two names they hold.
  bool get isCompanyAdmin =>
      hasRole('SUPER_ADMIN') || roles.contains('BOARD_ADMIN');

  /// One or two letters for the avatar, from the first and last name.
  String get initials {
    final parts = name
        .trim()
        .split(RegExp(r'\s+'))
        .where((p) => p.isNotEmpty)
        .toList();
    if (parts.isEmpty) return '?';
    final first = parts.first.substring(0, 1);
    if (parts.length == 1) return first.toUpperCase();
    return (first + parts.last.substring(0, 1)).toUpperCase();
  }

  static List<String> _strings(dynamic v) =>
      v is List ? v.map((e) => e.toString()).toList() : const <String>[];
}
