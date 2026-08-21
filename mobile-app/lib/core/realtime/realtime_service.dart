import 'dart:async';
import 'dart:convert';

import 'package:stomp_dart_client/stomp_dart_client.dart';

import '../config/app_config.dart';
import '../storage/token_store.dart';

/// Something the server pushed.
class RealtimeEvent {
  const RealtimeEvent({required this.topic, required this.body});

  /// The destination it arrived on, so a listener can tell chat from a task.
  final String topic;
  final Map<String, dynamic> body;
}

/// The live connection to the portal.
///
/// The portal pushes chat, notifications, tasks and presence over STOMP. This
/// app polled instead — which is adequate for a list somebody is already looking
/// at, and useless for a notification: nothing arrived until a screen was opened
/// and asked. A message that only appears when you go looking for it is a list
/// item, not a notification.
///
/// This became possible with the certificate. A websocket to a plain-http origin
/// is `ws://`, Android refuses cleartext, and there was no domain to issue a
/// certificate for. `wss://` needed the domain first.
class RealtimeService {
  RealtimeService(this._tokens);

  final TokenStore _tokens;

  StompClient? _client;
  final _events = StreamController<RealtimeEvent>.broadcast();

  /// Destinations currently subscribed, so a reconnect can restore them.
  ///
  /// STOMP subscriptions do not survive a dropped socket. Without this, the
  /// first tunnel or lift would leave the app connected and silent — the worst
  /// of the three states, because nothing looks wrong.
  final Set<String> _topics = {};
  final Map<String, StompUnsubscribe> _active = {};

  Stream<RealtimeEvent> get events => _events.stream;
  bool get isConnected => _client?.connected ?? false;

  /// The SockJS raw-websocket transport.
  ///
  /// The server registers `/ws` with `.withSockJS()` and no plain endpoint
  /// beside it. SockJS exposes a real websocket at `{endpoint}/websocket`, which
  /// is what a native client can speak — the rest of SockJS is fallbacks for
  /// browsers that cannot.
  static String get _url {
    final base = AppConfig.apiBaseUrl.endsWith('/api')
        ? AppConfig.apiBaseUrl.substring(0, AppConfig.apiBaseUrl.length - 4)
        : AppConfig.apiBaseUrl;
    return '${base.replaceFirst(RegExp(r'^http'), 'ws')}/ws/websocket';
  }

  /// Reconnect attempts with exponential backoff.
  int _reconnectAttempts = 0;
  static const _maxReconnectDelay = Duration(seconds: 30);

  void connect() {
    if (_client != null) return;

    final token = _tokens.accessToken;
    if (token == null || token.isEmpty) return;

    _client = StompClient(
      config: StompConfig(
        url: _url,
        stompConnectHeaders: {'Authorization': 'Bearer $token'},
        webSocketConnectHeaders: {'Authorization': 'Bearer $token'},
        onConnect: _onConnect,
        // Exponential backoff: 1s → 2s → 4s → 8s → 16s → 30s (capped).
        // The default 5s was too slow for chat — a user sends a message and
        // the reply appears 5 seconds later even though the socket dropped.
        reconnectDelay: Duration(
          seconds: (1 * (1 << _reconnectAttempts.clamp(0, 5))).clamp(1, 30),
        ),
        heartbeatIncoming: const Duration(seconds: 15),
        heartbeatOutgoing: const Duration(seconds: 15),
        onWebSocketError: (_) {},
        onStompError: (_) {},
        onDisconnect: (_) {
          _active.clear();
          _reconnectAttempts++;
        },
      ),
    );

    _client!.activate();
    _reconnectAttempts = 0;
  }

  void _onConnect(StompFrame _) {
    _reconnectAttempts = 0; // Reset backoff on successful connection.
    // Re-subscribe to everything asked for while the socket was down.
    for (final topic in _topics) {
      _subscribeNow(topic);
    }
  }

  /// Listen to a destination, now or as soon as the socket is up.
  void subscribe(String topic) {
    if (!_topics.add(topic)) return;
    if (isConnected) _subscribeNow(topic);
  }

  void _subscribeNow(String topic) {
    final client = _client;
    if (client == null || !client.connected) return;
    _active[topic]?.call(unsubscribeHeaders: null);
    _active[topic] = client.subscribe(
      destination: topic,
      callback: (frame) {
        final raw = frame.body;
        if (raw == null || raw.isEmpty) return;
        try {
          final decoded = jsonDecode(raw);
          if (decoded is Map<String, dynamic>) {
            _events.add(RealtimeEvent(topic: topic, body: decoded));
          }
        } catch (_) {
          // A frame that is not JSON is not something this app can use, and
          // throwing here would take the whole socket down with it.
        }
      },
    );
  }

  void unsubscribe(String topic) {
    _topics.remove(topic);
    _active.remove(topic)?.call(unsubscribeHeaders: null);
  }

  Future<void> disconnect() async {
    _topics.clear();
    _active.clear();
    _client?.deactivate();
    _client = null;
  }

  void dispose() {
    disconnect();
    _events.close();
  }
}
