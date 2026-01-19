package com.pranavajay.database

import com.pranavajay.models.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document

object Collections {
    private val db get() = DatabaseFactory.getDatabase()

    val users get() = db.getCollection<UserEntity>("users")
    val messages get() = db.getCollection<MessageEntity>("messages")
    val chats get() = db.getCollection<ChatEntity>("chats")
    val groups get() = db.getCollection<GroupEntity>("groups")
    val statuses get() = db.getCollection<StatusEntity>("statuses")
    val channels get() = db.getCollection<ChannelEntity>("channels")
    val calls get() = db.getCollection<CallEntity>("calls")
    val reactions get() = db.getCollection<ReactionEntity>("reactions")
}

// User Repository
object UserRepository {
    suspend fun createUser(user: UserEntity): UserEntity {
        Collections.users.insertOne(user)
        return user
    }

    suspend fun findByEmail(email: String): UserEntity? {
        return Collections.users.find(Document("email", email)).firstOrNull()
    }

    suspend fun findById(id: String): UserEntity? {
        return Collections.users.find(Document("id", id)).firstOrNull()
    }

    suspend fun findByPhone(phone: String): UserEntity? {
        return Collections.users.find(Document("phone", phone)).firstOrNull()
    }

    suspend fun findByIds(ids: List<String>): List<UserEntity> {
        return Collections.users.find(Document("id", Document("\$in", ids))).toList()
    }

    suspend fun updateUser(id: String, updates: Document): Boolean {
        val result = Collections.users.updateOne(
            Document("id", id),
            Document("\$set", updates)
        )
        return result.modifiedCount > 0
    }

    suspend fun setOnlineStatus(id: String, isOnline: Boolean) {
        val updates = Document()
            .append("isOnline", isOnline)
            .append("lastSeen", if (!isOnline) System.currentTimeMillis() else null)
        updateUser(id, updates)
    }

    suspend fun searchUsers(query: String): List<UserEntity> {
        val regex = Document("\$regex", query).append("\$options", "i")
        val filter = Document("\$or", listOf(
            Document("username", regex),
            Document("email", regex),
            Document("phone", regex)
        ))
        return Collections.users.find(filter).toList()
    }

    suspend fun addContact(userId: String, contactId: String) {
        Collections.users.updateOne(
            Document("id", userId),
            Document("\$addToSet", Document("contacts", contactId))
        )
    }

    suspend fun blockUser(userId: String, blockedId: String) {
        Collections.users.updateOne(
            Document("id", userId),
            Document("\$addToSet", Document("blockedUsers", blockedId))
        )
    }

    suspend fun unblockUser(userId: String, blockedId: String) {
        Collections.users.updateOne(
            Document("id", userId),
            Document("\$pull", Document("blockedUsers", blockedId))
        )
    }

    suspend fun updatePublicKey(userId: String, publicKey: String) {
        updateUser(userId, Document("publicKey", publicKey))
    }
}

// Message Repository
object MessageRepository {
    suspend fun saveMessage(message: MessageEntity): MessageEntity {
        Collections.messages.insertOne(message)
        return message
    }

    suspend fun getMessageById(id: String): MessageEntity? {
        return Collections.messages.find(Document("id", id)).firstOrNull()
    }

    suspend fun getMessagesByChatId(chatId: String, limit: Int = 50, before: Long? = null): List<MessageEntity> {
        val filter = Document("chatId", chatId).append("isDeleted", false)
        before?.let { filter.append("timestamp", Document("\$lt", it)) }

        return Collections.messages
            .find(filter)
            .limit(limit)
            .toList()
            .sortedBy { it.timestamp }
    }

    suspend fun markAsRead(messageId: String) {
        Collections.messages.updateOne(
            Document("id", messageId),
            Document("\$set", Document("isRead", true))
        )
    }

    suspend fun markAsDelivered(messageId: String) {
        Collections.messages.updateOne(
            Document("id", messageId),
            Document("\$set", Document("isDelivered", true))
        )
    }

    suspend fun deleteMessage(messageId: String) {
        Collections.messages.updateOne(
            Document("id", messageId),
            Document("\$set", Document("isDeleted", true))
        )
    }

    suspend fun deleteMessagePermanently(messageId: String) {
        Collections.messages.deleteOne(Document("id", messageId))
    }
    
    suspend fun deleteForUser(messageId: String, userId: String) {
        Collections.messages.updateOne(
            Document("id", messageId),
            Document("\$addToSet", Document("deletedFor", userId))
        )
    }

    suspend fun editMessage(messageId: String, newContent: String) {
        Collections.messages.updateOne(
            Document("id", messageId),
            Document("\$set", Document("content", newContent)
                .append("isEdited", true)
                .append("editedAt", System.currentTimeMillis()))
        )
    }

    suspend fun addReaction(messageId: String, userId: String, emoji: String) {
        Collections.messages.updateOne(
            Document("id", messageId),
            Document("\$addToSet", Document("reactions.$emoji", userId))
        )
    }

    suspend fun removeReaction(messageId: String, userId: String, emoji: String) {
        Collections.messages.updateOne(
            Document("id", messageId),
            Document("\$pull", Document("reactions.$emoji", userId))
        )
    }

    suspend fun markScreenshotted(messageId: String, userId: String) {
        Collections.messages.updateOne(
            Document("id", messageId),
            Document("\$set", Document("isScreenshotted", true))
                .append("\$addToSet", Document("screenshottedBy", userId))
        )
    }

    suspend fun getExpiredMessages(): List<MessageEntity> {
        val now = System.currentTimeMillis()
        return Collections.messages
            .find(Document("expiresAt", Document("\$lte", now)).append("expiresAt", Document("\$ne", null)))
            .toList()
    }

    suspend fun searchMessages(query: String, chatId: String? = null): List<MessageEntity> {
        val regex = Document("\$regex", query).append("\$options", "i")
        val filter = Document("content", regex).append("isDeleted", false)
        chatId?.let { filter.append("chatId", it) }
        return Collections.messages.find(filter).limit(50).toList()
    }
}

// Chat Repository
object ChatRepository {
    suspend fun createChat(chat: ChatEntity): ChatEntity {
        Collections.chats.insertOne(chat)
        return chat
    }

    suspend fun getChatById(id: String): ChatEntity? {
        return Collections.chats.find(Document("id", id)).firstOrNull()
    }

    suspend fun findChatByUsers(user1: String, user2: String): ChatEntity? {
        val filter = Document("\$or", listOf(
            Document("participants", listOf(user1, user2)),
            Document("participants", listOf(user2, user1))
        ))
        return Collections.chats.find(filter).firstOrNull()
    }

    suspend fun getChatsByUserId(userId: String): List<ChatEntity> {
        return Collections.chats
            .find(Document("participants", userId))
            .toList()
            .sortedByDescending { it.lastMessageTime }
    }

    suspend fun updateLastMessage(chatId: String, message: String, time: Long, type: String = "TEXT") {
        Collections.chats.updateOne(
            Document("id", chatId),
            Document("\$set", Document("lastMessage", message)
                .append("lastMessageTime", time)
                .append("lastMessageType", type))
        )
    }

    suspend fun incrementUnread(chatId: String, forUserId: String) {
        Collections.chats.updateOne(
            Document("id", chatId),
            Document("\$inc", Document("unreadCount.$forUserId", 1))
        )
    }

    suspend fun resetUnread(chatId: String, forUserId: String) {
        Collections.chats.updateOne(
            Document("id", chatId),
            Document("\$set", Document("unreadCount.$forUserId", 0))
        )
    }

    suspend fun updateDisappearingMode(chatId: String, enabled: Boolean, duration: Long?) {
        val updates = Document("disappearingMode", enabled)
        duration?.let { updates.append("disappearAfter", it) }
        Collections.chats.updateOne(
            Document("id", chatId),
            Document("\$set", updates)
        )
    }

    suspend fun pinChat(chatId: String, userId: String, pinned: Boolean) {
        Collections.chats.updateOne(
            Document("id", chatId),
            Document("\$set", Document("isPinned.$userId", pinned))
        )
    }

    suspend fun muteChat(chatId: String, userId: String, muted: Boolean) {
        Collections.chats.updateOne(
            Document("id", chatId),
            Document("\$set", Document("isMuted.$userId", muted))
        )
    }

    suspend fun archiveChat(chatId: String, userId: String, archived: Boolean) {
        Collections.chats.updateOne(
            Document("id", chatId),
            Document("\$set", Document("isArchived.$userId", archived))
        )
    }
    
    suspend fun setDisappearingMessages(chatId: String, duration: Long?) {
        val updates = Document()
        if (duration != null && duration > 0) {
            updates.append("disappearingMode", true).append("disappearAfter", duration)
        } else {
            updates.append("disappearingMode", false)
        }
        Collections.chats.updateOne(
            Document("id", chatId),
            Document("\$set", updates)
        )
    }
    
    suspend fun setGhostMode(chatId: String, enabled: Boolean) {
        Collections.chats.updateOne(
            Document("id", chatId),
            Document("\$set", Document("disappearingMode", enabled))
        )
    }
    
    suspend fun setPinned(chatId: String, pinned: Boolean) {
        Collections.chats.updateOne(
            Document("id", chatId),
            Document("\$set", Document("isPinned", pinned))
        )
    }
    
    suspend fun setArchived(chatId: String, archived: Boolean) {
        Collections.chats.updateOne(
            Document("id", chatId),
            Document("\$set", Document("isArchived", archived))
        )
    }
}

// Group Repository
object GroupRepository {
    suspend fun createGroup(group: GroupEntity): GroupEntity {
        Collections.groups.insertOne(group)
        return group
    }

    suspend fun getGroupById(id: String): GroupEntity? {
        return Collections.groups.find(Document("id", id)).firstOrNull()
    }

    suspend fun getGroupsByUserId(userId: String): List<GroupEntity> {
        return Collections.groups
            .find(Document("members", userId))
            .toList()
    }

    suspend fun addMember(groupId: String, userId: String) {
        Collections.groups.updateOne(
            Document("id", groupId),
            Document("\$addToSet", Document("members", userId))
        )
    }

    suspend fun removeMember(groupId: String, userId: String) {
        Collections.groups.updateOne(
            Document("id", groupId),
            Document("\$pull", Document("members", userId))
        )
    }

    suspend fun addAdmin(groupId: String, userId: String) {
        Collections.groups.updateOne(
            Document("id", groupId),
            Document("\$addToSet", Document("admins", userId))
        )
    }

    suspend fun updateGroup(groupId: String, updates: Document) {
        Collections.groups.updateOne(
            Document("id", groupId),
            Document("\$set", updates)
        )
    }
}

// Status Repository
object StatusRepository {
    suspend fun createStatus(status: StatusEntity): StatusEntity {
        Collections.statuses.insertOne(status)
        return status
    }

    suspend fun getStatusesByUserId(userId: String): List<StatusEntity> {
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        return Collections.statuses
            .find(Document("userId", userId).append("createdAt", Document("\$gte", twentyFourHoursAgo)))
            .toList()
    }

    suspend fun getContactsStatuses(contactIds: List<String>): List<StatusEntity> {
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        return Collections.statuses
            .find(Document("userId", Document("\$in", contactIds))
                .append("createdAt", Document("\$gte", twentyFourHoursAgo)))
            .toList()
    }

    suspend fun addViewer(statusId: String, viewerId: String) {
        Collections.statuses.updateOne(
            Document("id", statusId),
            Document("\$addToSet", Document("viewedBy", viewerId))
        )
    }

    suspend fun addReaction(statusId: String, userId: String, emoji: String) {
        Collections.statuses.updateOne(
            Document("id", statusId),
            Document("\$addToSet", Document("reactions.$emoji", userId))
        )
    }

    suspend fun deleteExpiredStatuses() {
        val now = System.currentTimeMillis()
        Collections.statuses.deleteMany(Document("expiresAt", Document("\$lte", now)))
    }
    
    suspend fun deleteStatus(statusId: String, userId: String) {
        Collections.statuses.deleteOne(
            Document("id", statusId).append("userId", userId)
        )
    }
    
    suspend fun getStatusViewers(statusId: String): List<String> {
        val status = Collections.statuses.find(Document("id", statusId)).firstOrNull()
        return status?.viewedBy ?: emptyList()
    }
    
    suspend fun getStatusById(statusId: String): StatusEntity? {
        return Collections.statuses.find(Document("id", statusId)).firstOrNull()
    }
}

// Channel Repository
object ChannelRepository {
    suspend fun createChannel(channel: ChannelEntity): ChannelEntity {
        Collections.channels.insertOne(channel)
        return channel
    }

    suspend fun getChannelById(id: String): ChannelEntity? {
        return Collections.channels.find(Document("id", id)).firstOrNull()
    }

    suspend fun getChannelsByUserId(userId: String): List<ChannelEntity> {
        return Collections.channels
            .find(Document("subscribers", userId))
            .toList()
    }

    suspend fun subscribe(channelId: String, userId: String) {
        Collections.channels.updateOne(
            Document("id", channelId),
            Document("\$addToSet", Document("subscribers", userId))
                .append("\$inc", Document("subscriberCount", 1))
        )
    }

    suspend fun unsubscribe(channelId: String, userId: String) {
        Collections.channels.updateOne(
            Document("id", channelId),
            Document("\$pull", Document("subscribers", userId))
                .append("\$inc", Document("subscriberCount", -1))
        )
    }

    suspend fun searchPublicChannels(query: String): List<ChannelEntity> {
        val regex = Document("\$regex", query).append("\$options", "i")
        return Collections.channels
            .find(Document("isPublic", true).append("name", regex))
            .toList()
    }
}

// Call Repository
object CallRepository {
    suspend fun createCall(call: CallEntity): CallEntity {
        Collections.calls.insertOne(call)
        return call
    }

    suspend fun getCallById(id: String): CallEntity? {
        return Collections.calls.find(Document("id", id)).firstOrNull()
    }

    suspend fun updateCallStatus(callId: String, status: String) {
        val updates = Document("status", status)
        if (status == "ONGOING") {
            updates.append("startedAt", System.currentTimeMillis())
        } else if (status == "ENDED") {
            updates.append("endedAt", System.currentTimeMillis())
        }
        Collections.calls.updateOne(
            Document("id", callId),
            Document("\$set", updates)
        )
    }

    suspend fun getCallHistory(userId: String, limit: Int = 50): List<CallEntity> {
        return Collections.calls
            .find(Document("\$or", listOf(
                Document("callerId", userId),
                Document("receiverId", userId)
            )))
            .limit(limit)
            .toList()
            .sortedByDescending { it.createdAt }
    }
}
