import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/network/api_client.dart';
import '../core/storage/token_store.dart';
import '../models/auth_user.dart';
import '../repositories/auth_repository.dart';
import '../repositories/work_repository.dart';

/// Wiring. Everything is constructed once here and read through providers, so
/// no widget builds its own client and there is exactly one token store.

/// Announces that the session ended, without the announcer needing to know who
/// is listening.
///
/// This exists to break a cycle. The client needs to say "the session is gone";
/// the auth notifier needs to react. Having the client reach for the auth
/// provider directly made the three depend on each other in a ring that Riverpod
/// cannot resolve. A one-way signal leaves the client knowing nothing about
/// auth, and auth listening for an event.
class SessionSignal {
  final StreamController<void> _controller = StreamController<void>.broadcast();

  Stream<void> get onExpired => _controller.stream;

  void expired() {
    if (!_controller.isClosed) _controller.add(null);
  }

  void dispose() => _controller.close();
}

final Provider<SessionSignal> sessionSignalProvider = Provider<SessionSignal>((
  ref,
) {
  final signal = SessionSignal();
  ref.onDispose(signal.dispose);
  return signal;
});

final Provider<TokenStore> tokenStoreProvider = Provider<TokenStore>(
  (ref) => TokenStore(),
);

final Provider<ApiClient> apiClientProvider = Provider<ApiClient>((ref) {
  final signal = ref.watch(sessionSignalProvider);
  return ApiClient(
    tokens: ref.watch(tokenStoreProvider),
    onSessionExpired: signal.expired,
  );
});

final Provider<AuthRepository> authRepositoryProvider =
    Provider<AuthRepository>(
      (ref) => AuthRepository(
        api: ref.watch(apiClientProvider),
        tokens: ref.watch(tokenStoreProvider),
      ),
    );

final Provider<WorkRepository> workRepositoryProvider =
    Provider<WorkRepository>(
      (ref) => WorkRepository(ref.watch(apiClientProvider)),
    );

/// Where the session is, as far as the UI is concerned.
enum AuthStatus { checking, signedIn, signedOut }

class AuthState {
  const AuthState({required this.status, this.user, this.error});

  final AuthStatus status;
  final AuthUser? user;
  final String? error;

  bool get isSignedIn => status == AuthStatus.signedIn && user != null;
}

class AuthNotifier extends StateNotifier<AuthState> {
  AuthNotifier(this._repo, SessionSignal signal)
    : super(const AuthState(status: AuthStatus.checking)) {
    // The client could not renew the token. Whoever was signed in is not any more.
    _subscription = signal.onExpired.listen((_) => _forceSignOut());
  }

  final AuthRepository _repo;
  late final StreamSubscription<void> _subscription;

  @override
  void dispose() {
    _subscription.cancel();
    super.dispose();
  }

  /// Called once at startup. A token the server rejects ends the session here,
  /// before any screen is drawn — which is what stops the app showing a
  /// signed-in shell with nothing behind it.
  Future<void> restore() async {
    final user = await _repo.restore();
    if (!mounted) return;
    state = user == null
        ? const AuthState(status: AuthStatus.signedOut)
        : AuthState(status: AuthStatus.signedIn, user: user);
  }

  Future<bool> signIn(String username, String password) async {
    state = const AuthState(status: AuthStatus.checking);
    try {
      final user = await _repo.login(username: username, password: password);
      if (!mounted) return false;
      state = AuthState(status: AuthStatus.signedIn, user: user);
      return true;
    } catch (e) {
      if (!mounted) return false;
      state = AuthState(status: AuthStatus.signedOut, error: e.toString());
      return false;
    }
  }

  Future<void> signOut() async {
    await _repo.logout();
    if (!mounted) return;
    state = const AuthState(status: AuthStatus.signedOut);
  }

  void _forceSignOut() {
    if (!mounted) return;
    state = const AuthState(
      status: AuthStatus.signedOut,
      error: 'Your session has ended. Please sign in again.',
    );
  }
}

final StateNotifierProvider<AuthNotifier, AuthState> authProvider =
    StateNotifierProvider<AuthNotifier, AuthState>(
      (ref) => AuthNotifier(
        ref.watch(authRepositoryProvider),
        ref.watch(sessionSignalProvider),
      ),
    );

/// The signed-in person, or null. Most screens want this rather than the state.
final Provider<AuthUser?> currentUserProvider = Provider<AuthUser?>(
  (ref) => ref.watch(authProvider).user,
);
