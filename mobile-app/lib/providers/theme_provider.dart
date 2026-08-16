import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Light, dark, or whatever the phone is set to.
///
/// The portal has a light/dark button in its top bar and this app had none — it
/// followed the system and offered no way to disagree with it. That is a real
/// difference for anybody who keeps their phone in dark mode but wants to read a
/// payslip in light, and there was no way to ask.
///
/// Kept in plain preferences rather than the secure store: which theme somebody
/// prefers is not a secret, and the keychain is for tokens.
class ThemeController extends StateNotifier<ThemeMode> {
  ThemeController() : super(ThemeMode.system) {
    _restore();
  }

  static const _key = 'hrp.themeMode';

  Future<void> _restore() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final saved = prefs.getString(_key);
      if (saved == null) return;
      state = switch (saved) {
        'light' => ThemeMode.light,
        'dark' => ThemeMode.dark,
        _ => ThemeMode.system,
      };
    } catch (_) {
      // A phone with storage unavailable still gets a working app; it just
      // follows the system, which is where it started.
    }
  }

  Future<void> set(ThemeMode mode) async {
    state = mode;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_key, switch (mode) {
        ThemeMode.light => 'light',
        ThemeMode.dark => 'dark',
        ThemeMode.system => 'system',
      });
    } catch (_) {
      // The choice still applies for this run. Losing it on restart is a much
      // smaller problem than refusing to apply it at all.
    }
  }

  /// Straight to the other one, for the single button in the top bar.
  ///
  /// From "follow the system" it goes to the opposite of what is on screen —
  /// tapping a toggle and seeing nothing change is the failure this avoids.
  Future<void> toggle(Brightness current) async {
    final next = switch (state) {
      ThemeMode.system =>
        current == Brightness.dark ? ThemeMode.light : ThemeMode.dark,
      ThemeMode.light => ThemeMode.dark,
      ThemeMode.dark => ThemeMode.light,
    };
    await set(next);
  }
}

final StateNotifierProvider<ThemeController, ThemeMode> themeModeProvider =
    StateNotifierProvider<ThemeController, ThemeMode>(
  (ref) => ThemeController(),
);
