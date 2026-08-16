import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/calls/call_service.dart';
import 'app_providers.dart';
import 'realtime_provider.dart';

final Provider<CallService> callServiceProvider = Provider<CallService>((ref) {
  final service = CallService(
    api: ref.watch(apiClientProvider),
    realtime: ref.watch(realtimeServiceProvider),
  );
  ref.onDispose(service.dispose);
  return service;
});

/// Ticks whenever the call changes, so a widget can watch one thing.
///
/// The service holds mutable state rather than rebuilding an immutable object
/// on every ICE candidate — a call produces dozens per second, and allocating a
/// new state object for each would be work nothing reads. This is the signal to
/// redraw; the service is the truth.
final StreamProvider<void> callChangesProvider = StreamProvider<void>((ref) {
  return ref.watch(callServiceProvider).changes;
});

/// Starts listening for incoming calls once somebody is signed in.
final Provider<void> callBinderProvider = Provider<void>((ref) {
  final user = ref.watch(currentUserProvider);
  if (user == null) return;
  ref.watch(callServiceProvider).bind(user.id);
});
