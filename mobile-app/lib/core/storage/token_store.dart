import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// The access and refresh tokens, and the cached profile that goes with them.
///
/// Kept in the platform keystore (Android) / keychain (iOS), not in shared
/// preferences: a refresh token is a long-lived credential and preferences are
/// readable on a rooted device.
///
/// The tokens are held in memory as well, because every request reads them and
/// a keystore round trip per request is slow enough to notice.
class TokenStore {
  TokenStore({FlutterSecureStorage? storage})
    : _storage =
          storage ??
          const FlutterSecureStorage(
            aOptions: AndroidOptions(encryptedSharedPreferences: true),
            iOptions: IOSOptions(
              accessibility: KeychainAccessibility.first_unlock,
            ),
          );

  final FlutterSecureStorage _storage;

  static const _kAccess = 'hrp.access';
  static const _kRefresh = 'hrp.refresh';
  static const _kUser = 'hrp.user';

  String? _access;
  String? _refresh;
  Map<String, dynamic>? _user;
  bool _loaded = false;

  String? get accessToken => _access;
  String? get refreshToken => _refresh;
  Map<String, dynamic>? get cachedUser => _user;

  /// A cached profile is a convenience, never proof of a session — the token is
  /// what decides. The web client once treated a leftover cached user as being
  /// signed in and showed an empty portal with no token behind it.
  bool get hasSession => _access != null && _access!.isNotEmpty;

  /// Read once at startup. Safe to call again; it only does work the first time.
  Future<void> load() async {
    if (_loaded) return;
    _access = await _storage.read(key: _kAccess);
    _refresh = await _storage.read(key: _kRefresh);
    final raw = await _storage.read(key: _kUser);
    if (raw != null) {
      try {
        _user = jsonDecode(raw) as Map<String, dynamic>;
      } catch (_) {
        // Unreadable cache is not worth failing startup over.
        _user = null;
      }
    }
    _loaded = true;
  }

  Future<void> saveTokens({required String access, String? refresh}) async {
    _access = access;
    await _storage.write(key: _kAccess, value: access);
    if (refresh != null) {
      _refresh = refresh;
      await _storage.write(key: _kRefresh, value: refresh);
    }
  }

  Future<void> saveUser(Map<String, dynamic> user) async {
    _user = user;
    await _storage.write(key: _kUser, value: jsonEncode(user));
  }

  /// Wipe everything. Called on sign-out, and whenever the server rejects the
  /// session — leaving a half-cleared state is how phantom sessions happen.
  Future<void> clear() async {
    _access = null;
    _refresh = null;
    _user = null;
    await Future.wait([
      _storage.delete(key: _kAccess),
      _storage.delete(key: _kRefresh),
      _storage.delete(key: _kUser),
    ]);
  }
}
