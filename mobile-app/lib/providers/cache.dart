import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Keeps a provider's result for a while after the screen using it closes.
///
/// Every list in this app is an `autoDispose` provider, which throws its result
/// away the moment the last widget watching it goes. Leaving a screen and
/// coming back therefore re-runs the request, and the phone shows a spinner for
/// a round trip it already paid for a second ago. Against a server in another
/// continent that round trip is most of a second, so moving between two tabs a
/// few times feels like the whole application is slow when nothing is wrong
/// with it.
///
/// This keeps the result alive for [duration] after the last listener leaves.
/// Come back inside that window and the data is already there; come back later
/// and it refetches. Pull-to-refresh still invalidates immediately, so a
/// deliberate refresh always reaches the server.
///
/// Only for data that does not change second by second -- the staff directory,
/// holidays, leave types, the module list. Anything live (attendance today,
/// pending approvals, chat) is deliberately left uncached.
void cacheFor(Ref ref, [Duration duration = const Duration(minutes: 3)]) {
  final link = ref.keepAlive();
  Timer? expiry;

  // A refetch restarts the clock rather than stacking timers.
  ref.onDispose(() => expiry?.cancel());
  ref.onCancel(() {
    expiry?.cancel();
    expiry = Timer(duration, link.close);
  });
  ref.onResume(() => expiry?.cancel());
}

/// Reference data that changes a few times a year at most.
const cacheLong = Duration(minutes: 30);

/// Lists that change during a working day but not minute to minute.
const cacheShort = Duration(minutes: 3);
