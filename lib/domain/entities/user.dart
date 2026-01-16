/// User entity representing a messenger user
class User {
  final String id;
  final String callsign;
  final String? displayName;
  final String? avatarUrl;
  final bool isVerified;
  final DateTime createdAt;

  const User({
    required this.id,
    required this.callsign,
    this.displayName,
    this.avatarUrl,
    this.isVerified = false,
    required this.createdAt,
  });

  User copyWith({
    String? id,
    String? callsign,
    String? displayName,
    String? avatarUrl,
    bool? isVerified,
    DateTime? createdAt,
  }) {
    return User(
      id: id ?? this.id,
      callsign: callsign ?? this.callsign,
      displayName: displayName ?? this.displayName,
      avatarUrl: avatarUrl ?? this.avatarUrl,
      isVerified: isVerified ?? this.isVerified,
      createdAt: createdAt ?? this.createdAt,
    );
  }
}
