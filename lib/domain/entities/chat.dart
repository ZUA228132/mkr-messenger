import 'message.dart';

/// Chat types
enum ChatType { direct, group, channel }

/// Chat entity representing a conversation
class Chat {
  final String id;
  final ChatType type;
  final List<String> participantIds;
  final Map<String, String> participantNames;
  final Message? lastMessage;
  final DateTime updatedAt;
  final String? name;
  final String? avatarUrl;
  final int unreadCount;

  const Chat({
    required this.id,
    required this.type,
    required this.participantIds,
    this.participantNames = const {},
    this.lastMessage,
    required this.updatedAt,
    this.name,
    this.avatarUrl,
    this.unreadCount = 0,
  });

  /// Get display name for a participant
  String? getParticipantName(String participantId) {
    return participantNames[participantId];
  }

  Chat copyWith({
    String? id,
    ChatType? type,
    List<String>? participantIds,
    Map<String, String>? participantNames,
    Message? lastMessage,
    DateTime? updatedAt,
    String? name,
    String? avatarUrl,
    int? unreadCount,
  }) {
    return Chat(
      id: id ?? this.id,
      type: type ?? this.type,
      participantIds: participantIds ?? this.participantIds,
      participantNames: participantNames ?? this.participantNames,
      lastMessage: lastMessage ?? this.lastMessage,
      updatedAt: updatedAt ?? this.updatedAt,
      name: name ?? this.name,
      avatarUrl: avatarUrl ?? this.avatarUrl,
      unreadCount: unreadCount ?? this.unreadCount,
    );
  }
}
