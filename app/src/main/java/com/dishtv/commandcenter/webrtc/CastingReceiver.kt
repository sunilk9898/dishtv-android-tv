package com.dishtv.commandcenter.webrtc

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import com.dishtv.commandcenter.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.*
/**
 * DishTV AI Command Center — WebRTC Casting Receiver
 *
 * Acts as a WebRTC receiver for live casting sessions.
 * When the backend initiates a casting session (e.g., HOD sharing their laptop screen),
 * this receiver joins the session and displays the incoming video stream on the TV.
 *
 * Flow:
 * 1. Backend sends SCREEN_CAST_RECEIVE command via WebSocket with session details
 * 2. CastingReceiver initializes PeerConnection
 * 3. Exchanges SDP offer/answer via signaling (WebSocket)
 * 4. Receives video stream and renders on SurfaceView
 * 5. Session ends when backend sends stop command
 */
class CastingReceiver(
    private val context: Context
) {
    companion object {
        private const val TAG = "CastingReceiver"
    }

    // ─── State ─────────────────────────────────────────────
    enum class CastingState {
        IDLE, CONNECTING, RECEIVING, ERROR, DISCONNECTED
    }

    private val _state = MutableStateFlow(CastingState.IDLE)
    val state: StateFlow<CastingState> = _state.asStateFlow()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var eglBase: EglBase? = null
    private var surfaceViewRenderer: SurfaceViewRenderer? = null
    private var currentSessionToken: String? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ICE servers (STUN/TURN for NAT traversal)
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        // Add TURN server for production:
        // PeerConnection.IceServer.builder("turn:turn.dishtv.com:3478")
        //     .setUsername("dishtv").setPassword("secret").createIceServer()
    )

    // Callback for sending signaling messages back to server
    var onSignalingMessage: ((String) -> Unit)? = null

    // ─── Initialize WebRTC ─────────────────────────────────
    fun initialize() {
        Log.i(TAG, "Initializing WebRTC engine")

        try {
            // Initialize WebRTC
            val initOptions = PeerConnectionFactory.InitializationOptions
                .builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            // Create EGL context for hardware-accelerated rendering
            eglBase = EglBase.create()

            // Build PeerConnectionFactory
            val options = PeerConnectionFactory.Options()
            val encoderFactory = DefaultVideoEncoderFactory(
                eglBase!!.eglBaseContext, true, true
            )
            val decoderFactory = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()

            Log.i(TAG, "WebRTC engine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC: ${e.message}", e)
            _state.value = CastingState.ERROR
        }
    }

    // ─── Start Receiving Cast Session ──────────────────────
    fun startReceiving(
        sessionToken: String,
        surfaceView: SurfaceViewRenderer,
        sdpOffer: String? = null
    ) {
        Log.i(TAG, "Starting casting session: $sessionToken")

        currentSessionToken = sessionToken
        surfaceViewRenderer = surfaceView
        _state.value = CastingState.CONNECTING

        try {
            // Initialize the surface renderer
            surfaceView.init(eglBase!!.eglBaseContext, null)
            surfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            surfaceView.setMirror(false)

            // Create PeerConnection
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            peerConnection = peerConnectionFactory?.createPeerConnection(
                rtcConfig,
                createPeerConnectionObserver(surfaceView)
            )

            // Add receive-only transceivers
            peerConnection?.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            )
            peerConnection?.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            )

            // If we received an SDP offer, set it and create answer
            if (sdpOffer != null) {
                handleRemoteOffer(sdpOffer)
            }

            Log.i(TAG, "PeerConnection created, waiting for media stream")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start receiving: ${e.message}", e)
            _state.value = CastingState.ERROR
        }
    }

    // ─── Handle Remote SDP Offer ───────────────────────────
    fun handleRemoteOffer(sdpOffer: String) {
        Log.d(TAG, "Setting remote SDP offer")

        val offer = SessionDescription(SessionDescription.Type.OFFER, sdpOffer)

        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set successfully, creating answer")
                createAnswer()
            }

            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote description: $error")
                _state.value = CastingState.ERROR
            }

            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, offer)
    }

    // ─── Create SDP Answer ─────────────────────────────────
    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let { answer ->
                    Log.d(TAG, "SDP answer created, setting local description")

                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description set, sending answer to server")
                            // Send the SDP answer back to the server via signaling
                            val signalingMsg = """
                                {
                                    "type": "sdp_answer",
                                    "session_token": "$currentSessionToken",
                                    "sdp": "${answer.description.replace("\n", "\\n").replace("\r", "\\r")}"
                                }
                            """.trimIndent()
                            onSignalingMessage?.invoke(signalingMsg)
                        }

                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to set local description: $error")
                        }

                        override fun onCreateSuccess(sdp: SessionDescription?) {}
                        override fun onCreateFailure(error: String?) {}
                    }, answer)
                }
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create answer: $error")
                _state.value = CastingState.ERROR
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    // ─── Handle ICE Candidate from Server ──────────────────
    fun handleRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        Log.d(TAG, "Adding remote ICE candidate")
        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        peerConnection?.addIceCandidate(iceCandidate)
    }

    // ─── Create PeerConnection Observer ────────────────────
    private fun createPeerConnectionObserver(surfaceView: SurfaceViewRenderer): PeerConnection.Observer {
        return object : PeerConnection.Observer {

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "Local ICE candidate generated")
                    val signalingMsg = """
                        {
                            "type": "ice_candidate",
                            "session_token": "$currentSessionToken",
                            "sdp_mid": "${it.sdpMid}",
                            "sdp_m_line_index": ${it.sdpMLineIndex},
                            "candidate": "${it.sdp}"
                        }
                    """.trimIndent()
                    onSignalingMessage?.invoke(signalingMsg)
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.i(TAG, "ICE connection state: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        _state.value = CastingState.RECEIVING
                        Log.i(TAG, "Casting session CONNECTED - receiving stream")
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED -> {
                        _state.value = CastingState.DISCONNECTED
                        Log.w(TAG, "Casting session disconnected/failed")
                    }
                    PeerConnection.IceConnectionState.CLOSED -> {
                        _state.value = CastingState.IDLE
                    }
                    else -> {}
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                Log.i(TAG, "Received track: ${track?.kind()}")

                if (track is VideoTrack) {
                    scope.launch {
                        track.addSink(surfaceView)
                        Log.i(TAG, "Video track attached to surface renderer")
                    }
                }
            }

            override fun onAddStream(stream: MediaStream?) {
                Log.i(TAG, "Stream added: ${stream?.id}, video tracks: ${stream?.videoTracks?.size}")
                stream?.videoTracks?.firstOrNull()?.let { videoTrack ->
                    scope.launch {
                        videoTrack.addSink(surfaceView)
                        Log.i(TAG, "Video stream attached to surface renderer")
                    }
                }
            }

            // Required observer methods
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "Signaling state: $state")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "ICE gathering state: $state")
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed")
            }
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        }
    }

    // ─── Stop Receiving ────────────────────────────────────
    fun stopReceiving() {
        Log.i(TAG, "Stopping casting session")

        try {
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            surfaceViewRenderer?.release()
            surfaceViewRenderer = null

            currentSessionToken = null
            _state.value = CastingState.IDLE

            Log.i(TAG, "Casting session stopped cleanly")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping casting: ${e.message}", e)
        }
    }

    // ─── Cleanup ───────────────────────────────────────────
    fun release() {
        Log.i(TAG, "Releasing WebRTC resources")

        stopReceiving()

        try {
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null

            eglBase?.release()
            eglBase = null

            scope.cancel()
            Log.i(TAG, "WebRTC resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WebRTC: ${e.message}", e)
        }
    }

    // ─── Get EGL Base Context (for external renderers) ─────
    fun getEglBaseContext(): EglBase.Context? = eglBase?.eglBaseContext

    // ─── Is Currently Receiving ────────────────────────────
    fun isReceiving(): Boolean = _state.value == CastingState.RECEIVING
}
