import 'package:flutter/material.dart';

import '../core/branding/branding.dart';

/// Material 3, in the portal's colours, for both themes.
///
/// The indigo is taken from the web client's primary so the two products look
/// like the same product. Both themes are defined in full rather than one being
/// derived from the other — a naively inverted dark theme is how contrast gets
/// lost on the surfaces that matter.
class AppTheme {
  const AppTheme._();

  static const Color _brand = Color(0xFF4F46E5); // indigo, as on the web
  static const Color _success = Color(0xFF10B981);
  static const Color _warning = Color(0xFFF59E0B);
  static const Color _danger = Color(0xFFEF4444);

  static Color success(BuildContext c) => _success;
  static Color warning(BuildContext c) => _warning;
  static Color danger(BuildContext c) => _danger;

  static ThemeData get light => _build(Brightness.light, null);
  static ThemeData get dark => _build(Brightness.dark, null);

  /// The same theme in the company's chosen colour and typeface.
  ///
  /// [brand] null means nothing has been chosen — most companies — and the
  /// portal's own indigo applies, which is why the two getters above exist
  /// unchanged for the sign-in screen: nobody has said who they are yet, so
  /// there is no company whose colours could apply.
  static ThemeData branded(Brightness brightness, ResolvedBranding brand) =>
      _build(brightness, brand);

  static ThemeData _build(Brightness brightness, ResolvedBranding? brand) {
    final scheme = ColorScheme.fromSeed(
      // The accent seeds the whole scheme rather than being painted on top of
      // it. Overriding only `primary` leaves the container and on-container
      // colours derived from indigo, and a teal button on a faint indigo card is
      // the sort of mismatch that reads as a bug rather than a theme.
      seedColor: brand?.theme.accent ?? _brand,
      brightness: brightness,
    );
    final isDark = brightness == Brightness.dark;

    /*
     * The page background is deliberately not taken from the theme's `surface`.
     *
     * Four of the twenty themes are dark ones, and a person choosing "Midnight"
     * for their company must not override the light/dark setting somebody made
     * on their own phone. The accent — the part that reads as "our colour" —
     * carries into both. This matches what the web client does, and for the same
     * reason.
     */

    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: isDark
          ? const Color(0xFF0F1117)
          : const Color(0xFFF7F8FB),

      appBarTheme: AppBarTheme(
        backgroundColor: isDark ? const Color(0xFF151823) : Colors.white,
        foregroundColor: scheme.onSurface,
        elevation: 0,
        scrolledUnderElevation: 1,
        centerTitle: false,
        titleTextStyle: TextStyle(
          color: scheme.onSurface,
          fontSize: 18,
          fontWeight: FontWeight.w600,
        ),
      ),

      cardTheme: CardThemeData(
        color: isDark ? const Color(0xFF171B26) : Colors.white,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: BorderSide(
            color: scheme.outlineVariant.withValues(alpha: isDark ? 0.35 : 0.6),
          ),
        ),
      ),

      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: isDark ? const Color(0xFF11141D) : Colors.white,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 14,
          vertical: 14,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: scheme.outlineVariant),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: scheme.outlineVariant),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: scheme.primary, width: 1.6),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: _danger),
        ),
        focusedErrorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: _danger, width: 1.6),
        ),
      ),

      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(50),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
        ),
      ),

      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size.fromHeight(48),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      ),

      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),

      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: isDark ? const Color(0xFF151823) : Colors.white,
        indicatorColor: scheme.primary.withValues(alpha: 0.14),
        labelTextStyle: WidgetStateProperty.all(
          const TextStyle(fontSize: 12, fontWeight: FontWeight.w500),
        ),
      ),

      dividerTheme: DividerThemeData(
        color: scheme.outlineVariant.withValues(alpha: 0.6),
        thickness: 1,
        space: 1,
      ),

      // Body text carries the body family; headings are applied below, so a
      // pairing like "Serif + Sans" reads as two faces rather than one.
      fontFamily: brand?.font.bodyFamily,
    ).copyWith(
      textTheme: _withHeadingFace(
        ThemeData(brightness: brightness).textTheme,
        brand,
      ),
    );
  }

  /// Headings in the heading face, everything else left alone.
  ///
  /// Returns the text theme untouched when the pairing uses one family for both
  /// — rebuilding every style to set the same value it already has would be
  /// work for no visible difference.
  static TextTheme _withHeadingFace(TextTheme base, ResolvedBranding? brand) {
    final heading = brand?.font.headingFamily;
    if (heading == null || heading == brand?.font.bodyFamily) return base;
    return base.copyWith(
      displayLarge: base.displayLarge?.copyWith(fontFamily: heading),
      displayMedium: base.displayMedium?.copyWith(fontFamily: heading),
      displaySmall: base.displaySmall?.copyWith(fontFamily: heading),
      headlineLarge: base.headlineLarge?.copyWith(fontFamily: heading),
      headlineMedium: base.headlineMedium?.copyWith(fontFamily: heading),
      headlineSmall: base.headlineSmall?.copyWith(fontFamily: heading),
      titleLarge: base.titleLarge?.copyWith(fontFamily: heading),
      titleMedium: base.titleMedium?.copyWith(fontFamily: heading),
    );
  }
}
