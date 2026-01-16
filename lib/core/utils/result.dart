import '../errors/failures.dart';

/// A Result type that represents either a success value or a failure
sealed class Result<T> {
  const Result();
  
  bool get isSuccess => this is Success<T>;
  bool get isFailure => this is Failure;
  
  T? get valueOrNull => switch (this) {
    Success<T> s => s.value,
    Error<T> _ => null,
  };
  
  Failure? get failureOrNull => switch (this) {
    Success<T> _ => null,
    Error<T> e => e.failure,
  };
  
  R fold<R>({
    required R Function(T value) onSuccess,
    required R Function(Failure failure) onFailure,
  }) {
    return switch (this) {
      Success<T> s => onSuccess(s.value),
      Error<T> e => onFailure(e.failure),
    };
  }
}

/// Represents a successful result with a value
class Success<T> extends Result<T> {
  final T value;
  const Success(this.value);
}

/// Represents a failed result with a failure
class Error<T> extends Result<T> {
  final Failure failure;
  const Error(this.failure);
}
