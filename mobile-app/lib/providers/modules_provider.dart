import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/branding/branding.dart';
import 'app_providers.dart';

/// Which modules this company has switched on, and how it wants to look.
///
/// Both arrive from `GET /api/my-modules` — one request, because both are needed
/// before the first screen can be drawn and a second would mean the colour
/// arriving after the page, as a flash.
///
/// This is the mobile half of what the web client does. Without it the app
/// showed every module to everybody: switching Chat off in the admin screen hid
/// it in the browser and changed nothing on the phone, which is worse than not
/// having the setting at all — somebody believes a module is off when it is
/// still in a colleague's pocket.
class ModuleSettings {
  const ModuleSettings({
    required this.enabled,
    required this.configured,
    this.branding,
  });

  final Set<String> enabled;

  /// Whether this company has ever set its modules up.
  ///
  /// The distinction matters: with no rows at all, hiding everything would empty
  /// the app for a company that simply never opened the module screen. Absent
  /// means show everything; present-and-off means hide.
  final bool configured;

  final BrandingDoc? branding;

  /// Nothing known yet. Everything shows — see [has].
  static const unknown = ModuleSettings(enabled: {}, configured: false);

  /// Whether a module should appear.
  ///
  /// Unknown counts as on, deliberately, and in two cases: before the request
  /// has answered, and for a company that has configured nothing. Both are
  /// "we do not know", and the cost of guessing wrong in the other direction is
  /// an app with no navigation in it.
  bool has(String code) {
    if (!configured) return true;
    return enabled.contains(code.toUpperCase());
  }

  static ModuleSettings fromJson(dynamic raw) {
    if (raw is! Map) return unknown;

    final list = raw['enabled'];
    final codes = <String>{};
    if (list is List) {
      for (final item in list) {
        final code = item?.toString().trim().toUpperCase();
        if (code != null && code.isNotEmpty) codes.add(code);
      }
    }

    dynamic brandingRaw = raw['branding'];
    if (brandingRaw is String) {
      // Stored as free text, so it arrives as a string that still has to be
      // decoded. A malformed one leaves branding null rather than throwing —
      // a bad colour must not stop the app from loading.
      brandingRaw = _tryDecode(brandingRaw);
    }

    return ModuleSettings(
      enabled: codes,
      configured: raw['configured'] == true,
      branding: BrandingDoc.parse(brandingRaw),
    );
  }

  static dynamic _tryDecode(String raw) {
    if (raw.trim().isEmpty) return null;
    try {
      return jsonDecode(raw);
    } catch (_) {
      return null;
    }
  }
}

/// The company's module settings, refreshed whenever the session changes.
///
/// A failure leaves the previous answer in place rather than emptying the app:
/// losing signal for a moment is not the same as a module being switched off.
final FutureProvider<ModuleSettings> moduleSettingsProvider =
    FutureProvider<ModuleSettings>((ref) async {
  // Rebuilds on sign-in and sign-out, so a second person signing in on the same
  // phone does not inherit the first one's modules.
  final user = ref.watch(currentUserProvider);
  if (user == null) return ModuleSettings.unknown;

  try {
    final data = await ref.watch(apiClientProvider).get('/my-modules');
    return ModuleSettings.fromJson(data);
  } catch (_) {
    // Show everything rather than nothing. An app that hides half its navigation
    // because one request timed out looks broken in a way nobody can diagnose.
    return ModuleSettings.unknown;
  }
});

/// The settings as a plain value, with "not answered yet" folded into "unknown".
///
/// Screens want to ask `has('LEAVE')` in a build method; making each of them
/// unwrap an AsyncValue first would put a spinner in front of navigation that
/// has a perfectly good default.
final Provider<ModuleSettings> modulesProvider = Provider<ModuleSettings>((ref) {
  return ref
      .watch(moduleSettingsProvider)
      .maybeWhen(data: (value) => value, orElse: () => ModuleSettings.unknown);
});

/// The look for this person, on this screen.
final Provider<ResolvedBranding> brandingProvider = Provider<ResolvedBranding>((
  ref,
) {
  final doc = ref.watch(modulesProvider).branding;
  final roles = ref.watch(currentUserProvider)?.roles ?? const <String>[];
  return resolveBranding(doc, roles);
});
