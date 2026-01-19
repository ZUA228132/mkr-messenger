package com.pioneer.messenger.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        MessageEntity::class,
        ChatEntity::class,
        UserEntity::class,
        TaskEntity::class,
        FinanceEntity::class,
        MapMarkerEntity::class,
        MapAreaEntity::class,
        SessionKeyEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PioneerDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    abstract fun userDao(): UserDao
    abstract fun taskDao(): TaskDao
    abstract fun financeDao(): FinanceDao
    abstract fun mapDao(): MapDao
    abstract fun keyDao(): SessionKeyDao
}

// === ENTITIES ===

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val encryptedContent: String,
    val nonce: String,
    val timestamp: Long,
    val type: String,
    val status: String,
    val replyToId: String?,
    val isEdited: Boolean,
    val editedAt: Long?,
    val duration: Int? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val localFilePath: String? = null, // Путь к локальному файлу для медиа
    val fileUrl: String? = null // URL файла на сервере
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val description: String?,
    val avatarUrl: String?,
    val participants: String, // JSON array
    val admins: String,
    val createdAt: Long,
    val encryptionKeyId: String,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val unreadCount: Int,
    val autoDeleteDays: Int? = null
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val publicKey: String,
    val avatarUrl: String?,
    val accessLevel: Int,
    val isOnline: Boolean,
    val lastSeen: Long
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val creatorId: String,
    val assigneeIds: String, // JSON array
    val status: String,
    val priority: String,
    val dueDate: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val chatId: String?,
    val subtasks: String, // JSON array
    val attachments: String // JSON array
)

@Entity(tableName = "finance_records")
data class FinanceEntity(
    @PrimaryKey val id: String,
    val type: String,
    val category: String,
    val amount: Double,
    val currency: String,
    val description: String,
    val createdBy: String,
    val createdAt: Long,
    val tags: String // JSON array
)

@Entity(tableName = "map_markers")
data class MapMarkerEntity(
    @PrimaryKey val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val description: String?,
    val type: String,
    val icon: String?,
    val color: String,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
    val accessLevel: Int,
    val visibleTo: String?, // JSON array
    val metadata: String // JSON object
)

@Entity(tableName = "map_areas")
data class MapAreaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val points: String, // JSON array of GeoPoints
    val fillColor: String,
    val strokeColor: String,
    val createdBy: String,
    val createdAt: Long,
    val accessLevel: Int,
    val visibleTo: String?
)

@Entity(tableName = "session_keys")
data class SessionKeyEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val encryptedKey: String,
    val iv: String,
    val createdAt: Long,
    val expiresAt: Long?
)

// === DAOs ===

@Dao
interface MessageDao {
    // Для списка сообщений - загружаем все поля
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun getMessagesByChat(chatId: String): Flow<List<MessageEntity>>
    
    // Для получения полных данных конкретного сообщения (включая медиа)
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: String): MessageEntity?
    
    // Получить недавние сообщения от конкретного отправителя (для проверки дубликатов)
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND senderId = :senderId AND timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getRecentMessagesBySender(chatId: String, senderId: String, since: Long): List<MessageEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)
    
    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)
    
    @Query("UPDATE messages SET localFilePath = :path WHERE id = :messageId")
    suspend fun updateLocalFilePath(messageId: String, path: String)
    
    @Delete
    suspend fun deleteMessage(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)
    
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChat(chatId: String)
    
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY isPinned DESC")
    fun getAllChats(): Flow<List<ChatEntity>>
    
    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getChatById(id: String): ChatEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)
    
    @Update
    suspend fun updateChat(chat: ChatEntity)
    
    @Delete
    suspend fun deleteChat(chat: ChatEntity)
    
    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>
    
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: String): UserEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    
    @Update
    suspend fun updateUser(user: UserEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY dueDate ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>
    
    @Query("SELECT * FROM tasks WHERE status != 'COMPLETED' ORDER BY priority DESC, dueDate ASC")
    fun getActiveTasks(): Flow<List<TaskEntity>>
    
    @Query("SELECT * FROM tasks WHERE assigneeIds LIKE '%' || :userId || '%'")
    fun getTasksByAssignee(userId: String): Flow<List<TaskEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)
    
    @Update
    suspend fun updateTask(task: TaskEntity)
    
    @Delete
    suspend fun deleteTask(task: TaskEntity)
}

@Dao
interface FinanceDao {
    @Query("SELECT * FROM finance_records ORDER BY createdAt DESC")
    fun getAllRecords(): Flow<List<FinanceEntity>>
    
    @Query("SELECT * FROM finance_records WHERE createdAt BETWEEN :startDate AND :endDate")
    fun getRecordsByPeriod(startDate: Long, endDate: Long): Flow<List<FinanceEntity>>
    
    @Query("SELECT SUM(amount) FROM finance_records WHERE type = 'INCOME' AND createdAt BETWEEN :startDate AND :endDate")
    suspend fun getTotalIncome(startDate: Long, endDate: Long): Double?
    
    @Query("SELECT SUM(amount) FROM finance_records WHERE type = 'EXPENSE' AND createdAt BETWEEN :startDate AND :endDate")
    suspend fun getTotalExpense(startDate: Long, endDate: Long): Double?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: FinanceEntity)
    
    @Delete
    suspend fun deleteRecord(record: FinanceEntity)
}

@Dao
interface MapDao {
    @Query("SELECT * FROM map_markers WHERE accessLevel <= :userLevel")
    fun getMarkersByAccess(userLevel: Int): Flow<List<MapMarkerEntity>>
    
    @Query("SELECT * FROM map_areas WHERE accessLevel <= :userLevel")
    fun getAreasByAccess(userLevel: Int): Flow<List<MapAreaEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarker(marker: MapMarkerEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArea(area: MapAreaEntity)
    
    @Update
    suspend fun updateMarker(marker: MapMarkerEntity)
    
    @Update
    suspend fun updateArea(area: MapAreaEntity)
    
    @Delete
    suspend fun deleteMarker(marker: MapMarkerEntity)
    
    @Delete
    suspend fun deleteArea(area: MapAreaEntity)
}

@Dao
interface SessionKeyDao {
    @Query("SELECT * FROM session_keys WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getKeyForChat(chatId: String): SessionKeyEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: SessionKeyEntity)
    
    @Query("DELETE FROM session_keys WHERE expiresAt < :now")
    suspend fun deleteExpiredKeys(now: Long)
}

// === TYPE CONVERTERS ===

class Converters {
    @TypeConverter
    fun fromStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList()
        else value.split(",")
    }
    
    @TypeConverter
    fun toStringList(list: List<String>): String {
        return list.joinToString(",")
    }
}
