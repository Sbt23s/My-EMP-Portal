import '../../models/auth_user.dart';

/// Who this app is for.
///
/// Employees, team leads and HR. Administrators sign in on the web.
///
/// The reason is weight, not permission. An administrator's portal carries the
/// organisation dashboards, the directory, payroll runs, reports and the audit
/// trail — screens built for a wide window and a real connection, each pulling
/// lists measured in hundreds. Rendering that on a phone is slow enough to feel
/// broken, and the honest answer is that this app was not built for it.
///
/// Enforced in the client only, deliberately. The server is unchanged: an
/// administrator's credentials are still perfectly valid, still work in the
/// browser, and nothing about their account is altered. Adding a role check to
/// the login endpoint would have changed the web portal too, which is the one
/// thing this must not do.
///
/// So this is a door, not a lock — someone determined could talk to the API
/// directly, exactly as they always could. It is here to send a person to the
/// right product, not to keep anybody out of anything they are entitled to.
class MobileAccess {
  const MobileAccess._();

  /// Roles that run a company. These sign in on the web.
  ///
  /// Listed rather than inferred from `isCompanyAdmin` so the two cannot drift:
  /// this decides who is turned away at the door and wants to be read on its
  /// own, without following an alias into another file.
  static const Set<String> blockedRoles = {
    'SUPER_ADMIN',
    'COMPANY_ADMIN',
    'BOARD_ADMIN',
    'TECHNICAL_ADMIN',
    'CV_ADM',
    'IT_ADM',
  };

  /// Whether this person may use the mobile app.
  ///
  /// An account with no roles at all is allowed through. It is a broken account
  /// rather than an administrator, and it will find an app with nothing in it —
  /// which is a truer thing to show than "administrators use the web", a
  /// sentence that would send them looking for a browser they do not need.
  static bool allows(AuthUser user) {
    final held = user.roles.map((r) => r.toUpperCase());
    return !held.any(blockedRoles.contains);
  }

  /// What to tell somebody who is turned away.
  ///
  /// Names the product they should use and says their account is fine. Being
  /// refused at a login screen reads as "wrong password" unless the message
  /// says otherwise, and an administrator retyping a correct password is the
  /// failure this sentence exists to prevent.
  static const String refusal =
      'This app is for employees, team leads and HR.\n\n'
      'Your account is an administrator account and works normally — please '
      'sign in on the web portal, where the admin screens live.';
}
