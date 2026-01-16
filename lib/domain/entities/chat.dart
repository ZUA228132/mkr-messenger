import 'message.dart';

/// Chat types
enum ChatType { direct, group, channel }

/// Chat entity representing a conversation
class Chat {
  final String id;
  final ChatType type;
  final List<String> participantIds;
  final Message? lastMessage;
  final DateTime updatedAt;
  final String? name;
  final String? avatarUrl;

  const Chat({
    required this.id,
    required this.type,
    required this.participantIds,
    this.lastMessage,
    required this.updatedAt,
    this.name,
    this.avatarUrl,
  });

  Chat copyWith({
    String? id,
    ChatType? type,
    List<String>? participantIds,
    Message? lastMessage,
    DateTime? updatedAt,
    String? name,
    String? avatarUrl,
  }) {
    return Chat(
      id: id ?? this.id,
      type: type ?? this.type,
      participantIds: participantIds ?? this.participantIds,
      lastMessage: lastMessage ?? this.lastMessage,
      updatedAt: updatedAt ?? this.updatedAt,
      name: name ?? this.name,
      avatarUrl: avatarUrl ?? this.avatarUrl,
    );
  }
}
