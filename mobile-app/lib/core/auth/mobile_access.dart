import '../../models/auth_user.dart';

/// Who this app is for.
///
/// All roles — employees, team leads, HR, CTO, and company admins — may use
/// the mobile app. Only platform-level roles (BOARD_ADMIN) and the technical
/// admin panel (TECHNICAL_ADMIN) are gated, as those screens are too heavy
/// for a phone.
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

  /// Roles turned away from the mobile app.
  ///
  /// The mobile app now carries every module the web does — attendance,
  /// chat, calls, approvals, payroll, reports — so company-level admins
  /// (SUPER_ADMIN / COMPANY_ADMIN) including the CTO are allowed through.
  /// Only platform-level roles (BOARD_ADMIN) and the technical admin panel
  /// (TECHNICAL_ADMIN) remain gated, as those pages are too heavy for a phone.
  static const Set<String> blockedRoles = {
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
  /// Platform-level administrators and the technical admin panel are too
  /// heavy for a phone. Their account is fine — please use the web.
  static const String refusal =
      'This app does not support platform administrator or technical admin '
      'accounts.\n\n'
      'Your account is valid — please sign in on the web portal.';
}
