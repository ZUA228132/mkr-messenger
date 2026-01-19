package com.pioneer.messenger.data.webrtc

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import org.webrtc.*
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * WebRTC клиент для аудио/видео звонков
 */
class WebRTCClient(
    private val context: Context
) {
    companion object {
        private const val WS_URL = "wss://kluboksrm.ru/call"
        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var webSocket: WebSocket? = null
    
    private var currentUserId: String? = null
    private var currentCallId: String? = null
    
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()
    
    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()
    
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    
    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()
    
    // Callback для входящих звонков
    var onIncomingCall: ((String, String, Boolean) -> Unit)? = null
    
    init {
        initializePeerConnectionFactory()
    }
    
    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        
        val encoderFactory = DefaultVideoEncoderFactory(
            EglBase.create().eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(EglBase.create().eglBaseContext)
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }
    
    /**
     * Подключиться к сигнальному серверу
     */
    fun connect(userId: String) {
        currentUserId = userId
        
        // Trust all certs для самоподписанного сертификата
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
        
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
        
        val request = Request.Builder()
            .url("$WS_URL/$userId")
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _callState.value = CallState.Connected
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleSignalingMessage(text)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _callState.value = CallState.Error(t.message ?: "Connection failed")
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _callState.value = CallState.Idle
            }
        })
    }
    
    /**
     * Начать исходящий звонок
     */
    fun startCall(calleeId: String, isVideo: Boolean) {
        currentCallId = UUID.randomUUID().toString()
        _callState.value = CallState.Calling(calleeId, isVideo)
        
        createPeerConnection()
        createLocalTracks(isVideo)
        
        // Создаём offer
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo.toString()))
        }
        
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        // Отправляем offer на сервер
                        sendOffer(calleeId, isVideo, sdp.description)
                    }
                    override fun onSetFailure(error: String?) {}
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                _callState.value = CallState.Error(error ?: "Failed to create offer")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }
    
    /**
     * Принять входящий звонок
     */
    fun acceptCall(callId: String, remoteSdp: String, isVideo: Boolean) {
        currentCallId = callId
        _callState.value = CallState.InCall(isVideo)
        
        createPeerConnection()
        createLocalTracks(isVideo)
        
        // Устанавливаем remote description
        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, remoteSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                // Создаём answer
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo.toString()))
                }
                
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) {
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                sendAnswer(sdp.description)
                            }
                            override fun onSetFailure(error: String?) {}
                            override fun onCreateSuccess(sdp: SessionDescription?) {}
                            override fun onCreateFailure(error: String?) {}
                        }, sdp)
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {}
                }, constraints)
            }
            override fun onSetFailure(error: String?) {}
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, remoteDesc)
    }
    
    /**
     * Отклонить входящий звонок
     */
    fun rejectCall(callId: String) {
        sendMessage(mapOf(
            "type" to "reject",
            "callId" to callId
        ))
        _callState.value = CallState.Idle
    }
    
    /**
     * Завершить звонок
     */
    fun endCall() {
        currentCallId?.let { callId ->
            sendMessage(mapOf(
                "type" to "end",
                "callId" to callId
            ))
        }
        cleanup()
        _callState.value = CallState.Idle
    }
    
    /**
     * Включить/выключить микрофон
     */
    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        localAudioTrack?.setEnabled(!_isMuted.value)
    }
    
    /**
     * Включить/выключить видео
     */
    fun toggleVideo() {
        _isVideoEnabled.value = !_isVideoEnabled.value
        localVideoTrack?.setEnabled(_isVideoEnabled.value)
    }
    
    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        
        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    sendIceCandidate(candidate)
                }
                
                override fun onTrack(transceiver: RtpTransceiver) {
                    val track = transceiver.receiver.track()
                    if (track is VideoTrack) {
                        scope.launch(Dispatchers.Main) {
                            _remoteVideoTrack.value = track
                        }
                    }
                }
                
                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            val isVideo = _callState.value.let { 
                                it is CallState.Calling && it.isVideo || 
                                it is CallState.InCall && it.isVideo 
                            }
                            _callState.value = CallState.InCall(isVideo)
                        }
                        PeerConnection.PeerConnectionState.DISCONNECTED,
                        PeerConnection.PeerConnectionState.FAILED -> {
                            cleanup()
                            _callState.value = CallState.Idle
                        }
                        else -> {}
                    }
                }
                
                override fun onSignalingChange(state: PeerConnection.SignalingState) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
                override fun onAddStream(stream: MediaStream) {}
                override fun onRemoveStream(stream: MediaStream) {}
                override fun onDataChannel(channel: DataChannel) {}
                override fun onRenegotiationNeeded() {}
            }
        )
    }
    
    private fun createLocalTracks(isVideo: Boolean) {
        // Аудио трек
        val audioConstraints = MediaConstraints()
        val audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio0", audioSource)
        localAudioTrack?.let { peerConnection?.addTrack(it) }
        
        // Видео трек (если нужен)
        if (isVideo) {
            val videoCapturer = createCameraCapturer()
            videoCapturer?.let { capturer ->
                val surfaceTextureHelper = SurfaceTextureHelper.create(
                    "CaptureThread",
                    EglBase.create().eglBaseContext
                )
                val videoSource = peerConnectionFactory?.createVideoSource(capturer.isScreencast)
                capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
                capturer.startCapture(1280, 720, 30)
                
                localVideoTrack = peerConnectionFactory?.createVideoTrack("video0", videoSource)
                localVideoTrack?.let { peerConnection?.addTrack(it) }
            }
        }
    }
    
    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        
        // Сначала пробуем фронтальную камеру
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        
        // Если нет фронтальной, используем заднюю
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        
        return null
    }
    
    private fun handleSignalingMessage(message: String) {
        try {
            val data = json.decodeFromString<Map<String, String>>(message)
            val type = data["type"] ?: return
            
            when (type) {
                "incoming_call" -> {
                    val callId = data["callId"] ?: return
                    val eventData = data["data"]?.let { 
                        json.decodeFromString<Map<String, String>>(it) 
                    } ?: return
                    
                    val callerId = eventData["callerId"] ?: return
                    val isVideo = eventData["isVideo"]?.toBoolean() ?: false
                    val sdp = eventData["sdp"] ?: return
                    
                    _callState.value = CallState.Incoming(callId, callerId, isVideo, sdp)
                    onIncomingCall?.invoke(callId, callerId, isVideo)
                }
                
                "call_accepted" -> {
                    val sdp = data["data"] ?: return
                    val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            val isVideo = (_callState.value as? CallState.Calling)?.isVideo ?: false
                            _callState.value = CallState.InCall(isVideo)
                        }
                        override fun onSetFailure(error: String?) {}
                        override fun onCreateSuccess(sdp: SessionDescription?) {}
                        override fun onCreateFailure(error: String?) {}
                    }, remoteDesc)
                }
                
                "call_rejected" -> {
                    cleanup()
                    _callState.value = CallState.Rejected
                }
                
                "call_ended" -> {
                    cleanup()
                    _callState.value = CallState.Idle
                }
                
                "ice_candidate" -> {
                    val candidateData = data["data"]?.let {
                        json.decodeFromString<Map<String, String>>(it)
                    } ?: return
                    
                    val candidate = IceCandidate(
                        candidateData["sdpMid"],
                        candidateData["sdpMLineIndex"]?.toIntOrNull() ?: 0,
                        candidateData["candidate"] ?: return
                    )
                    peerConnection?.addIceCandidate(candidate)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun sendOffer(calleeId: String, isVideo: Boolean, sdp: String) {
        sendMessage(mapOf(
            "type" to "offer",
            "callId" to (currentCallId ?: ""),
            "callerId" to (currentUserId ?: ""),
            "calleeId" to calleeId,
            "isVideo" to isVideo.toString(),
            "sdp" to sdp
        ))
    }
    
    private fun sendAnswer(sdp: String) {
        sendMessage(mapOf(
            "type" to "answer",
            "callId" to (currentCallId ?: ""),
            "sdp" to sdp
        ))
    }
    
    private fun sendIceCandidate(candidate: IceCandidate) {
        sendMessage(mapOf(
            "type" to "ice_candidate",
            "callId" to (currentCallId ?: ""),
            "candidate" to candidate.sdp,
            "sdpMid" to (candidate.sdpMid ?: ""),
            "sdpMLineIndex" to candidate.sdpMLineIndex.toString()
        ))
    }
    
    private fun sendMessage(data: Map<String, String>) {
        webSocket?.send(json.encodeToString(data))
    }
    
    private fun cleanup() {
        localAudioTrack?.dispose()
        localVideoTrack?.dispose()
        peerConnection?.close()
        peerConnection = null
        localAudioTrack = null
        localVideoTrack = null
        _remoteVideoTrack.value = null
        currentCallId = null
    }
    
    fun disconnect() {
        cleanup()
        webSocket?.close(1000, "Disconnecting")
        webSocket = null
    }
}

sealed class CallState {
    object Idle : CallState()
    object Connected : CallState()
    data class Calling(val calleeId: String, val isVideo: Boolean) : CallState()
    data class Incoming(val callId: String, val callerId: String, val isVideo: Boolean, val sdp: String) : CallState()
    data class InCall(val isVideo: Boolean) : CallState()
    object Rejected : CallState()
    data class Error(val message: String) : CallState()
}
