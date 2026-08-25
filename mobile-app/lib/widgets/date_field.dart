import 'package:flutter/material.dart';

/// Picking a date, as a compact box rather than a full screen.
///
/// Flutter's own picker decides between a dialog and a full-screen sheet by
/// asking whether the calendar fits in the space it has. On a phone with the
/// font size turned up it does not fit, so it takes the whole screen -- which
/// is jarring next to the rest of the app, hides the form being filled in, and
/// looks nothing like the date field on the website.
///
/// Two things keep it a box:
///
/// The text inside the dialog is pinned to its normal size. It is a grid of
/// two-digit numbers, so scaling them buys nothing legible and costs the
/// layout the room it needs to stay a dialog.
///
/// The keyboard-entry toggle is removed. It is the control that makes the
/// picker tall enough to give up on being a dialog, and a date typed by hand
/// is the rarer case on a phone.
Future<DateTime?> pickDate(
  BuildContext context, {
  required DateTime initialDate,
  required DateTime firstDate,
  required DateTime lastDate,
  String? helpText,
}) {
  return showDatePicker(
    context: context,
    initialDate: initialDate,
    firstDate: firstDate,
    lastDate: lastDate,
    helpText: helpText,
    initialEntryMode: DatePickerEntryMode.calendarOnly,
    builder: (context, child) {
      final media = MediaQuery.of(context);
      return MediaQuery(
        data: media.copyWith(textScaler: TextScaler.noScaling),
        child: child ?? const SizedBox.shrink(),
      );
    },
  );
}
