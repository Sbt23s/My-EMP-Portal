import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../config/app_config.dart';
import '../error/failures.dart';
import '../storage/token_store.dart';
import 'api_envelope.dart';

/// The one way this app talks to the backend.
///
/// Three things live here so no screen has to think about them:
///
///  * the access token goes on every request
///  * a 401 is answered by refreshing once and replaying the request, and if the
///    refresh fails the session is cleared and [onSessionExpired] fires so the
///    router can send the person to the login screen
///  * every Dio error becomes a [Failure] with a sentence worth showing
///
/// Concurrent 401s share a single refresh. Without that, six widgets loading at
/// once produce six refresh calls, five of which fail because the first already
/// rotated the token — and the session dies for no reason.
class ApiClient {
  ApiClient({required TokenStore tokens, Dio? dio, this.onSessionExpired})
    : _tokens = tokens,
      _dio = dio ?? Dio() {
    _dio.options
      ..baseUrl = AppConfig.apiBaseUrl
      ..connectTimeout = AppConfig.connectTimeout
      ..receiveTimeout = AppConfig.receiveTimeout
      ..headers['Content-Type'] = 'application/json'
      // Anything 4xx is handled below rather than thrown as a transport error,
      // so validation messages survive instead of becoming "request failed".
      ..validateStatus = (status) => status != null && status < 500;

    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          final token = _tokens.accessToken;
          if (token != null && token.isNotEmpty) {
            options.headers['Authorization'] = 'Bearer $token';
          }
          return handler.next(options);
        },
        onResponse: (response, handler) async {
          if (response.statusCode == 401 &&
              !_isAuthCall(response.requestOptions.path)) {
            final replayed = await _refreshAndReplay(response.requestOptions);
            if (replayed != null) return handler.resolve(replayed);
          }
          return handler.next(response);
        },
      ),
    );

    if (kDebugMode) {
      _dio.interceptors.add(
        LogInterceptor(
          request: false,
          requestHeader: false,
          requestBody: false,
          responseHeader: false,
          responseBody: false,
          logPrint: (o) => debugPrint('[api] $o'),
        ),
      );
    }
  }

  final Dio _dio;

  /// The underlying client, for the one thing the wrappers cannot express.
  ///
  /// Every method here unwraps the JSON envelope; a report is .xlsx bytes and
  /// has no envelope to unwrap. Exposed rather than adding a bytes-shaped
  /// variant of each verb — one caller needs this, and it still travels with
  /// the interceptors that attach the token and refresh it.
  Dio get raw => _dio;
  final TokenStore _tokens;

  /// Fired when the session cannot be renewed. The router listens.
  final void Function()? onSessionExpired;

  Completer<bool>? _refreshInFlight;

  /// Sign-in and refresh must not be retried on 401 — that is the answer, not a
  /// problem to work around.
  bool _isAuthCall(String path) =>
      path.contains('/auth/login') ||
      path.contains('/auth/refresh') ||
      path.contains('/technical-admin/auth/login');

  Future<Response<dynamic>?> _refreshAndReplay(RequestOptions original) async {
    final refreshed = await _refreshOnce();
    if (!refreshed) {
      await _tokens.clear();
      onSessionExpired?.call();
      return null;
    }
    try {
      final token = _tokens.accessToken;
      return await _dio.fetch(
        original..headers['Authorization'] = 'Bearer $token',
      );
    } on DioException {
      return null;
    }
  }

  /// One refresh at a time; everyone else waits for its result.
  Future<bool> _refreshOnce() {
    final existing = _refreshInFlight;
    if (existing != null) return existing.future;

    final completer = Completer<bool>();
    _refreshInFlight = completer;

    () async {
      try {
        final refresh = _tokens.refreshToken;
        if (refresh == null || refresh.isEmpty) {
          completer.complete(false);
          return;
        }
        // A bare Dio: the interceptor above must not run on this call.
        final plain = Dio(
          BaseOptions(
            baseUrl: AppConfig.apiBaseUrl,
            connectTimeout: AppConfig.connectTimeout,
            receiveTimeout: AppConfig.receiveTimeout,
            headers: const {'Content-Type': 'application/json'},
          ),
        );
        final res = await plain.post<dynamic>(
          '/auth/refresh',
          data: {'refreshToken': refresh},
        );
        final data = ApiEnvelope.unwrap(res.data);
        final tokens = data is Map<String, dynamic>
            ? (data['tokens'] ?? data)
            : null;
        final access = tokens is Map<String, dynamic>
            ? tokens['accessToken'] as String?
            : null;
        if (access == null || access.isEmpty) {
          completer.complete(false);
          return;
        }
        await _tokens.saveTokens(
          access: access,
          refresh: tokens is Map<String, dynamic>
              ? tokens['refreshToken'] as String?
              : null,
        );
        completer.complete(true);
      } catch (_) {
        completer.complete(false);
      } finally {
        _refreshInFlight = null;
      }
    }();

    return completer.future;
  }

  Future<dynamic> get(String path, {Map<String, dynamic>? query}) =>
      _send(() => _dio.get<dynamic>(path, queryParameters: query));

  Future<dynamic> post(
    String path, {
    Object? body,
    Map<String, dynamic>? query,
  }) =>
      _send(() => _dio.post<dynamic>(path, data: body, queryParameters: query));

  Future<dynamic> put(String path, {Object? body}) =>
      _send(() => _dio.put<dynamic>(path, data: body));

  Future<dynamic> delete(String path) =>
      _send(() => _dio.delete<dynamic>(path));

  Future<dynamic> upload(String path, FormData form) => _send(
    () => _dio.post<dynamic>(
      path,
      data: form,
      options: Options(contentType: 'multipart/form-data'),
    ),
  );

  Future<dynamic> _send(Future<Response<dynamic>> Function() call) async {
    try {
      final res = await call();
      final status = res.statusCode ?? 0;
      if (status >= 200 && status < 300) return ApiEnvelope.unwrap(res.data);
      throw _fromStatus(status, res.data);
    } on DioException catch (e) {
      throw _fromDio(e);
    } on Failure {
      rethrow;
    } catch (_) {
      throw const ParseFailure();
    }
  }

  Failure _fromStatus(int status, dynamic body) {
    final map = body is Map<String, dynamic> ? body : const <String, dynamic>{};
    final message = map['message']?.toString();

    switch (status) {
      case 400:
      case 422:
        return ValidationFailure(
          message ?? 'Please check what you entered.',
          _fieldErrors(map),
        );
      case 401:
        return const AuthFailure();
      case 403:
        return ForbiddenFailure(
          message ?? "You don't have permission to do that.",
        );
      case 404:
        return NotFoundFailure(message ?? 'Not found.');
      case 409:
        return ValidationFailure(message ?? 'That already exists.');
      default:
        return ServerFailure(
          message ?? 'Something went wrong. Try again shortly.',
        );
    }
  }

  /// The backend returns field errors under `errors` as { field: message }.
  Map<String, String> _fieldErrors(Map<String, dynamic> body) {
    final errors = body['errors'];
    if (errors is Map) {
      return errors.map((k, v) => MapEntry(k.toString(), v.toString()));
    }
    return const {};
  }

  Failure _fromDio(DioException e) {
    switch (e.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
        return const TimeoutFailure();
      case DioExceptionType.connectionError:
      case DioExceptionType.unknown:
        return const NetworkFailure();
      case DioExceptionType.badCertificate:
        return const NetworkFailure('Could not verify the server certificate.');
      case DioExceptionType.cancel:
        return const NetworkFailure('Request cancelled.');
      case DioExceptionType.badResponse:
        return _fromStatus(e.response?.statusCode ?? 500, e.response?.data);
      // Dio adds cases over time; anything new is treated as a transport
      // problem rather than stopping the build on the next upgrade.
      default:
        return const NetworkFailure();
    }
  }
}
