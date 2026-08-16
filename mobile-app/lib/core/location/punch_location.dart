import 'package:geolocator/geolocator.dart';

/// Why a punch has no coordinates, when it has none.
///
/// Not a bool. "Location unavailable" covers four different situations and the
/// person needs a different sentence for each — turning GPS on, granting a
/// permission, and opening system settings are three separate actions, and
/// "could not get your location" tells them which one to take: none.
enum LocationOutcome {
  /// A position was read.
  fixed,

  /// The device's location services are switched off entirely.
  serviceOff,

  /// Refused this time. Asking again is allowed.
  denied,

  /// Refused permanently. Only system settings can undo it — asking again does
  /// nothing at all, which is why this is a separate case.
  deniedForever,

  /// Permission granted, services on, no fix in time. Indoors, usually.
  timedOut,
}

/// A position, or the reason there isn't one.
class PunchLocation {
  const PunchLocation({required this.outcome, this.latitude, this.longitude});

  final LocationOutcome outcome;
  final double? latitude;
  final double? longitude;

  bool get hasFix => latitude != null && longitude != null;

  /// What to tell somebody whose punch went through without coordinates.
  ///
  /// Null when there is a fix — there is nothing to say, and a message that
  /// appears every single time trains people to dismiss it unread.
  String? get warning => switch (outcome) {
        LocationOutcome.fixed => null,
        LocationOutcome.serviceOff =>
          'Location is off, so this punch was saved without it. '
              'Turn on location and punch again if your site needs it.',
        LocationOutcome.denied =>
          'Location permission was declined, so this punch was saved without '
              'it.',
        LocationOutcome.deniedForever =>
          'Location is blocked for this app. Allow it in Settings if your '
              'site checks where you punch from.',
        LocationOutcome.timedOut =>
          'Could not get a GPS fix in time — this punch was saved without '
              'location. Try again outdoors if your site needs it.',
      };
}

/// Reading where somebody is, for a punch.
///
/// The rule that shapes all of this: **a punch is never blocked**. Somebody
/// standing at the gate at nine o'clock must be able to mark their attendance
/// whether or not the GPS cooperates. The server agrees — it records a punch
/// with no coordinates as a geofence exception rather than refusing it — so
/// every failure here returns a reason, never an exception.
///
/// Which is the whole reason this file exists. The app was sending no
/// coordinates at all, so *every* punch made from a phone was filed as a
/// geofence exception, and a field employee could punch in from anywhere.
class PunchLocationService {
  const PunchLocationService();

  /// How long to wait for a fix.
  ///
  /// Eight seconds. Long enough for a cold GPS outdoors, short enough that
  /// somebody punching in does not think the app has hung — and the punch goes
  /// through regardless when it lapses.
  static const Duration _timeout = Duration(seconds: 8);

  Future<PunchLocation> current() async {
    try {
      if (!await Geolocator.isLocationServiceEnabled()) {
        return const PunchLocation(outcome: LocationOutcome.serviceOff);
      }

      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        // Asked at the moment it is needed, with the punch button on screen,
        // rather than in a burst at first launch where it has no context.
        permission = await Geolocator.requestPermission();
      }

      if (permission == LocationPermission.deniedForever) {
        return const PunchLocation(outcome: LocationOutcome.deniedForever);
      }
      if (permission == LocationPermission.denied) {
        return const PunchLocation(outcome: LocationOutcome.denied);
      }

      final position = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          // Medium, not best. `best` keeps the radio on hunting for a metre of
          // precision that a geofence measured in tens of metres does not need,
          // and it is the difference between a punch that takes two seconds and
          // one that takes fifteen.
          accuracy: LocationAccuracy.medium,
          timeLimit: _timeout,
        ),
      );

      return PunchLocation(
        outcome: LocationOutcome.fixed,
        latitude: position.latitude,
        longitude: position.longitude,
      );
    } catch (_) {
      // Every failure path lands here as well — a timeout, a platform channel
      // that is not available, a permission the OS revoked mid-call. None of
      // them may stop somebody marking their attendance.
      return const PunchLocation(outcome: LocationOutcome.timedOut);
    }
  }
}
