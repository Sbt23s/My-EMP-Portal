import '../error/failures.dart';

/// The shape every endpoint on this backend answers with.
///
///   { "success": true, "message": "OK", "data": {...}, "timestamp": "..." }
///
/// Some endpoints put the payload directly in the body instead of under `data`
/// — the paged ones do — so [unwrap] copes with both rather than each caller
/// remembering which is which.
class ApiEnvelope {
  const ApiEnvelope._();

  /// The payload, whether it arrived wrapped or bare.
  ///
  /// Throws [ServerFailure] when the envelope says the call failed, carrying the
  /// reference the backend now returns instead of an exception dump.
  static dynamic unwrap(dynamic body) {
    if (body is! Map<String, dynamic>) return body;

    if (body.containsKey('success')) {
      final ok = body['success'] == true;
      if (!ok) {
        final message = body['message']?.toString() ?? 'Request failed';
        throw ServerFailure(message, _referenceIn(message));
      }
      // A successful envelope with no `data` is normal for endpoints that only
      // report that something happened.
      return body.containsKey('data') ? body['data'] : body;
    }

    // Paged responses come back bare: content / page / size / totalElements.
    return body;
  }

  /// The backend appends "(ref a1b2c3d4)" to a 500 so the log can be found.
  static String? _referenceIn(String message) {
    final match = RegExp(r'\(ref ([0-9a-f]{8})\)').firstMatch(message);
    return match?.group(1);
  }

  /// A list from either a bare array or a `content` array.
  static List<Map<String, dynamic>> listOf(dynamic payload) {
    if (payload is List) {
      return payload.whereType<Map<String, dynamic>>().toList();
    }
    if (payload is Map<String, dynamic> && payload['content'] is List) {
      return (payload['content'] as List)
          .whereType<Map<String, dynamic>>()
          .toList();
    }
    return const [];
  }
}

/// One page of results, in the shape `PageEnvelope` uses on the server.
class Paged<T> {
  const Paged({
    required this.items,
    required this.page,
    required this.totalPages,
    required this.totalElements,
    required this.last,
  });

  final List<T> items;
  final int page;
  final int totalPages;
  final int totalElements;
  final bool last;

  static Paged<T> from<T>(
    dynamic payload,
    T Function(Map<String, dynamic>) parse,
  ) {
    final map = payload is Map<String, dynamic>
        ? payload
        : const <String, dynamic>{};
    return Paged<T>(
      items: ApiEnvelope.listOf(payload).map(parse).toList(),
      page: _int(map['page']) ?? _int(map['number']) ?? 0,
      totalPages: _int(map['totalPages']) ?? 1,
      totalElements: _int(map['totalElements']) ?? 0,
      last: map['last'] as bool? ?? true,
    );
  }

  static Paged<T> empty<T>() => Paged<T>(
    items: const [],
    page: 0,
    totalPages: 0,
    totalElements: 0,
    last: true,
  );

  bool get isEmpty => items.isEmpty;

  static int? _int(dynamic v) => v is int ? v : (v is num ? v.toInt() : null);
}
