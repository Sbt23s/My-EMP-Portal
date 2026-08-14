import 'package:flutter/material.dart';

/// The company's chosen look, as the web client defines it.
///
/// A deliberate port of `web/src/lib/branding.ts`, kept in step with it by hand
/// — the two products read the same document out of the same column, and a
/// theme this file does not know about is one a person picks in the admin screen
/// and then cannot see on their phone, with nothing to say why.
///
/// The ids are the contract. Names and hex values may be corrected; an id may
/// not be renamed without renaming it on the web at the same time.

@immutable
class BrandTheme {
  const BrandTheme({
    required this.id,
    required this.name,
    required this.accent,
    required this.surface,
    required this.ink,
  });

  final String id;
  final String name;

  /// The one colour everything else is built around.
  final Color accent;
  final Color surface;
  final Color ink;
}

const List<BrandTheme> kBrandThemes = [
  BrandTheme(id: 'indigo', name: 'Indigo', accent: Color(0xFF4F46E5), surface: Color(0xFFFFFFFF), ink: Color(0xFF0F172A)),
  BrandTheme(id: 'royal', name: 'Royal Blue', accent: Color(0xFF2563EB), surface: Color(0xFFFFFFFF), ink: Color(0xFF0F172A)),
  BrandTheme(id: 'sky', name: 'Sky', accent: Color(0xFF0284C7), surface: Color(0xFFF8FAFC), ink: Color(0xFF0F172A)),
  BrandTheme(id: 'teal', name: 'Teal', accent: Color(0xFF0D9488), surface: Color(0xFFFFFFFF), ink: Color(0xFF0F172A)),
  BrandTheme(id: 'emerald', name: 'Emerald', accent: Color(0xFF059669), surface: Color(0xFFFFFFFF), ink: Color(0xFF0F172A)),
  BrandTheme(id: 'forest', name: 'Forest', accent: Color(0xFF15803D), surface: Color(0xFFF7FAF7), ink: Color(0xFF14261A)),
  BrandTheme(id: 'lime', name: 'Lime', accent: Color(0xFF65A30D), surface: Color(0xFFFCFDF7), ink: Color(0xFF1A2010)),
  BrandTheme(id: 'amber', name: 'Amber', accent: Color(0xFFD97706), surface: Color(0xFFFFFDF7), ink: Color(0xFF231607)),
  BrandTheme(id: 'orange', name: 'Orange', accent: Color(0xFFEA580C), surface: Color(0xFFFFFCF9), ink: Color(0xFF25130A)),
  BrandTheme(id: 'rose', name: 'Rose', accent: Color(0xFFE11D48), surface: Color(0xFFFFFAFB), ink: Color(0xFF25101A)),
  BrandTheme(id: 'crimson', name: 'Crimson', accent: Color(0xFFBE123C), surface: Color(0xFFFFFFFF), ink: Color(0xFF1F0A12)),
  BrandTheme(id: 'plum', name: 'Plum', accent: Color(0xFF9333EA), surface: Color(0xFFFDFAFF), ink: Color(0xFF1C1024)),
  BrandTheme(id: 'violet', name: 'Violet', accent: Color(0xFF7C3AED), surface: Color(0xFFFFFFFF), ink: Color(0xFF160E23)),
  BrandTheme(id: 'fuchsia', name: 'Fuchsia', accent: Color(0xFFC026D3), surface: Color(0xFFFFFAFE), ink: Color(0xFF230A26)),
  BrandTheme(id: 'slate', name: 'Slate', accent: Color(0xFF475569), surface: Color(0xFFF8FAFC), ink: Color(0xFF0F172A)),
  BrandTheme(id: 'graphite', name: 'Graphite', accent: Color(0xFF374151), surface: Color(0xFFFFFFFF), ink: Color(0xFF111827)),
  BrandTheme(id: 'midnight', name: 'Midnight', accent: Color(0xFF6366F1), surface: Color(0xFF0F172A), ink: Color(0xFFE2E8F0)),
  BrandTheme(id: 'carbon', name: 'Carbon', accent: Color(0xFF22D3EE), surface: Color(0xFF111827), ink: Color(0xFFE5E7EB)),
  BrandTheme(id: 'obsidian', name: 'Obsidian', accent: Color(0xFFA78BFA), surface: Color(0xFF18181B), ink: Color(0xFFE4E4E7)),
  BrandTheme(id: 'ink', name: 'Deep Ink', accent: Color(0xFF38BDF8), surface: Color(0xFF0B1120), ink: Color(0xFFDBEAFE)),
];

@immutable
class BrandFont {
  const BrandFont({
    required this.id,
    required this.name,
    required this.heading,
    required this.body,
  });

  final String id;
  final String name;

  /// A family name, or empty for "whatever the platform uses".
  ///
  /// Empty rather than naming a default: passing null to `fontFamily` is how you
  /// say "the system one" in Flutter, and spelling out "Roboto" would be wrong
  /// on iOS and wrong again on any Android skin that ships something else.
  final String heading;
  final String body;

  String? get headingFamily => heading.isEmpty ? null : heading;
  String? get bodyFamily => body.isEmpty ? null : body;
}

/// The twenty pairings the admin screen offers.
///
/// Nothing is downloaded here either. A family the device does not have falls
/// back to the platform default, which is the same bargain the web client makes
/// — and on Android most of these resolve to Roboto, so the difference between
/// several of them is smaller on a phone than in a browser. That is honest: the
/// choice still applies, it simply has less to work with.
const List<BrandFont> kBrandFonts = [
  BrandFont(id: 'system', name: 'System', heading: '', body: ''),
  BrandFont(id: 'grotesk', name: 'Grotesk', heading: 'Segoe UI', body: 'Segoe UI'),
  BrandFont(id: 'helvetica', name: 'Helvetica', heading: 'Helvetica', body: 'Helvetica'),
  BrandFont(id: 'arial', name: 'Arial', heading: 'Arial', body: 'Arial'),
  BrandFont(id: 'verdana', name: 'Verdana', heading: 'Verdana', body: 'Verdana'),
  BrandFont(id: 'tahoma', name: 'Tahoma', heading: 'Tahoma', body: 'Tahoma'),
  BrandFont(id: 'trebuchet', name: 'Trebuchet', heading: 'Trebuchet MS', body: 'Trebuchet MS'),
  BrandFont(id: 'calibri', name: 'Calibri', heading: 'Calibri', body: 'Calibri'),
  BrandFont(id: 'optima', name: 'Optima', heading: 'Optima', body: 'Candara'),
  BrandFont(id: 'georgia', name: 'Georgia', heading: 'Georgia', body: 'Georgia'),
  BrandFont(id: 'garamond', name: 'Garamond', heading: 'Garamond', body: 'Garamond'),
  BrandFont(id: 'cambria', name: 'Cambria', heading: 'Cambria', body: 'Cambria'),
  BrandFont(id: 'book', name: 'Bookman', heading: 'Bookman Old Style', body: 'Georgia'),
  BrandFont(id: 'palatino', name: 'Palatino', heading: 'Palatino', body: 'Palatino'),
  BrandFont(id: 'times', name: 'Times', heading: 'Times New Roman', body: 'Times New Roman'),
  BrandFont(id: 'serif-sans', name: 'Serif + Sans', heading: 'Georgia', body: ''),
  BrandFont(id: 'sans-serif', name: 'Sans + Serif', heading: 'Segoe UI', body: 'Georgia'),
  BrandFont(id: 'condensed', name: 'Condensed', heading: 'Arial Narrow', body: 'Arial'),
  BrandFont(id: 'mono-head', name: 'Mono Headings', heading: 'Consolas', body: ''),
  BrandFont(id: 'mono', name: 'Monospace', heading: 'Courier New', body: 'Courier New'),
];

/// What one scope can set. Both optional — an override may change only colour.
@immutable
class Look {
  const Look({this.themeId, this.fontId});

  final String? themeId;
  final String? fontId;

  static Look? fromJson(dynamic raw) {
    if (raw is! Map) return null;
    return Look(
      themeId: raw['themeId']?.toString(),
      fontId: raw['fontId']?.toString(),
    );
  }
}

/// The whole document: a company default, plus per-role and per-module overrides.
@immutable
class BrandingDoc {
  const BrandingDoc({
    this.base = const Look(),
    this.roles = const {},
    this.modules = const {},
    this.productName,
    this.welcomeText,
  });

  final Look base;
  final Map<String, Look> roles;
  final Map<String, Look> modules;
  final String? productName;
  final String? welcomeText;

  static Map<String, Look> _scopes(dynamic raw) {
    if (raw is! Map) return const {};
    final out = <String, Look>{};
    raw.forEach((key, value) {
      final look = Look.fromJson(value);
      if (look != null) out[key.toString()] = look;
    });
    return out;
  }

  /// Parses whatever the server stored, without letting bad data blank the app.
  ///
  /// Returns null for absent or unreadable settings — the ordinary case, since
  /// most companies have never opened the branding screen, and the app's own
  /// palette is the right answer for them.
  static BrandingDoc? parse(dynamic raw) {
    if (raw is! Map) return null;
    // A document written before roles and modules existed was the base object on
    // its own. Reading it as the base keeps those companies' colours.
    final baseRaw = raw['base'] is Map ? raw['base'] : raw;
    final base = Look.fromJson(baseRaw) ?? const Look();
    final name = (baseRaw is Map ? baseRaw['productName'] : null)?.toString().trim();
    final welcome = (baseRaw is Map ? baseRaw['welcomeText'] : null)?.toString().trim();

    return BrandingDoc(
      base: base,
      roles: _scopes(raw['roles']),
      modules: _scopes(raw['modules']),
      productName: (name == null || name.isEmpty) ? null : name,
      welcomeText: (welcome == null || welcome.isEmpty) ? null : welcome,
    );
  }
}

/// The four scopes the chooser offers, and the role codes that map onto each.
///
/// Nobody holds "HR_MANAGER" — they hold IT_HR, CV_HR or IT_MGR, depending on
/// the industry their company is in. Matching the chooser's four codes literally
/// would mean an override that applies to no one, which looks exactly like the
/// feature not working.
const Map<String, List<String>> kBrandingRoleAliases = {
  'COMPANY_ADMIN': ['COMPANY_ADMIN', 'SUPER_ADMIN', 'BOARD_ADMIN', 'CV_ADM', 'IT_ADM'],
  'HR_MANAGER': ['HR_MANAGER', 'IT_HR', 'CV_HR', 'IT_MGR'],
  'TEAM_LEAD': ['TEAM_LEAD', 'IT_TL', 'CV_SUP'],
  'EMPLOYEE': ['EMPLOYEE', 'IT_EMP', 'CV_EMP'],
};

/// Which of the four scopes this person falls into, most senior first.
///
/// Someone can hold more than one role. Reading them in this order means an
/// admin who is also on the employee list gets the admin look, which is the one
/// chosen for them deliberately.
String? brandingRoleFor(List<String> roles) {
  if (roles.isEmpty) return null;
  final held = roles.map((r) => r.toUpperCase()).toSet();
  for (final scope in ['COMPANY_ADMIN', 'HR_MANAGER', 'TEAM_LEAD', 'EMPLOYEE']) {
    if (kBrandingRoleAliases[scope]!.any(held.contains)) return scope;
  }
  return null;
}

/// A resolved look: exactly one theme and one font pairing.
@immutable
class ResolvedBranding {
  const ResolvedBranding({
    required this.theme,
    required this.font,
    this.productName,
    this.welcomeText,
  });

  final BrandTheme theme;
  final BrandFont font;
  final String? productName;
  final String? welcomeText;

  /// The app's own palette, for a company that has chosen nothing.
  ///
  /// Not `const` with a list index — indexing a const list is not a constant
  /// expression in Dart, so this names the defaults outright.
  static const fallback = ResolvedBranding(
    theme: BrandTheme(id: 'indigo', name: 'Indigo', accent: Color(0xFF4F46E5), surface: Color(0xFFFFFFFF), ink: Color(0xFF0F172A)),
    font: BrandFont(id: 'system', name: 'System', heading: '', body: ''),
  );
}

/// module override → role override → company default.
///
/// Resolved one property at a time rather than one object at a time: an override
/// that sets only a colour must keep the company's font, not fall back to the
/// platform default for everything it did not mention.
ResolvedBranding resolveBranding(
  BrandingDoc? doc,
  List<String> roles, {
  String? moduleCode,
}) {
  if (doc == null) return ResolvedBranding.fallback;

  final roleScope = brandingRoleFor(roles);
  final roleLook = roleScope == null ? null : doc.roles[roleScope];
  final moduleLook = moduleCode == null ? null : doc.modules[moduleCode];

  final themeId = moduleLook?.themeId ?? roleLook?.themeId ?? doc.base.themeId;
  final fontId = moduleLook?.fontId ?? roleLook?.fontId ?? doc.base.fontId;

  return ResolvedBranding(
    theme: kBrandThemes.firstWhere(
      (t) => t.id == themeId,
      orElse: () => kBrandThemes[0],
    ),
    font: kBrandFonts.firstWhere(
      (f) => f.id == fontId,
      orElse: () => kBrandFonts[0],
    ),
    productName: doc.productName,
    welcomeText: doc.welcomeText,
  );
}
