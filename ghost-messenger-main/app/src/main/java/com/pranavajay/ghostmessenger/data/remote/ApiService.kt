package com.pranavajay.ghostmessenger.data.remote

import com.pranavajay.ghostmessenger.data.models.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    // Auth
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>
    
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
    
    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body request: OtpRequest): Response<AuthResponse>
    
    @POST("api/auth/google")
    suspend fun googleAuth(@Body token: Map<String, String>): Response<AuthResponse>
    
    @GET("api/users/me")
    suspend fun getCurrentUser(@Header("Authorization") token: String): Response<User>
    
    // Chats
    @GET("api/chats")
    suspend fun getChats(@Header("Authorization") token: String): Response<List<Chat>>
    
    @POST("api/chats")
    suspend fun createChat(
        @Header("Authorization") token: String,
        @Body request: CreateChatRequest
    ): Response<Chat>
    
    @PUT("api/chats/{chatId}/disappearing")
    suspend fun setDisappearingMessages(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: String,
        @Body request: DisappearingSettingsRequest
    ): Response<Map<String, Any>>
    
    @PUT("api/chats/{chatId}/ghost-mode")
    suspend fun setGhostMode(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: String,
        @Body request: GhostModeRequest
    ): Response<Map<String, Any>>
    
    @PUT("api/chats/{chatId}/pin")
    suspend fun pinChat(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: String,
        @Body request: PinChatRequest
    ): Response<Map<String, Any>>
    
    @PUT("api/chats/{chatId}/archive")
    suspend fun archiveChat(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: String,
        @Body request: ArchiveChatRequest
    ): Response<Map<String, Any>>
    
    // Messages
    @GET("api/messages/{chatId}")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: String
    ): Response<List<Message>>
    
    @POST("api/messages")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body message: SendMessageRequest
    ): Response<Message>
    
    @POST("api/messages/forward")
    suspend fun forwardMessage(
        @Header("Authorization") token: String,
        @Body request: ForwardMessageRequest
    ): Response<Map<String, Int>>
    
    @DELETE("api/messages/{messageId}")
    suspend fun deleteMessage(
        @Header("Authorization") token: String,
        @Path("messageId") messageId: String,
        @Query("forEveryone") forEveryone: Boolean = false
    ): Response<Map<String, Boolean>>
    
    // Reactions
    @POST("api/reactions")
    suspend fun addReaction(
        @Header("Authorization") token: String,
        @Body request: AddReactionRequest
    ): Response<Map<String, Any>>
    
    @HTTP(method = "DELETE", path = "api/reactions", hasBody = true)
    suspend fun removeReaction(
        @Header("Authorization") token: String,
        @Body request: RemoveReactionRequest
    ): Response<Map<String, Any>>
    
    // Calls
    @POST("api/calls/initiate")
    suspend fun initiateCall(
        @Header("Authorization") token: String,
        @Body request: InitiateCallRequest
    ): Response<CallResponse>
    
    @POST("api/calls/{callId}/accept")
    suspend fun acceptCall(
        @Header("Authorization") token: String,
        @Path("callId") callId: String
    ): Response<Map<String, Any>>
    
    @POST("api/calls/{callId}/decline")
    suspend fun declineCall(
        @Header("Authorization") token: String,
        @Path("callId") callId: String
    ): Response<Map<String, Any>>
    
    @POST("api/calls/{callId}/end")
    suspend fun endCall(
        @Header("Authorization") token: String,
        @Path("callId") callId: String
    ): Response<Map<String, Any>>
    
    @GET("api/calls/history")
    suspend fun getCallHistory(
        @Header("Authorization") token: String
    ): Response<List<CallEntity>>
    
    // Search
    @GET("api/search")
    suspend fun globalSearch(
        @Header("Authorization") token: String,
        @Query("q") query: String,
        @Query("type") type: String? = null
    ): Response<SearchResult>
    
    @GET("api/search/messages")
    suspend fun searchMessages(
        @Header("Authorization") token: String,
        @Query("q") query: String,
        @Query("chatId") chatId: String? = null
    ): Response<List<Message>>
    
    // Channels
    @GET("api/channels")
    suspend fun getChannels(
        @Header("Authorization") token: String
    ): Response<List<Channel>>
    
    @POST("api/channels")
    suspend fun createChannel(
        @Header("Authorization") token: String,
        @Body request: CreateChannelRequest
    ): Response<Channel>
    
    @POST("api/channels/{channelId}/subscribe")
    suspend fun subscribeChannel(
        @Header("Authorization") token: String,
        @Path("channelId") channelId: String
    ): Response<Map<String, Any>>
    
    @POST("api/channels/{channelId}/unsubscribe")
    suspend fun unsubscribeChannel(
        @Header("Authorization") token: String,
        @Path("channelId") channelId: String
    ): Response<Map<String, Any>>
    
    @GET("api/channels/search")
    suspend fun searchChannels(
        @Header("Authorization") token: String,
        @Query("q") query: String
    ): Response<List<Channel>>
    
    // Upload
    @Multipart
    @POST("api/upload")
    suspend fun uploadFile(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): Response<Map<String, String>>
    
    // Users
    @GET("api/users/search")
    suspend fun searchUsers(
        @Header("Authorization") token: String,
        @Query("q") query: String
    ): Response<List<User>>
    
    @PUT("api/users/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<User>
    
    @PUT("api/users/fcm-token")
    suspend fun updateFcmToken(
        @Header("Authorization") token: String,
        @Body request: Map<String, String>
    ): Response<Map<String, Any>>
    
    // Status/Stories
    @GET("api/status/me")
    suspend fun getMyStatuses(
        @Header("Authorization") token: String
    ): Response<List<Status>>
    
    @GET("api/status")
    suspend fun getContactsStatuses(
        @Header("Authorization") token: String
    ): Response<List<Status>>
    
    @POST("api/status")
    suspend fun createStatus(
        @Header("Authorization") token: String,
        @Body request: CreateStatusRequest
    ): Response<Status>
    
    @POST("api/status/{statusId}/view")
    suspend fun viewStatus(
        @Header("Authorization") token: String,
        @Path("statusId") statusId: String
    ): Response<Map<String, Any>>
    
    @POST("api/status/{statusId}/react")
    suspend fun reactToStatus(
        @Header("Authorization") token: String,
        @Path("statusId") statusId: String,
        @Body request: Map<String, String>
    ): Response<Map<String, Any>>
    
    @DELETE("api/status/{statusId}")
    suspend fun deleteStatus(
        @Header("Authorization") token: String,
        @Path("statusId") statusId: String
    ): Response<Map<String, Any>>
    
    // Groups
    @GET("api/groups")
    suspend fun getGroups(
        @Header("Authorization") token: String
    ): Response<List<Group>>
    
    @POST("api/groups")
    suspend fun createGroup(
        @Header("Authorization") token: String,
        @Body request: CreateGroupRequest
    ): Response<Group>
    
    @GET("api/groups/{groupId}")
    suspend fun getGroup(
        @Header("Authorization") token: String,
        @Path("groupId") groupId: String
    ): Response<Group>
    
    @PUT("api/groups/{groupId}")
    suspend fun updateGroup(
        @Header("Authorization") token: String,
        @Path("groupId") groupId: String,
        @Body request: UpdateGroupRequest
    ): Response<Group>
    
    @POST("api/groups/{groupId}/members")
    suspend fun addGroupMember(
        @Header("Authorization") token: String,
        @Path("groupId") groupId: String,
        @Body request: Map<String, String>
    ): Response<Map<String, Any>>
    
    @DELETE("api/groups/{groupId}/members/{memberId}")
    suspend fun removeGroupMember(
        @Header("Authorization") token: String,
        @Path("groupId") groupId: String,
        @Path("memberId") memberId: String
    ): Response<Map<String, Any>>
    
    @POST("api/groups/{groupId}/leave")
    suspend fun leaveGroup(
        @Header("Authorization") token: String,
        @Path("groupId") groupId: String
    ): Response<Map<String, Any>>
}
