package com.pioneer.messenger.data.crypto

import android.util.Base64
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * X25519 Key Exchange для безопасного обмена ключами между пользователями
 */
@Singleton
class KeyExchange @Inject constructor() {
    
    companion object {
        private const val KEY_ALGORITHM = "X25519"
        private const val KEY_AGREEMENT_ALGORITHM = "X25519"
    }
    
    data class KeyPairData(
        val publicKey: String,
        val privateKey: ByteArray
    )
    
    // Генерация пары ключей для пользователя
    fun generateKeyPair(): KeyPairData {
        val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        val keyPair = keyPairGenerator.generateKeyPair()
        
        return KeyPairData(
            publicKey = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP),
            privateKey = keyPair.private.encoded
        )
    }
    
    // Вычисление общего секрета (Diffie-Hellman)
    fun computeSharedSecret(privateKey: ByteArray, otherPublicKey: String): ByteArray {
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        
        // Восстановление приватного ключа
        val privateKeySpec = java.security.spec.PKCS8EncodedKeySpec(privateKey)
        val privKey = keyFactory.generatePrivate(privateKeySpec)
        
        // Восстановление публичного ключа другой стороны
        val publicKeyBytes = Base64.decode(otherPublicKey, Base64.NO_WRAP)
        val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)
        val pubKey = keyFactory.generatePublic(publicKeySpec)
        
        // Key Agreement
        val keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM)
        keyAgreement.init(privKey)
        keyAgreement.doPhase(pubKey, true)
        
        // Derive key using HKDF
        val sharedSecret = keyAgreement.generateSecret()
        return deriveKey(sharedSecret, 32)
    }
    
    // HKDF для деривации ключа
    private fun deriveKey(inputKey: ByteArray, length: Int): ByteArray {
        val hmac = javax.crypto.Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(inputKey, "HmacSHA256"))
        
        val info = "pioneer-chat-key".toByteArray()
        val result = ByteArray(length)
        var offset = 0
        var counter: Byte = 1
        
        while (offset < length) {
            hmac.reset()
            if (offset > 0) {
                hmac.update(result, offset - 32, 32)
            }
            hmac.update(info)
            hmac.update(counter)
            
            val block = hmac.doFinal()
            val toCopy = minOf(block.size, length - offset)
            System.arraycopy(block, 0, result, offset, toCopy)
            offset += toCopy
            counter++
        }
        
        return result
    }
    
    // Создание подписи для верификации
    fun sign(data: ByteArray, privateKey: ByteArray): String {
        // Используем Ed25519 для подписи
        val keyFactory = KeyFactory.getInstance("Ed25519")
        val privateKeySpec = java.security.spec.PKCS8EncodedKeySpec(privateKey)
        val privKey = keyFactory.generatePrivate(privateKeySpec)
        
        val signature = Signature.getInstance("Ed25519")
        signature.initSign(privKey)
        signature.update(data)
        
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }
    
    // Верификация подписи
    fun verify(data: ByteArray, signatureStr: String, publicKey: String): Boolean {
        return try {
            val keyFactory = KeyFactory.getInstance("Ed25519")
            val publicKeyBytes = Base64.decode(publicKey, Base64.NO_WRAP)
            val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)
            val pubKey = keyFactory.generatePublic(publicKeySpec)
            
            val signature = Signature.getInstance("Ed25519")
            signature.initVerify(pubKey)
            signature.update(data)
            
            val signatureBytes = Base64.decode(signatureStr, Base64.NO_WRAP)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
}
