import 'dart:developer' as developer;

import '../../core/error/api_error.dart';
import '../../core/result/result.dart';
import '../../domain/entities/user.dart';
import '../datasources/api_client.dart';
import '../models/user_models.dart';

/// Remote User Repository for MKR Backend integration
///
/// Requirements: 7.1-7.3 - User profile view and edit
/// Requirements: 8.1-8.3 - User search and chat creation
/// Requirements: 9.2 - FCM token registration
class RemoteUserRepository {
  final ApiClient _apiClient;

  RemoteUserRepository({
    required ApiClient apiClient,
  }) : _apiClient = apiClient;

  /// Get user by ID
  /// Requirements: 7.1 - GET /api/users/{userId}
  Future<Result<User>> getUser(String userId) async {
    developer.log(
      'Fetching user: $userId',
      name: 'RemoteUserRepository',
    );

    final result = await _apiClient.get('/api/users/$userId');

    return result.fold(
      onSuccess: (response) {
        try {
          final userResponse = UserResponse.fromJson(
            response.data as Map<String, dynamic>,
          );
          final user = userResponse.toEntity();

          developer.log(
            'Fetched user: ${user.id} (${user.callsign})',
            name: 'RemoteUserRepository',
          );

          return Success(user);
        } catch (e) {
          developer.log(
            'Failed to parse user response: $e',
            name: 'RemoteUserRepository',
          );
          return Failure(ApiError(message: 'Failed to parse user: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to fetch user: ${apiError.message}',
          name: 'RemoteUserRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Get current user profile
  /// Requirements: 7.1 - GET /api/users/{userId} (for current user)
  Future<Result<User>> getCurrentUser() async {
    developer.log(
      'Fetching current user profile',
      name: 'RemoteUserRepository',
    );

    final result = await _apiClient.get('/api/users/me');

    return result.fold(
      onSuccess: (response) {
        try {
          final userResponse = UserResponse.fromJson(
            response.data as Map<String, dynamic>,
          );
          final user = userResponse.toEntity();

          developer.log(
            'Fetched current user: ${user.id} (${user.callsign})',
            name: 'RemoteUserRepository',
          );

          return Success(user);
        } catch (e) {
          developer.log(
            'Failed to parse current user response: $e',
            name: 'RemoteUserRepository',
          );
          return Failure(ApiError(message: 'Failed to parse user: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to fetch current user: ${apiError.message}',
          name: 'RemoteUserRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Update user profile
  /// Requirements: 7.2 - POST /api/users/me
  Future<Result<User>> updateProfile({
    String? displayName,
    String? callsign,
    String? bio,
  }) async {
    final request = UpdateProfileRequest(
      displayName: displayName,
      callsign: callsign,
      bio: bio,
    );

    developer.log(
      'Updating profile: displayName=$displayName',
      name: 'RemoteUserRepository',
    );

    final result = await _apiClient.post(
      '/api/users/me',
      data: request.toJson(),
    );

    return result.fold(
      onSuccess: (response) {
        try {
          final userResponse = UserResponse.fromJson(
            response.data as Map<String, dynamic>,
          );
          final user = userResponse.toEntity();

          developer.log(
            'Profile updated successfully: ${user.id}',
            name: 'RemoteUserRepository',
          );

          return Success(user);
        } catch (e) {
          developer.log(
            'Failed to parse update profile response: $e',
            name: 'RemoteUserRepository',
          );
          return Failure(ApiError(message: 'Failed to parse user: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to update profile: ${apiError.message}',
          name: 'RemoteUserRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Update user avatar
  /// Requirements: 7.3 - POST /api/users/me/avatar
  Future<Result<User>> updateAvatar(String avatarUrl) async {
    final request = UpdateAvatarRequest(avatarUrl: avatarUrl);

    developer.log(
      'Updating avatar',
      name: 'RemoteUserRepository',
    );

    final result = await _apiClient.post(
      '/api/users/me/avatar',
      data: request.toJson(),
    );

    return result.fold(
      onSuccess: (response) {
        try {
          final userResponse = UserResponse.fromJson(
            response.data as Map<String, dynamic>,
          );
          final user = userResponse.toEntity();

          developer.log(
            'Avatar updated successfully: ${user.id}',
            name: 'RemoteUserRepository',
          );

          return Success(user);
        } catch (e) {
          developer.log(
            'Failed to parse update avatar response: $e',
            name: 'RemoteUserRepository',
          );
          return Failure(ApiError(message: 'Failed to parse user: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to update avatar: ${apiError.message}',
          name: 'RemoteUserRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Upload avatar file and update profile
  /// Requirements: 7.3 - POST /api/users/me/avatar (with file upload)
  Future<Result<User>> uploadAvatar(String filePath) async {
    developer.log(
      'Uploading avatar file: $filePath',
      name: 'RemoteUserRepository',
    );

    final result = await _apiClient.uploadFile(
      '/api/users/me/avatar',
      filePath: filePath,
      fieldName: 'avatar',
    );

    return result.fold(
      onSuccess: (response) {
        try {
          developer.log(
            'Upload avatar response: ${response.data}',
            name: 'RemoteUserRepository',
          );

          // Handle different response formats
          final data = response.data as Map<String, dynamic>;
          final responseData = data['data'] as Map<String, dynamic>? ?? data;

          final userResponse = UserResponse.fromJson(responseData);
          final user = userResponse.toEntity();

          developer.log(
            'Avatar uploaded successfully: ${user.id}, avatarUrl: ${user.avatarUrl}',
            name: 'RemoteUserRepository',
          );

          return Success(user);
        } catch (e) {
          developer.log(
            'Failed to parse upload avatar response: $e\nResponse was: ${response.data}',
            name: 'RemoteUserRepository',
          );
          return Failure(ApiError(message: 'Failed to parse user: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to upload avatar: ${apiError.message} (code: ${apiError.statusCode})',
          name: 'RemoteUserRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Search users by query
  /// Requirements: 8.1 - GET /api/users/search?q={query}
  /// Requirements: 8.2 - Display search results with username, displayName, online status
  Future<Result<List<User>>> searchUsers(String query) async {
    developer.log(
      'Searching users: query="$query"',
      name: 'RemoteUserRepository',
    );

    final result = await _apiClient.get(
      '/api/users/search',
      queryParameters: {'q': query},
    );

    return result.fold(
      onSuccess: (response) {
        try {
          final searchResponse = SearchUsersResponse.fromJson(response.data);
          final users = searchResponse.toEntities();

          developer.log(
            'Found ${users.length} users for query: "$query"',
            name: 'RemoteUserRepository',
          );

          return Success(users);
        } catch (e) {
          developer.log(
            'Failed to parse search users response: $e',
            name: 'RemoteUserRepository',
          );
          return Failure(ApiError(message: 'Failed to parse users: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to search users: ${apiError.message}',
          name: 'RemoteUserRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Update FCM token for push notifications
  /// Requirements: 9.2 - POST /api/users/fcm-token
  Future<Result<void>> updateFcmToken(String token) async {
    final request = UpdateFcmTokenRequest(
      token: token,
      platform: 'ios',
    );

    developer.log(
      'Updating FCM token',
      name: 'RemoteUserRepository',
    );

    final result = await _apiClient.post(
      '/api/users/fcm-token',
      data: request.toJson(),
    );

    return result.fold(
      onSuccess: (_) {
        developer.log(
          'FCM token updated successfully',
          name: 'RemoteUserRepository',
        );
        return const Success(null);
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to update FCM token: ${apiError.message}',
          name: 'RemoteUserRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Get user online status
  /// Requirements: 8.2 - Display online status in search results
  Future<Result<bool>> getUserOnlineStatus(String userId) async {
    developer.log(
      'Checking online status for user: $userId',
      name: 'RemoteUserRepository',
    );

    final result = await _apiClient.get('/api/users/$userId/status');

    return result.fold(
      onSuccess: (response) {
        try {
          final data = response.data as Map<String, dynamic>;
          final isOnline = data['isOnline'] as bool? ?? false;

          developer.log(
            'User $userId online status: $isOnline',
            name: 'RemoteUserRepository',
          );

          return Success(isOnline);
        } catch (e) {
          developer.log(
            'Failed to parse online status response: $e',
            name: 'RemoteUserRepository',
          );
          return Failure(ApiError(message: 'Failed to parse status: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to get online status: ${apiError.message}',
          name: 'RemoteUserRepository',
        );
        return Failure(apiError);
      },
    );
  }
}
