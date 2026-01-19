package com.pioneer.messenger.di

import android.content.Context
import androidx.room.Room
import com.pioneer.messenger.data.calls.LiveKitClient
import com.pioneer.messenger.data.calls.WebRTCClient as CallsWebRTCClient
import com.pioneer.messenger.data.webrtc.WebRTCClient as WebRTCSignalingClient
import com.pioneer.messenger.data.crypto.CryptoManager
import com.pioneer.messenger.data.local.PioneerDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PioneerDatabase {
        return Room.databaseBuilder(
            context,
            PioneerDatabase::class.java,
            "pioneer_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    fun provideMessageDao(database: PioneerDatabase) = database.messageDao()
    
    @Provides
    fun provideChatDao(database: PioneerDatabase) = database.chatDao()
    
    @Provides
    fun provideUserDao(database: PioneerDatabase) = database.userDao()
    
    @Provides
    fun provideTaskDao(database: PioneerDatabase) = database.taskDao()
    
    @Provides
    fun provideFinanceDao(database: PioneerDatabase) = database.financeDao()
    
    @Provides
    fun provideMapDao(database: PioneerDatabase) = database.mapDao()
    
    @Provides
    fun provideKeyDao(database: PioneerDatabase) = database.keyDao()
    
    @Provides
    @Singleton
    fun provideLiveKitClient(
        @ApplicationContext context: Context
    ): LiveKitClient {
        return LiveKitClient(context)
    }
    
    @Provides
    @Singleton
    fun provideCallsWebRTCClient(
        @ApplicationContext context: Context,
        cryptoManager: CryptoManager
    ): CallsWebRTCClient {
        return CallsWebRTCClient(context, cryptoManager)
    }
    
    @Provides
    @Singleton
    fun provideWebRTCSignalingClient(
        @ApplicationContext context: Context
    ): WebRTCSignalingClient {
        return WebRTCSignalingClient(context)
    }
}
