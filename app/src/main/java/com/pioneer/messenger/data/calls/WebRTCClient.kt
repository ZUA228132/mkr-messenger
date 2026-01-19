package com.pioneer.messenger.data.calls

import android.content.Context
import android.util.Log
import com.pioneer.messenger.data.crypto.CryptoManager
import org.webrtc.*

/**
 * WebRTC клиент для аудио/видео звонков с шифрованием
 */
class WebRTCClient(
    private val context: Context,
    private val cryptoManager: CryptoManager
) {
    companion object {
        private const val TAG = "WebRTCClient"
        private const val LOCAL_TRACK_ID = "local_track"
        private const val LOCAL_STREAM_ID = "local_stream"
    }
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    
    private var localSurfaceView: SurfaceViewRenderer? = null
    private var remoteSurfaceView: SurfaceViewRenderer? = null
    
    private var eglBase: EglBase? = null
    
    private var callKey: ByteArray? = null
    
    interface WebRTCListener {
        fun onLocalStream(stream: MediaStream)
        fun onRemoteStream(stream: MediaStream)
        fun onIceCandidate(candidate: IceCandidate)
        fun onConnectionStateChange(state: PeerConnection.PeerConnectionState)
        fun onCallEnded()
    }
    
    private var listener: WebRTCListener? = null
    
    fun setListener(listener: WebRTCListener) {
        this.listener = listener
    }
    
    fun initialize() {
        eglBase = EglBase.create()
        
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        
        PeerConnectionFactory.initialize(options)
        
        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase?.eglBaseContext,
            true,
            true
        )
        
        val decoderFactory = DefaultVideoDecoderFactory(eglBase?.eglBaseContext)
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }
    
    fun initializeSurfaceViews(
        localView: SurfaceViewRenderer,
        remoteView: SurfaceViewRenderer
    ) {
        localSurfaceView = localView
        remoteSurfaceView = remoteView
        
        localView.init(eglBase?.eglBaseContext, null)
        localView.setEnableHardwareScaler(true)
        localView.setMirror(true)
        
        remoteView.init(eglBase?.eglBaseContext, null)
        remoteView.setEnableHardwareScaler(true)
        remoteView.setMirror(false)
    }
    
    fun startLocalVideo(useFrontCamera: Boolean = true) {
        val factory = peerConnectionFactory ?: return
        
        // Создаём видео источник
        val videoSource = factory.createVideoSource(false)
        
        // Создаём capturer
        videoCapturer = createCameraCapturer(useFrontCamera)
        
        surfaceTextureHelper = SurfaceTextureHelper.create(
            "CaptureThread",
            eglBase?.eglBaseContext
        )
        
        videoCapturer?.initialize(
            surfaceTextureHelper,
            context,
            videoSource.capturerObserver
        )
        
        videoCapturer?.startCapture(1280, 720, 30)
        
        localVideoTrack = factory.createVideoTrack(LOCAL_TRACK_ID + "_video", videoSource)
        localVideoTrack?.setEnabled(true)
        localVideoTrack?.addSink(localSurfaceView)
    }
    
    fun startLocalAudio() {
        val factory = peerConnectionFactory ?: return
        
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        
        val audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack(LOCAL_TRACK_ID + "_audio", audioSource)
        localAudioTrack?.setEnabled(true)
    }
    
    private fun createCameraCapturer(useFrontCamera: Boolean): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        
        // Ищем нужную камеру
        for (deviceName in deviceNames) {
            val isFront = enumerator.isFrontFacing(deviceName)
            if (useFrontCamera == isFront) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }
        
        // Fallback на любую камеру
        for (deviceName in deviceNames) {
            val capturer = enumerator.createCapturer(deviceName, null)
            if (capturer != null) return capturer
        }
        
        return null
    }
    
    fun createPeerConnection(sessionKey: ByteArray) {
        callKey = sessionKey
        
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        
        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    Log.d(TAG, "Signaling state: $state")
                }
                
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "ICE connection state: $state")
                }
                
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Log.d(TAG, "ICE gathering state: $state")
                }
                
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let { listener?.onIceCandidate(it) }
                }
                
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                
                override fun onAddStream(stream: MediaStream?) {
                    stream?.let { 
                        listener?.onRemoteStream(it)
                        it.videoTracks?.firstOrNull()?.addSink(remoteSurfaceView)
                    }
                }
                
                override fun onRemoveStream(stream: MediaStream?) {}
                
                override fun onDataChannel(channel: DataChannel?) {}
                
                override fun onRenegotiationNeeded() {
                    Log.d(TAG, "Renegotiation needed")
                }
                
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    streams?.firstOrNull()?.let { stream ->
                        stream.videoTracks?.firstOrNull()?.addSink(remoteSurfaceView)
                    }
                }
                
                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    newState?.let { listener?.onConnectionStateChange(it) }
                }
            }
        )
        
        // Добавляем локальные треки
        localVideoTrack?.let { track ->
            peerConnection?.addTrack(track, listOf(LOCAL_STREAM_ID))
        }
        
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf(LOCAL_STREAM_ID))
        }
    }
    
    fun createOffer(callback: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let { 
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() { callback(it) }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Create offer failed: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }
    
    fun createAnswer(callback: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() { callback(it) }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Create answer failed: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }
    
    fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set successfully")
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Set remote description failed: $error")
            }
        }, sdp)
    }
    
    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }
    
    fun toggleMute(): Boolean {
        val enabled = localAudioTrack?.enabled() ?: true
        localAudioTrack?.setEnabled(!enabled)
        return !enabled
    }
    
    fun toggleVideo(): Boolean {
        val enabled = localVideoTrack?.enabled() ?: true
        localVideoTrack?.setEnabled(!enabled)
        return !enabled
    }
    
    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }
    
    fun release() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null
        
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        
        localVideoTrack?.dispose()
        localVideoTrack = null
        
        localAudioTrack?.dispose()
        localAudioTrack = null
        
        peerConnection?.close()
        peerConnection = null
        
        localSurfaceView?.release()
        remoteSurfaceView?.release()
        
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        
        eglBase?.release()
        eglBase = null
        
        callKey = null
    }
}
