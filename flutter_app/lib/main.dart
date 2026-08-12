import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'features/auth/login_screen.dart';
import 'providers/app_providers.dart';
import 'routes/app_shell.dart';
import 'themes/app_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ProviderScope(child: HrPortalApp()));
}

class HrPortalApp extends ConsumerStatefulWidget {
  const HrPortalApp({super.key});

  @override
  ConsumerState<HrPortalApp> createState() => _HrPortalAppState();
}

class _HrPortalAppState extends ConsumerState<HrPortalApp> {
  @override
  void initState() {
    super.initState();
    // Ask the server who this token belongs to before anything is drawn. A
    // token the server rejects ends the session here, so the app can never show
    // a signed-in shell with nothing behind it.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(authProvider.notifier).restore();
    });
  }

  @override
  Widget build(BuildContext context) {
    final auth = ref.watch(authProvider);

    return MaterialApp(
      title: 'Pixous HR Portal',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      // Follows the phone. Both themes are defined in full, so either is legible.
      themeMode: ThemeMode.system,
      home: switch (auth.status) {
        AuthStatus.checking when auth.user == null => const _Splash(),
        AuthStatus.signedIn => const AppShell(),
        _ => const LoginScreen(),
      },
    );
  }
}

class _Splash extends StatelessWidget {
  const _Splash();

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              height: 64,
              width: 64,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: scheme.primary,
                borderRadius: BorderRadius.circular(18),
              ),
              child: const Icon(
                Icons.apartment_rounded,
                color: Colors.white,
                size: 32,
              ),
            ),
            const SizedBox(height: 28),
            const SizedBox(
              height: 22,
              width: 22,
              child: CircularProgressIndicator(strokeWidth: 2.4),
            ),
          ],
        ),
      ),
    );
  }
}
