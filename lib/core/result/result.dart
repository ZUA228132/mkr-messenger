import '../error/api_error.dart';
import '../errors/failures.dart' as failures;

/// A Result type that represents either a success value or a failure.
/// 
/// This is a sealed class that can only be either [Success], [Failure], or [Error].
/// Use pattern matching or the [fold] method to handle both cases.
/// 
/// Example:
/// ```dart
/// final result = await repository.getData();
/// result.fold(
///   onSuccess: (data) => print('Got data: $data'),
///   onFailure: (error) => print('Error: ${error.message}'),
/// );
/// ```
sealed class Result<T> {
  const Result();

  /// Returns true if this is a [Success].
  bool get isSuccess => this is Success<T>;

  /// Returns true if this is a [Failure] or [Error].
  bool get isFailure => this is Failure<T> || this is Error<T>;

  /// Returns the value if this is a [Success], otherwise returns null.
  T? get valueOrNull => switch (this) {
        Success<T> s => s.value,
        Failure<T> _ => null,
        Error<T> _ => null,
      };

  /// Returns the ApiError if this is a [Failure], otherwise returns null.
  ApiError? get errorOrNull => switch (this) {
        Success<T> _ => null,
        Failure<T> f => f.error,
        Error<T> e => ApiError(message: e.failure.message),
      };

  /// Returns the Failure if this is an [Error], otherwise returns null.
  /// For backward compatibility with existing code.
  failures.Failure? get failureOrNull => switch (this) {
        Success<T> _ => null,
        Failure<T> f => failures.ServerFailure(f.error.message),
        Error<T> e => e.failure,
      };

  /// Returns the value if this is a [Success], otherwise returns [defaultValue].
  T getOrElse(T defaultValue) => switch (this) {
        Success<T> s => s.value,
        Failure<T> _ => defaultValue,
        Error<T> _ => defaultValue,
      };

  /// Returns the value if this is a [Success], otherwise calls [orElse].
  T getOrElseGet(T Function() orElse) => switch (this) {
        Success<T> s => s.value,
        Failure<T> _ => orElse(),
        Error<T> _ => orElse(),
      };

  /// Returns the value if this is a [Success], otherwise throws the error.
  T getOrThrow() => switch (this) {
        Success<T> s => s.value,
        Failure<T> f => throw Exception(f.error.message),
        Error<T> e => throw Exception(e.failure.message),
      };

  /// Transforms the value if this is a [Success], otherwise returns the failure.
  Result<R> map<R>(R Function(T value) transform) => switch (this) {
        Success<T> s => Success(transform(s.value)),
        Failure<T> f => Failure(f.error),
        Error<T> e => Error(e.failure),
      };

  /// Transforms the value if this is a [Success] using a function that returns a [Result].
  Result<R> flatMap<R>(Result<R> Function(T value) transform) => switch (this) {
        Success<T> s => transform(s.value),
        Failure<T> f => Failure(f.error),
        Error<T> e => Error(e.failure),
      };

  /// Transforms the error if this is a [Failure], otherwise returns the [Success].
  Result<T> mapError(ApiError Function(ApiError error) transform) =>
      switch (this) {
        Success<T> s => s,
        Failure<T> f => Failure(transform(f.error)),
        Error<T> e => Failure(transform(ApiError(message: e.failure.message))),
      };

  /// Handles both success and failure cases.
  R fold<R>({
    required R Function(T value) onSuccess,
    required R Function(ApiError error) onFailure,
  }) =>
      switch (this) {
        Success<T> s => onSuccess(s.value),
        Failure<T> f => onFailure(f.error),
        Error<T> e => onFailure(ApiError(message: e.failure.message)),
      };

  /// Executes [action] if this is a [Success].
  Result<T> onSuccess(void Function(T value) action) {
    if (this is Success<T>) {
      action((this as Success<T>).value);
    }
    return this;
  }

  /// Executes [action] if this is a [Failure] or [Error].
  Result<T> onFailure(void Function(ApiError error) action) {
    if (this is Failure<T>) {
      action((this as Failure<T>).error);
    } else if (this is Error<T>) {
      action(ApiError(message: (this as Error<T>).failure.message));
    }
    return this;
  }

  /// Converts this Result to an async Result.
  Future<Result<T>> toFuture() => Future.value(this);
}

/// Represents a successful result with a value.
class Success<T> extends Result<T> {
  final T value;

  const Success(this.value);

  @override
  String toString() => 'Success($value)';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Success<T> &&
          runtimeType == other.runtimeType &&
          value == other.value;

  @override
  int get hashCode => value.hashCode;
}

/// Represents a failed result with an ApiError.
/// This is the new preferred way to represent failures.
class Failure<T> extends Result<T> {
  final ApiError error;

  const Failure(this.error);

  @override
  String toString() => 'Failure($error)';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Failure<T> &&
          runtimeType == other.runtimeType &&
          error == other.error;

  @override
  int get hashCode => error.hashCode;
}

/// Represents a failed result with a domain Failure.
/// This is kept for backward compatibility with existing code.
/// New code should use [Failure] with [ApiError] instead.
class Error<T> extends Result<T> {
  final failures.Failure failure;

  const Error(this.failure);

  @override
  String toString() => 'Error($failure)';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Error<T> &&
          runtimeType == other.runtimeType &&
          failure == other.failure;

  @override
  int get hashCode => failure.hashCode;
}

/// Extension methods for working with Results.
extension ResultExtensions<T> on Result<T> {
  /// Converts a nullable value to a Result.
  static Result<T> fromNullable<T>(T? value, {required ApiError onNull}) {
    if (value != null) {
      return Success(value);
    }
    return Failure(onNull);
  }
}

/// Extension methods for working with Future<Result>.
extension FutureResultExtensions<T> on Future<Result<T>> {
  /// Maps the success value of a Future<Result>.
  Future<Result<R>> mapAsync<R>(R Function(T value) transform) async {
    final result = await this;
    return result.map(transform);
  }

  /// FlatMaps the success value of a Future<Result>.
  Future<Result<R>> flatMapAsync<R>(
      Future<Result<R>> Function(T value) transform) async {
    final result = await this;
    return switch (result) {
      Success<T> s => transform(s.value),
      Failure<T> f => Failure(f.error),
      Error<T> e => Error(e.failure),
    };
  }

  /// Handles both success and failure cases of a Future<Result>.
  Future<R> foldAsync<R>({
    required R Function(T value) onSuccess,
    required R Function(ApiError error) onFailure,
  }) async {
    final result = await this;
    return result.fold(onSuccess: onSuccess, onFailure: onFailure);
  }
}

/// Helper functions for creating Results.
class Results {
  Results._();

  /// Creates a successful Result.
  static Result<T> success<T>(T value) => Success(value);

  /// Creates a failed Result with ApiError.
  static Result<T> failure<T>(ApiError error) => Failure(error);

  /// Creates a failed Result with domain Failure (backward compatibility).
  static Result<T> error<T>(failures.Failure failure) => Error(failure);

  /// Creates a failed Result from a message.
  static Result<T> failureFromMessage<T>(String message) =>
      Failure(ApiError(message: message));

  /// Wraps a function that might throw in a Result.
  static Result<T> tryCatch<T>(T Function() fn, {String? endpoint}) {
    try {
      return Success(fn());
    } catch (e) {
      return Failure(ApiError.unknown(e, endpoint: endpoint));
    }
  }

  /// Wraps an async function that might throw in a Result.
  static Future<Result<T>> tryCatchAsync<T>(
    Future<T> Function() fn, {
    String? endpoint,
  }) async {
    try {
      return Success(await fn());
    } catch (e) {
      return Failure(ApiError.unknown(e, endpoint: endpoint));
    }
  }

  /// Combines multiple Results into a single Result containing a list.
  static Result<List<T>> combine<T>(List<Result<T>> results) {
    final values = <T>[];
    for (final result in results) {
      switch (result) {
        case Success<T> s:
          values.add(s.value);
        case Failure<T> f:
          return Failure(f.error);
        case Error<T> e:
          return Error(e.failure);
      }
    }
    return Success(values);
  }
}

/// Adapter to convert between Failure types and ApiError.
extension FailureToApiErrorAdapter on failures.Failure {
  /// Converts a domain Failure to an ApiError.
  ApiError toApiError() {
    return ApiError(
      message: message,
      errorCode: runtimeType.toString(),
    );
  }
}

/// Adapter to convert ApiError to domain Failure.
extension ApiErrorToFailureAdapter on ApiError {
  /// Converts an ApiError to a domain Failure.
  failures.Failure toFailure() {
    if (isNetworkError) {
      return const failures.NetworkUnavailableFailure();
    }
    if (isAuthError) {
      return const failures.TokenExpiredFailure();
    }
    return failures.ServerFailure(message);
  }
}
