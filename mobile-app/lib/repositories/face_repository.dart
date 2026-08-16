import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';

import '../core/config/app_config.dart';
import '../core/error/failures.dart';
import '../core/network/api_client.dart';

/// What the face service made of a selfie.
class FaceVerdict {
  const FaceVerdict({
    required this.match,
    this.score,
    this.message,
    this.raw,
  });

  final bool match;
  final double? score;
  final String? message;

  /// Everything the check measured, kept whole so it can travel with the punch.
  ///
  /// A dispute months later has something to read rather than a bare yes, which
  /// is the same reason the web client sends it.
  final Map<String, dynamic>? raw;

  static FaceVerdict fromJson(Map<String, dynamic> json) => FaceVerdict(
        match: json['match'] == true,
        score: double.tryParse(json['score']?.toString() ?? ''),
        message: json['message']?.toString(),
        raw: json,
      );
}

/// Face verification and the punch that follows it.
///
/// Mirrors what the web client does, deliberately and to the letter: the selfie
/// goes to the Python face service, and only a match is allowed to become a
/// punch. Nothing is matched on the device.
///
/// That last part matters. The attendance endpoint refuses a face punch unless
/// the caller says `verified=true`, so an app that decided for itself would be
/// asserting something it had not checked. Sending the photo to the same service
/// the browser uses means the phone and the desk agree on what counts as a
/// match, and neither of them is the thing deciding.
class FaceRepository {
  FaceRepository(this._api);

  final ApiClient _api;

  /// A separate Dio, on purpose.
  ///
  /// The face service is a different application behind the same nginx: it sits
  /// under /analytics rather than /api, and it takes no bearer token — it
  /// answered 422 rather than 401 when probed without one. Reusing the app's
  /// client would send the session token to a service that has no business
  /// holding it, and would have to have its base path fought with on every call.
  late final Dio _faceDio = Dio(
    BaseOptions(
      baseUrl: AppConfig.faceServiceBaseUrl,
      connectTimeout: AppConfig.connectTimeout,
      receiveTimeout: AppConfig.receiveTimeout,
      // The service answers 4xx with a body worth reading — "no face", "several
      // faces", "not enrolled". Letting Dio throw on those would replace the
      // explanation with a status code.
      validateStatus: (_) => true,
    ),
  );

  /// POST /analytics/api/face/verify/{userId}
  ///
  /// Throws [ServerFailure] carrying the service's own words when it refuses,
  /// because those words are the useful part: a person can act on "no face
  /// detected" and cannot act on "verification failed".
  Future<FaceVerdict> verify({required int userId, required File photo}) async {
    final form = FormData.fromMap({
      'file': await MultipartFile.fromFile(photo.path, filename: 'face.jpg'),
    });

    late final Response<dynamic> res;
    try {
      res = await _faceDio.post<dynamic>('/api/face/verify/$userId', data: form);
    } on DioException catch (_) {
      // Unreachable is its own case. The service is optional infrastructure —
      // it runs in a container that can be switched off — and "could not reach
      // the face service" is a different problem from "that is not your face".
      throw const ServerFailure(
        'Could not reach the face service. It may not be running — ask HR, '
        'or use an ordinary punch instead.',
        null,
      );
    }

    final body = res.data;
    final json = body is Map<String, dynamic>
        ? body
        : (body is String ? jsonDecode(body) as Map<String, dynamic> : null);

    if (json == null) throw const ParseFailure();

    if (res.statusCode != null && res.statusCode! >= 400) {
      // FastAPI puts the reason in `detail`, which is sometimes a string and
      // sometimes a list of field errors.
      final detail = json['detail'];
      final text = detail is String
          ? detail
          : (detail is List && detail.isNotEmpty
              ? detail.first.toString()
              : 'Verification failed.');
      throw ServerFailure(text, null);
    }

    return FaceVerdict.fromJson(json);
  }

  /// POST /attendance/face-punch — multipart, with the selfie attached.
  ///
  /// The photo travels with the punch rather than being uploaded separately: it
  /// is the evidence for the punch, and a separate upload is one that can be
  /// orphaned from the record it belongs to.
  Future<void> facePunch({
    required bool punchIn,
    required File photo,
    required FaceVerdict verdict,
    double? latitude,
    double? longitude,
  }) async {
    final form = FormData.fromMap({
      'kind': punchIn ? 'punch-in' : 'punch-out',
      'verified': verdict.match.toString(),
      'photo': await MultipartFile.fromFile(photo.path, filename: 'punch.jpg'),
      if (verdict.score != null) 'score': verdict.score.toString(),
      if (verdict.raw != null) 'detail': jsonEncode(verdict.raw),
      if (latitude != null) 'latitude': latitude.toString(),
      if (longitude != null) 'longitude': longitude.toString(),
      'mode': 'FACE_VERIFIED',
    });

    await _api.upload('/attendance/face-punch', form);
  }
}
