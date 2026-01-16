import 'dart:developer' as developer;

import '../../core/error/api_error.dart';
import '../../core/result/result.dart';
import '../../domain/entities/chat.dart';
import '../datasources/api_client.dart';
import '../models/chat_models.dart';

/// Remote Chat Repository for MKR Backend integration
///
/// Requirements: 4.1-4.4 - Chat list, display, navigation, and creation
class RemoteChatRepository {
  final ApiClient _apiClient;

  RemoteChatRepository({
    required ApiClient apiClient,
  }) : _apiClient = apiClient;

  /// Get list of chats for current user
  /// Requirements: 4.1 - GET /api/chats
  /// Requirements: 4.2 - Display chat list with name, last message, unread count
  Future<Result<List<Chat>>> getChats() async {
    developer.log(
      'Fetching chats list',
      name: 'RemoteChatRepository',
    );

    final result = await _apiClient.get('/api/chats');

    return result.fold(
      onSuccess: (response) {
        try {
          final chatsResponse = ChatsListResponse.fromJson(response.data);
          final chats = chatsResponse.toEntities();

          developer.log(
            'Fetched ${chats.length} chats',
            name: 'RemoteChatRepository',
          );

          return Success(chats);
        } catch (e) {
          developer.log(
            'Failed to parse chats response: $e',
            name: 'RemoteChatRepository',
          );
          return Failure(ApiError(message: 'Failed to parse chats: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to fetch chats: ${apiError.message}',
          name: 'RemoteChatRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Create a new chat
  /// Requirements: 4.4 - POST /api/chats with participantIds
  Future<Result<Chat>> createChat({
    required String type,
    String? name,
    required List<String> participantIds,
  }) async {
    final request = CreateChatRequest(
      type: type,
      name: name,
      participantIds: participantIds,
    );

    developer.log(
      'Creating chat: type=$type, participants=${participantIds.length}',
      name: 'RemoteChatRepository',
    );

    final result = await _apiClient.post(
      '/api/chats',
      data: request.toJson(),
    );

    return result.fold(
      onSuccess: (response) {
        try {
          final chatResponse = ChatResponse.fromJson(
            response.data as Map<String, dynamic>,
          );
          final chat = chatResponse.toEntity();

          developer.log(
            'Chat created successfully: ${chat.id}',
            name: 'RemoteChatRepository',
          );

          return Success(chat);
        } catch (e) {
          developer.log(
            'Failed to parse create chat response: $e',
            name: 'RemoteChatRepository',
          );
          return Failure(ApiError(message: 'Failed to parse chat: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to create chat: ${apiError.message}',
          name: 'RemoteChatRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Delete a chat
  /// Requirements: 4.3 - Navigate to chat screen (implied delete capability)
  Future<Result<void>> deleteChat(String chatId) async {
    developer.log(
      'Deleting chat: $chatId',
      name: 'RemoteChatRepository',
    );

    final result = await _apiClient.delete('/api/chats/$chatId');

    return result.fold(
      onSuccess: (_) {
        developer.log(
          'Chat deleted successfully: $chatId',
          name: 'RemoteChatRepository',
        );
        return const Success(null);
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to delete chat: ${apiError.message}',
          name: 'RemoteChatRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Get a single chat by ID
  /// Requirements: 4.3 - Navigate to chat screen with chatId
  Future<Result<Chat>> getChat(String chatId) async {
    developer.log(
      'Fetching chat: $chatId',
      name: 'RemoteChatRepository',
    );

    final result = await _apiClient.get('/api/chats/$chatId');

    return result.fold(
      onSuccess: (response) {
        try {
          final chatResponse = ChatResponse.fromJson(
            response.data as Map<String, dynamic>,
          );
          final chat = chatResponse.toEntity();

          developer.log(
            'Fetched chat: ${chat.id}',
            name: 'RemoteChatRepository',
          );

          return Success(chat);
        } catch (e) {
          developer.log(
            'Failed to parse chat response: $e',
            name: 'RemoteChatRepository',
          );
          return Failure(ApiError(message: 'Failed to parse chat: $e'));
        }
      },
      onFailure: (apiError) {
        developer.log(
          'Failed to fetch chat: ${apiError.message}',
          name: 'RemoteChatRepository',
        );
        return Failure(apiError);
      },
    );
  }

  /// Create or get existing direct chat with a user
  /// Requirements: 8.3 - Create or open existing direct chat
  Future<Result<Chat>> getOrCreateDirectChat(String userId) async {
    developer.log(
      'Getting or creating direct chat with user: $userId',
      name: 'RemoteChatRepository',
    );

    // First try to create - backend should return existing if it exists
    return createChat(
      type: 'direct',
      participantIds: [userId],
    );
  }
}
