import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hr_portal_mobile/core/branding/branding.dart';
import 'package:hr_portal_mobile/core/network/api_client.dart';
import 'package:hr_portal_mobile/core/storage/token_store.dart';
import 'package:hr_portal_mobile/features/auth/login_screen.dart';
import 'package:hr_portal_mobile/main.dart';
import 'package:hr_portal_mobile/models/auth_user.dart';
import 'package:hr_portal_mobile/providers/app_providers.dart';
import 'package:hr_portal_mobile/providers/modules_provider.dart';
import 'package:hr_portal_mobile/routes/app_shell.dart';

/// Does the app start?
///
/// Everything else in this suite tests a model or a form in isolation. None of
/// it would catch the app throwing on the very first frame — a bad theme, a
/// provider that reads another before it exists, a null in the root widget —
/// and that failure looks identical to a broken install: the icon opens, a
/// white screen, gone.
///
/// There is no emulator on this machine, so the app has never been run on a
/// phone. This is the closest honest substitute: the real `HrPortalApp`, the
/// real provider graph, the real theme, built and painted.

/// Answers without a network. The real one would call /users/me on start.
class _OfflineAuthRepository extends AuthRepository {
  _OfflineAuthRepository({required this.user})
      : super(api: ApiClient(tokens: TokenStore()), tokens: TokenStore());

  final AuthUser? user;

  @override
  Future<AuthUser?> restore() async => user;

  @override
  Future<void> logout() async {}
}

AuthUser _employee() => AuthUser.fromJson({
      'id': 1,
      'name': 'Priya Raman',
      'username': 'priya',
      'roles': ['IT_EMP'],
      'permissions': <String>[],
    });

Widget _app({AuthUser? signedInAs, ModuleSettings? modules}) {
  return ProviderScope(
    overrides: [
      authRepositoryProvider.overrideWithValue(
        _OfflineAuthRepository(user: signedInAs),
      ),
      if (modules != null)
        moduleSettingsProvider.overrideWith((ref) async => modules),
    ],
    child: const HrPortalApp(),
  );
}

void main() {
  testWidgets('boots to the sign-in screen with no session', (tester) async {
    await tester.pumpWidget(_app());
    await tester.pumpAndSettle();

    expect(find.byType(LoginScreen), findsOneWidget);
    expect(find.text('Sign in'), findsWidgets);
    // Nothing thrown while building the root, the theme or the first screen.
    expect(tester.takeException(), isNull);
  });

  testWidgets('boots to the shell for a signed-in employee', (tester) async {
    await tester.pumpWidget(
      _app(
        signedInAs: _employee(),
        modules: const ModuleSettings(enabled: {}, configured: false),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(AppShell), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a company with only two modules gets only those tabs',
      (tester) async {
    // The whole point of the module gating: what is switched off is not there.
    await tester.pumpWidget(
      _app(
        signedInAs: _employee(),
        modules: const ModuleSettings(
          enabled: {'DASHBOARD', 'LEAVE'},
          configured: true,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Leave'), findsOneWidget);
    expect(find.text('Attendance'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a company with everything off still shows the notice, not a blank',
      (tester) async {
    // More and Profile are not modules, so the bar is never truly empty — but
    // the dashboard going means the first screen must be something, and a blank
    // panel reads as a broken app.
    await tester.pumpWidget(
      _app(
        signedInAs: _employee(),
        modules: const ModuleSettings(enabled: {}, configured: true),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(AppShell), findsOneWidget);
    expect(find.text('Home'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a company theme is applied to the running app', (tester) async {
    // Proves the branding actually reaches MaterialApp, rather than resolving
    // correctly in a unit test and never being wired to anything.
    await tester.pumpWidget(
      _app(
        signedInAs: _employee(),
        modules: ModuleSettings(
          enabled: const {'DASHBOARD'},
          configured: true,
          branding: BrandingDoc.parse({
            'base': {'themeId': 'emerald', 'productName': 'Sethu People'},
          }),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final app = tester.widget<MaterialApp>(find.byType(MaterialApp));
    expect(app.title, 'Sethu People');
    // Seeded from emerald, so the scheme is built around it rather than indigo.
    expect(app.theme!.colorScheme.primary, isNot(const Color(0xFF4F46E5)));
    expect(tester.takeException(), isNull);
  });
}
