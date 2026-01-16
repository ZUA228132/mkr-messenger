import 'dart:async';
import 'dart:developer' as developer;

import 'package:dio/dio.dart';

import '../../core/config/api_config.dart';
import '../../core/error/api_error.dart';
import '../../core/result/result.dart';
import 'secure_storage_datasource.dart';


/// Callback type for handling 401 unauthorized responses.
/// Used to trigger logout/redirect to login screen.
typedef OnUnauthorizedCallback = void Function();

/// API Client using Dio for HTTP requests to MKR Backend.
/// 
/// Requirements: 2.1, 2.3, 2.5 - Send POST requests to API endpoints
/// Requirements: 3.2 - Include Authorization header with Bearer token
/// Requirements: 10.4 - Log all API errors for debugging
class ApiClient {
  final Dio _dio;
  final SecureStorageDatasource _storage;
  OnUnauthorizedCallback? _onUnauthorized;

  ApiClient({
    Dio? dio,
    SecureStorageDatasource? storage,
  })  : _dio = dio ?? Dio(),
        _storage = storage ?? SecureStorageDatasource() {
    _configureDio();
  }

  /// Configure Dio with base options and interceptors
  void _configureDio() {
    _dio.options = BaseOptions(
      baseUrl: ApiConfig.baseUrl,
      connectTimeout: ApiConfig.connectTimeout,
      receiveTimeout: ApiConfig.receiveTimeout,
      sendTimeout: ApiConfig.sendTimeout,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    );

    // Add interceptors
    _dio.interceptors.addAll([
      _createAuthInterceptor(),
      _createLoggingInterceptor(),
      _createErrorInterceptor(),
    ]);
  }

  /// Set callback for handling 401 unauthorized responses.
  /// Requirements: 3.3 - Redirect to login screen when token expires
  void setOnUnauthorized(OnUnauthorizedCallback callback) {
    _onUnauthorized = callback;
  }

  /// Set auth token for requests.
  /// Requirements: 3.2 - Include Authorization header with Bearer token
  void setAuthToken(String token) {
    _dio.options.headers['Authorization'] = 'Bearer $token';
  }

  /// Clear auth token from requests.
  void clearAuthToken() {
    _dio.options.headers.remove('Authorization');
  }

  /// Create Auth Interceptor for adding Authorization header.
  /// Requirements: 3.2 - Include Authorization header with Bearer token
  Interceptor _createAuthInterceptor() {
    return InterceptorsWrapper(
      onRequest: (options, handler) async {
        // If Authorization header is not already set, try to get token from storage
        if (!options.headers.containsKey('Authorization')) {
          final token = await _storage.getAccessToken();
          if (token != null && token.isNotEmpty) {
            options.headers['Authorization'] = 'Bearer $token';
          }
        }
        handler.next(options);
      },
      onResponse: (response, handler) {
        handler.next(response);
      },
      onError: (error, handler) async {
        // Handle 401 Unauthorized - token expired or invalid
        // Requirements: 3.3 - Redirect to login screen when token expires
        if (error.response?.statusCode == 401) {
          _logError('Unauthorized request - token may be expired', error);
          _onUnauthorized?.call();
        }
        handler.next(error);
      },
    );
  }


  /// Create Logging Interceptor for debugging.
  /// Requirements: 10.4 - Log all API errors for debugging
  Interceptor _createLoggingInterceptor() {
    return InterceptorsWrapper(
      onRequest: (options, handler) {
        _logRequest(options);
        handler.next(options);
      },
      onResponse: (response, handler) {
        _logResponse(response);
        handler.next(response);
      },
      onError: (error, handler) {
        _logError('API Error', error);
        handler.next(error);
      },
    );
  }

  /// Create Error Interceptor for handling and transforming errors.
  /// Requirements: 10.1 - Parse error message and display it to user
  Interceptor _createErrorInterceptor() {
    return InterceptorsWrapper(
      onError: (error, handler) {
        // Transform DioException to more specific error types
        final apiError = _transformError(error);
        _logApiError(apiError, error.requestOptions.path);
        handler.next(error);
      },
    );
  }

  /// Transform DioException to ApiError.
  /// Requirements: 10.1 - Parse error message and display it to user
  ApiError _transformError(DioException error) {
    final endpoint = error.requestOptions.path;
    switch (error.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
        return ApiError.timeout(endpoint: endpoint);
      case DioExceptionType.connectionError:
        return ApiError.network(endpoint: endpoint);
      case DioExceptionType.badResponse:
        if (error.response != null) {
          return ApiError.fromResponseData(
            error.response!.data,
            statusCode: error.response!.statusCode,
            endpoint: endpoint,
          );
        }
        return ApiError.unknown(error.message, endpoint: endpoint);
      case DioExceptionType.cancel:
        return ApiError.cancelled(endpoint: endpoint);
      default:
        return ApiError.unknown(error.message, endpoint: endpoint);
    }
  }

  // ============ HTTP Methods ============

  /// Perform GET request.
  Future<Result<Response>> get(
    String path, {
    Map<String, dynamic>? queryParameters,
    Options? options,
  }) async {
    try {
      final response = await _dio.get(
        path,
        queryParameters: queryParameters,
        options: options,
      );
      return Success(response);
    } on DioException catch (e) {
      return Failure(_transformError(e));
    } catch (e) {
      return Failure(ApiError.unknown(e, endpoint: path));
    }
  }

  /// Perform POST request.
  /// Requirements: 2.1, 2.3, 2.5 - Send POST requests to API endpoints
  Future<Result<Response>> post(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
  }) async {
    try {
      final response = await _dio.post(
        path,
        data: data,
        queryParameters: queryParameters,
        options: options,
      );
      return Success(response);
    } on DioException catch (e) {
      return Failure(_transformError(e));
    } catch (e) {
      return Failure(ApiError.unknown(e, endpoint: path));
    }
  }


  /// Perform PUT request.
  Future<Result<Response>> put(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
  }) async {
    try {
      final response = await _dio.put(
        path,
        data: data,
        queryParameters: queryParameters,
        options: options,
      );
      return Success(response);
    } on DioException catch (e) {
      return Failure(_transformError(e));
    } catch (e) {
      return Failure(ApiError.unknown(e, endpoint: path));
    }
  }

  /// Perform DELETE request.
  Future<Result<Response>> delete(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
  }) async {
    try {
      final response = await _dio.delete(
        path,
        data: data,
        queryParameters: queryParameters,
        options: options,
      );
      return Success(response);
    } on DioException catch (e) {
      return Failure(_transformError(e));
    } catch (e) {
      return Failure(ApiError.unknown(e, endpoint: path));
    }
  }

  /// Perform PATCH request.
  Future<Result<Response>> patch(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
  }) async {
    try {
      final response = await _dio.patch(
        path,
        data: data,
        queryParameters: queryParameters,
        options: options,
      );
      return Success(response);
    } on DioException catch (e) {
      return Failure(_transformError(e));
    } catch (e) {
      return Failure(ApiError.unknown(e, endpoint: path));
    }
  }

  /// Upload file with multipart form data.
  Future<Result<Response>> uploadFile(
    String path, {
    required String filePath,
    required String fieldName,
    Map<String, dynamic>? additionalFields,
    Options? options,
  }) async {
    try {
      final formData = FormData.fromMap({
        fieldName: await MultipartFile.fromFile(filePath),
        ...?additionalFields,
      });

      final response = await _dio.post(
        path,
        data: formData,
        options: options,
      );
      return Success(response);
    } on DioException catch (e) {
      return Failure(_transformError(e));
    } catch (e) {
      return Failure(ApiError.unknown(e, endpoint: path));
    }
  }


  // ============ Logging ============

  /// Log request details.
  /// Requirements: 10.4 - Log all API errors for debugging
  void _logRequest(RequestOptions options) {
    developer.log(
      '→ ${options.method} ${options.path}',
      name: 'ApiClient',
    );
    if (options.queryParameters.isNotEmpty) {
      developer.log(
        '  Query: ${options.queryParameters}',
        name: 'ApiClient',
      );
    }
    if (options.data != null) {
      developer.log(
        '  Body: ${_sanitizeLogData(options.data)}',
        name: 'ApiClient',
      );
    }
  }

  /// Log response details.
  void _logResponse(Response response) {
    developer.log(
      '← ${response.statusCode} ${response.requestOptions.path}',
      name: 'ApiClient',
    );
  }

  /// Log error details.
  /// Requirements: 10.4 - Log all API errors for debugging
  void _logError(String message, DioException error) {
    developer.log(
      '✗ $message: ${error.message}',
      name: 'ApiClient',
      error: error,
    );
    if (error.response != null) {
      developer.log(
        '  Status: ${error.response?.statusCode}',
        name: 'ApiClient',
      );
      developer.log(
        '  Response: ${error.response?.data}',
        name: 'ApiClient',
      );
    }
  }

  /// Log ApiError with endpoint info.
  /// Requirements: 10.4 - Log all API errors for debugging
  void _logApiError(ApiError error, String endpoint) {
    developer.log(
      '✗ API Error on $endpoint: ${error.message}',
      name: 'ApiClient',
    );
    if (error.statusCode != null) {
      developer.log(
        '  Status Code: ${error.statusCode}',
        name: 'ApiClient',
      );
    }
    if (error.errorCode != null) {
      developer.log(
        '  Error Code: ${error.errorCode}',
        name: 'ApiClient',
      );
    }
  }

  /// Sanitize sensitive data from logs (e.g., passwords).
  dynamic _sanitizeLogData(dynamic data) {
    if (data is Map<String, dynamic>) {
      final sanitized = Map<String, dynamic>.from(data);
      const sensitiveKeys = ['password', 'token', 'secret', 'pin'];
      for (final key in sensitiveKeys) {
        if (sanitized.containsKey(key)) {
          sanitized[key] = '***';
        }
      }
      return sanitized;
    }
    return data;
  }

  // ============ Utility Methods ============

  /// Get the underlying Dio instance for advanced usage.
  Dio get dio => _dio;

  /// Check if client has auth token set.
  bool get hasAuthToken =>
      _dio.options.headers.containsKey('Authorization');
}
