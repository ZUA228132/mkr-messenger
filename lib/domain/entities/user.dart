/// User entity representing a messenger user
class User {
  final String id;
  final String callsign;
  final String? displayName;
  final String? avatarUrl;
  final String? bio;
  final bool isVerified;
  final bool isOnline;
  final DateTime? lastSeen;
  final DateTime createdAt;

  const User({
    required this.id,
    required this.callsign,
    this.displayName,
    this.avatarUrl,
    this.bio,
    this.isVerified = false,
    this.isOnline = false,
    this.lastSeen,
    required this.createdAt,
  });

  User copyWith({
    String? id,
    String? callsign,
    String? displayName,
    String? avatarUrl,
    String? bio,
    bool? isVerified,
    bool? isOnline,
    DateTime? lastSeen,
    DateTime? createdAt,
  }) {
    return User(
      id: id ?? this.id,
      callsign: callsign ?? this.callsign,
      displayName: displayName ?? this.displayName,
      avatarUrl: avatarUrl ?? this.avatarUrl,
      bio: bio ?? this.bio,
      isVerified: isVerified ?? this.isVerified,
      isOnline: isOnline ?? this.isOnline,
      lastSeen: lastSeen ?? this.lastSeen,
      createdAt: createdAt ?? this.createdAt,
    );
  }
}
