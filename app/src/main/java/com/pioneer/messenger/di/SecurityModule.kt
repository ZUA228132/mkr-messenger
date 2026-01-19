package com.pioneer.messenger.di

import android.content.Context
import com.pioneer.messenger.data.security.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Модуль Hilt для компонентов безопасности
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    
    @Provides
    @Singleton
    fun provideSecureDataManager(
        @ApplicationContext context: Context
    ): SecureDataManager {
        return SecureDataManager(context)
    }
    
    @Provides
    @Singleton
    fun provideSecurityManager(
        @ApplicationContext context: Context
    ): SecurityManager {
        return SecurityManager(context)
    }
    
    @Provides
    @Singleton
    fun provideMediaEncryption(
        @ApplicationContext context: Context,
        secureDataManager: SecureDataManager
    ): MediaEncryption {
        return MediaEncryption(context, secureDataManager)
    }
    
    @Provides
    @Singleton
    fun provideNetworkSecurity(
        @ApplicationContext context: Context
    ): NetworkSecurity {
        return NetworkSecurity(context)
    }
    
    @Provides
    @Singleton
    fun provideBiometricAuthManager(
        @ApplicationContext context: Context
    ): BiometricAuthManager {
        return BiometricAuthManager(context)
    }
    
    @Provides
    @Singleton
    fun provideSecureKeyboard(
        @ApplicationContext context: Context
    ): SecureKeyboard {
        return SecureKeyboard(context)
    }
    
    @Provides
    @Singleton
    fun provideStealthMode(
        @ApplicationContext context: Context
    ): StealthMode {
        return StealthMode(context)
    }
    
    @Provides
    @Singleton
    fun provideAutoDeleteManager(
        @ApplicationContext context: Context
    ): AutoDeleteManager {
        return AutoDeleteManager(context)
    }
}
