package com.pioneer.messenger.data.network

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * WebSocket клиент для real-time коммуникации
 */
class WebSocketClient(
    private val userId: String
) {
    private val client: OkHttpClient
    
    init {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        
        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }
    
    private var webSocket: WebSocket? = null
    private val messageChannel = Channel<WsMessage>(Channel.BUFFERED)
    private val json = Json { ignoreUnknownKeys = true }
    
    val messages: Flow<WsMessage> = messageChannel.receiveAsFlow()
    
    @Serializable
    data class WsMessage(
        val type: String,
        val payload: String
    )
    
    fun connect() {
        val request = Request.Builder()
            .url("${ApiClient.WS_URL}/$userId")
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Connected
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = json.decodeFromString<WsMessage>(text)
                    CoroutineScope(Dispatchers.IO).launch {
                        messageChannel.send(message)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
                // Reconnect after delay
                CoroutineScope(Dispatchers.IO).launch {
                    delay(5000)
                    connect()
                }
            }
        })
    }
    
    fun send(type: String, payload: String) {
        val message = WsMessage(type, payload)
        webSocket?.send(json.encodeToString(message))
    }
    
    fun sendTyping(chatId: String) {
        send("typing", """{"chatId":"$chatId","userId":"$userId"}""")
    }
    
    fun sendCallOffer(targetUserId: String, sdp: String) {
        send("call_offer", """{"targetUserId":"$targetUserId","sdp":"$sdp"}""")
    }
    
    fun sendCallAnswer(targetUserId: String, sdp: String) {
        send("call_answer", """{"targetUserId":"$targetUserId","sdp":"$sdp"}""")
    }
    
    fun sendIceCandidate(targetUserId: String, candidate: String) {
        send("call_ice", """{"targetUserId":"$targetUserId","candidate":"$candidate"}""")
    }
    
    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }
}
