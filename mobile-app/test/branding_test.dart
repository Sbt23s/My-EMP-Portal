import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hr_portal_mobile/core/branding/branding.dart';
import 'package:hr_portal_mobile/providers/modules_provider.dart';

/// Branding and module gating both fail silently.
///
/// A wrong answer here does not throw — it renders a perfectly good screen in
/// the wrong colour, or hides a module that should be there, or shows one that
/// should not. There is nothing in a log to catch it, which is why these are
/// asserted rather than looked at.
void main() {
  group('resolveBranding', () {
    const doc = BrandingDoc(
      base: Look(themeId: 'teal', fontId: 'georgia'),
      roles: {'EMPLOYEE': Look(themeId: 'rose')},
      modules: {'LEAVE': Look(fontId: 'mono')},
      productName: 'Sethu People',
    );

    test('falls back to the app palette with no document', () {
      final look = resolveBranding(null, ['IT_EMP']);
      expect(look.theme.id, 'indigo');
      expect(look.font.id, 'system');
    });

    test('applies the company default where nothing overrides it', () {
      final look = resolveBranding(doc, ['IT_HR']);
      expect(look.theme.id, 'teal');
      expect(look.font.id, 'georgia');
    });

    test('a role override changes the colour and keeps the company font', () {
      // Resolved per property. Taking the override wholesale would drop the
      // company's font for a scope that only chose a colour.
      final look = resolveBranding(doc, ['IT_EMP']);
      expect(look.theme.id, 'rose');
      expect(look.font.id, 'georgia');
    });

    test('a module beats the role it is viewed by', () {
      final look = resolveBranding(doc, ['IT_EMP'], moduleCode: 'LEAVE');
      expect(look.font.id, 'mono');
      // The module said nothing about colour, so the role's still stands.
      expect(look.theme.id, 'rose');
    });

    test('an id that no longer exists resolves to the default, not to nothing', () {
      const stale = BrandingDoc(base: Look(themeId: 'gone', fontId: 'gone'));
      final look = resolveBranding(stale, ['IT_EMP']);
      expect(look.theme.id, 'indigo');
      expect(look.font.id, 'system');
    });
  });

  group('brandingRoleFor', () {
    test('maps the industry codes people actually hold', () {
      // Nobody is given "HR_MANAGER" — they hold IT_HR, CV_HR or IT_MGR.
      expect(brandingRoleFor(['IT_HR']), 'HR_MANAGER');
      expect(brandingRoleFor(['CV_SUP']), 'TEAM_LEAD');
      expect(brandingRoleFor(['IT_EMP']), 'EMPLOYEE');
      expect(brandingRoleFor(['SUPER_ADMIN']), 'COMPANY_ADMIN');
    });

    test('takes the most senior role when somebody holds several', () {
      expect(brandingRoleFor(['IT_EMP', 'COMPANY_ADMIN']), 'COMPANY_ADMIN');
      expect(brandingRoleFor(['IT_EMP', 'IT_TL']), 'TEAM_LEAD');
    });

    test('answers nothing for no roles rather than guessing', () {
      expect(brandingRoleFor([]), isNull);
    });
  });

  group('BrandingDoc.parse', () {
    test('returns null for absent or unreadable settings', () {
      expect(BrandingDoc.parse(null), isNull);
      expect(BrandingDoc.parse('not a map'), isNull);
    });

    test('reads a document written before roles and modules existed', () {
      final doc = BrandingDoc.parse({'themeId': 'teal', 'fontId': 'georgia'});
      expect(doc!.base.themeId, 'teal');
      expect(doc.roles, isEmpty);
    });

    test('treats blank words as absent so the standard wording shows', () {
      final doc = BrandingDoc.parse({
        'base': {'themeId': 'teal', 'productName': '   ', 'welcomeText': ''},
      });
      expect(doc!.productName, isNull);
      expect(doc.welcomeText, isNull);
    });
  });

  group('ModuleSettings', () {
    test('an unconfigured company shows everything', () {
      // The distinction that matters: with no rows at all, hiding every module
      // would empty the app for a company that never opened the module screen.
      final s = ModuleSettings.fromJson({'enabled': [], 'configured': false});
      expect(s.has('LEAVE'), isTrue);
      expect(s.has('ANYTHING'), isTrue);
    });

    test('a configured company shows only what it enabled', () {
      final s = ModuleSettings.fromJson({
        'enabled': ['LEAVE', 'attendance'],
        'configured': true,
      });
      expect(s.has('LEAVE'), isTrue);
      // Stored lower-case in places and upper-case in others.
      expect(s.has('ATTENDANCE'), isTrue);
      expect(s.has('CHAT'), isFalse);
    });

    test('nothing known yet counts as on, so navigation is never empty mid-load', () {
      expect(ModuleSettings.unknown.has('CHAT'), isTrue);
    });

    test('branding arrives as a JSON string and is decoded', () {
      final s = ModuleSettings.fromJson({
        'enabled': ['LEAVE'],
        'configured': true,
        'branding': '{"base":{"themeId":"amber"}}',
      });
      expect(s.branding, isNotNull);
      expect(resolveBranding(s.branding, []).theme.id, 'amber');
    });

    test('malformed branding leaves the app on its own palette', () {
      // A bad colour must not stop the app from loading.
      final s = ModuleSettings.fromJson({
        'enabled': <String>[],
        'configured': true,
        'branding': '{ broken',
      });
      expect(s.branding, isNull);
      expect(resolveBranding(s.branding, []).theme.id, 'indigo');
    });
  });

  group('theme catalogue', () {
    test('every id is unique — the id is the contract with the web client', () {
      final themeIds = kBrandThemes.map((t) => t.id).toSet();
      final fontIds = kBrandFonts.map((f) => f.id).toSet();
      expect(themeIds.length, kBrandThemes.length);
      expect(fontIds.length, kBrandFonts.length);
    });

    test('the catalogue matches the twenty the admin screen offers', () {
      // If the web adds a theme and this does not, somebody picks it in the
      // admin screen and it silently does nothing on the phone.
      expect(kBrandThemes.length, 20);
      expect(kBrandFonts.length, 20);
    });

    test('the default indigo is the portal accent, exactly', () {
      expect(kBrandThemes.first.accent, const Color(0xFF4F46E5));
    });
  });
}
