/// What went wrong, in terms a screen can act on.
///
/// Every repository returns one of these rather than letting a DioException
/// reach the UI. A widget should never have to know what a status code is, and
/// "no internet" needs a different answer from "your session ended".
sealed class Failure implements Exception {
  const Failure(this.message);

  /// Safe to show to the person using the app.
  final String message;

  @override
  String toString() => message;
}

/// The request never reached the server.
class NetworkFailure extends Failure {
  const NetworkFailure([
    super.message = 'No connection. Check your internet and try again.',
  ]);
}

/// It reached the server and took too long to answer.
class TimeoutFailure extends Failure {
  const TimeoutFailure([
    super.message = 'The server took too long to respond. Try again.',
  ]);
}

/// Signed out, or the session ended and could not be renewed. The router sends
/// the person to the login screen when it sees this.
class AuthFailure extends Failure {
  const AuthFailure([
    super.message = 'Your session has ended. Please sign in again.',
  ]);
}

/// Signed in, but not allowed to do this.
class ForbiddenFailure extends Failure {
  const ForbiddenFailure([
    super.message = "You don't have permission to do that.",
  ]);
}

/// Asked for something that is not there.
class NotFoundFailure extends Failure {
  const NotFoundFailure([super.message = 'Not found.']);
}

/// The server rejected what was sent. [fieldErrors] fills in form fields when
/// the backend names them, so validation lands on the right input.
class ValidationFailure extends Failure {
  const ValidationFailure(super.message, [this.fieldErrors = const {}]);

  final Map<String, String> fieldErrors;
}

/// The server broke. The reference, when there is one, matches the backend log —
/// worth showing so support can find it.
class ServerFailure extends Failure {
  const ServerFailure([
    super.message = 'Something went wrong on our side. Try again shortly.',
    this.reference,
  ]);

  final String? reference;

  @override
  String toString() =>
      reference == null ? message : '$message (ref $reference)';
}

/// The response arrived but was not the shape we expect. Its own case because it
/// means a contract changed, not that the user did anything wrong.
class ParseFailure extends Failure {
  const ParseFailure([super.message = 'The server sent something unexpected.']);
}
