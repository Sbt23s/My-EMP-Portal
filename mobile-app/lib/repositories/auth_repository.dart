import '../core/auth/mobile_access.dart';
import '../core/error/failures.dart';
import '../core/network/api_client.dart';
import '../core/storage/token_store.dart';
import '../models/auth_user.dart';

/// Signing in, staying signed in, and signing out.
class AuthRepository {
  AuthRepository({required ApiClient api, required TokenStore tokens})
    : _api = api,
      _tokens = tokens;

  final ApiClient _api;
  final TokenStore _tokens;

  /// POST /auth/login -> { tokens: { accessToken, refreshToken }, user: {...} }
  Future<AuthUser> login({
    required String username,
    required String password,
  }) async {
    final data = await _api.post(
      '/auth/login',
      body: {'username': username.trim(), 'password': password},
    );

    if (data is! Map<String, dynamic>) throw const ParseFailure();

    final tokens = data['tokens'];
    final access = tokens is Map<String, dynamic>
        ? tokens['accessToken'] as String?
        : null;
    if (access == null || access.isEmpty) {
      throw const AuthFailure(
        'Signed in but no token was issued. Contact support.',
      );
    }

    await _tokens.saveTokens(
      access: access,
      refresh: tokens is Map<String, dynamic>
          ? tokens['refreshToken'] as String?
          : null,
    );

    final userJson = data['user'];
    if (userJson is! Map<String, dynamic>) throw const ParseFailure();

    final user = AuthUser.fromJson(userJson);

    /*
     * Administrators sign in on the web.
     *
     * Checked after the password, never before: refusing on the username alone
     * would tell an unauthenticated stranger which accounts are administrators.
     *
     * The tokens are thrown away rather than kept. Saving them first and
     * refusing afterwards would leave a valid session on the phone that the next
     * launch would restore straight past this check.
     */
    if (!MobileAccess.allows(user)) {
      await _tokens.clear();
      throw const AuthFailure(MobileAccess.refusal);
    }

    await _tokens.saveUser(userJson);
    return user;
  }

  /// Who the token belongs to, asked fresh.
  ///
  /// A rejection ends the session. A network failure does not — a tunnel should
  /// not sign someone out mid-task, so the cached profile stands until the
  /// server actually says no.
  Future<AuthUser?> restore() async {
    await _tokens.load();
    if (!_tokens.hasSession) {
      // No token means no session, whatever is left in the cache.
      await _tokens.clear();
      return null;
    }

    try {
      final data = await _api.get('/users/me');
      if (data is Map<String, dynamic>) {
        final user = AuthUser.fromJson(data);
        // Same gate as sign-in. Someone promoted to administrator while signed
        // in on their phone, or holding a token from a build that predates this
        // rule, would otherwise be restored straight past it.
        if (!MobileAccess.allows(user)) {
          await _tokens.clear();
          return null;
        }
        await _tokens.saveUser(data);
        return user;
      }
    } on AuthFailure {
      await _tokens.clear();
      return null;
    } on ForbiddenFailure {
      await _tokens.clear();
      return null;
    } on Failure {
      // Unreachable, not rejected: keep going with what we have.
    }

    final cached = _tokens.cachedUser;
    if (cached == null) return null;
    final user = AuthUser.fromJson(cached);
    if (!MobileAccess.allows(user)) {
      await _tokens.clear();
      return null;
    }
    return user;
  }

  /// Tell the server, then forget locally. The local clear happens either way —
  /// a failed call must not leave someone apparently signed in.
  Future<void> logout() async {
    final refresh = _tokens.refreshToken;
    if (refresh != null && refresh.isNotEmpty) {
      try {
        await _api.post('/auth/logout', body: {'refreshToken': refresh});
      } on Failure {
        // Best effort.
      }
    }
    await _tokens.clear();
  }
}
