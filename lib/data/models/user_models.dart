// User API Models for MKR Backend integration
// Requirements: 7.1, 7.2, 8.1 - User profile and search models

import '../../domain/entities/user.dart';

/// Response model for a single user from API
/// Requirements: 7.1 - GET /api/users/{userId} response
/// Requirements: 8.2 - Display search results with username, displayName, online status
class UserResponse {
  final String id;
  final String username;
  final String displayName;
  final String? avatarUrl;
  final String? bio;
  final bool isOnline;
  final bool isVerified;
  final int accessLevel;
  final int createdAt;

  const UserResponse({
    required this.id,
    required this.username,
    required this.displayName,
    this.avatarUrl,
    this.bio,
    this.isOnline = false,
    this.isVerified = false,
    this.accessLevel = 0,
    this.createdAt = 0,
  });

  factory UserResponse.fromJson(Map<String, dynamic> json) {
    return UserResponse(
      id: json['id'] as String,
      username: json['username'] as String? ?? '',
      displayName: json['displayName'] as String? ?? '',
      avatarUrl: json['avatarUrl'] as String?,
      bio: json['bio'] as String?,
      isOnline: json['isOnline'] as bool? ?? false,
      isVerified: json['isVerified'] as bool? ?? false,
      accessLevel: json['accessLevel'] as int? ?? 0,
      createdAt: json['createdAt'] as int? ?? 0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'username': username,
      'displayName': displayName,
      'avatarUrl': avatarUrl,
      'bio': bio,
      'isOnline': isOnline,
      'isVerified': isVerified,
      'accessLevel': accessLevel,
      'createdAt': createdAt,
    };
  }

  /// Convert to domain entity
  User toEntity() {
    return User(
      id: id,
      callsign: username,
      displayName: displayName.isNotEmpty ? displayName : null,
      avatarUrl: avatarUrl,
      bio: bio,
      isVerified: isVerified,
      isOnline: isOnline,
      createdAt: DateTime.fromMillisecondsSinceEpoch(createdAt),
    );
  }

  @override
  String toString() =>
      'UserResponse(id: $id, username: $username, displayName: $displayName, isOnline: $isOnline)';
}


/// Request body for updating user profile
/// Requirements: 7.2 - POST /api/users/me
class UpdateProfileRequest {
  final String? displayName;
  final String? bio;

  const UpdateProfileRequest({
    this.displayName,
    this.bio,
  });

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (displayName != null) {
      json['displayName'] = displayName;
    }
    if (bio != null) {
      json['bio'] = bio;
    }
    return json;
  }

  @override
  String toString() =>
      'UpdateProfileRequest(displayName: $displayName, bio: $bio)';
}

/// Request body for updating user avatar
/// Requirements: 7.3 - POST /api/users/me/avatar
class UpdateAvatarRequest {
  final String avatarUrl;

  const UpdateAvatarRequest({
    required this.avatarUrl,
  });

  Map<String, dynamic> toJson() {
    return {
      'avatarUrl': avatarUrl,
    };
  }

  @override
  String toString() => 'UpdateAvatarRequest(avatarUrl: $avatarUrl)';
}

/// Request body for updating FCM token
/// Requirements: 9.2 - POST /api/users/fcm-token
class UpdateFcmTokenRequest {
  final String token;
  final String platform;

  const UpdateFcmTokenRequest({
    required this.token,
    this.platform = 'ios',
  });

  Map<String, dynamic> toJson() {
    return {
      'token': token,
      'platform': platform,
    };
  }

  @override
  String toString() => 'UpdateFcmTokenRequest(platform: $platform)';
}

/// Response wrapper for user search results
/// Requirements: 8.1 - GET /api/users/search?q={query} response
/// Requirements: 8.2 - Display search results with username, displayName, online status
class SearchUsersResponse {
  final List<UserResponse> users;
  final int total;

  const SearchUsersResponse({
    required this.users,
    this.total = 0,
  });

  factory SearchUsersResponse.fromJson(dynamic json) {
    if (json is List) {
      return SearchUsersResponse(
        users: json
            .map((e) => UserResponse.fromJson(e as Map<String, dynamic>))
            .toList(),
        total: json.length,
      );
    } else if (json is Map<String, dynamic>) {
      final usersList = json['users'] as List<dynamic>? ?? [];
      return SearchUsersResponse(
        users: usersList
            .map((e) => UserResponse.fromJson(e as Map<String, dynamic>))
            .toList(),
        total: json['total'] as int? ?? usersList.length,
      );
    }
    return const SearchUsersResponse(users: []);
  }

  List<User> toEntities() {
    return users.map((u) => u.toEntity()).toList();
  }

  @override
  String toString() => 'SearchUsersResponse(count: ${users.length}, total: $total)';
}
