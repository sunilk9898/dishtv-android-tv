package com.dishtv.commandcenter.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dishtv.commandcenter.webrtc.CastingReceiver
import okhttp3.*
import org.json.JSONObject
import org.webrtc.SurfaceViewRenderer
import java.util.concurrent.TimeUnit

/**
 * DishTV AI Command Center — Android TV Main Activity
 *
 * Renders content from server commands:
 *  - WebView for dashboards, web URLs
 *  - SurfaceViewRenderer for WebRTC screen casting
 *  - TextView for status messages
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val DEVICE_ID = "TV001"
        private const val WS_URL = "wss://dishtv.io/ws/TV001?department=Reception"
    }

    // ─── Views ──────────────────────────────────────────
    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var overlay: LinearLayout
    private lateinit var statusTv: TextView
    private var webrtcSurface: SurfaceViewRenderer? = null

    // ─── WebRTC ─────────────────────────────────────────
    private var castingReceiver: CastingReceiver? = null
    private var isCasting = false

    // ─── WebSocket ──────────────────────────────────────
    private var ws: WebSocket? = null
    private var reconnect = true

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        buildUI()
        setContentView(root)
        initWebRTC()
        status("Connecting to DishTV Server...")
        connect()
    }

    override fun onDestroy() {
        reconnect = false
        ws?.close(1000, "bye")
        castingReceiver?.release()
        webrtcSurface?.release()
        webView.destroy()
        super.onDestroy()
    }

    // ─── UI Setup ───────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildUI() {
        val mp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        root = FrameLayout(this).apply { layoutParams = mp; setBackgroundColor(Color.BLACK) }

        // WebView for dashboards/web content
        webView = WebView(this).apply {
            layoutParams = mp; visibility = View.GONE; setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        root.addView(webView)

        // SurfaceViewRenderer for WebRTC casting
        webrtcSurface = SurfaceViewRenderer(this).apply {
            layoutParams = mp
            visibility = View.GONE
            setBackgroundColor(Color.BLACK)
        }
        root.addView(webrtcSurface)

        // Status overlay
        overlay = LinearLayout(this).apply {
            layoutParams = mp
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0a0a1a"))
        }
        statusTv = TextView(this).apply {
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(64, 32, 64, 32)
        }
        overlay.addView(statusTv)
        root.addView(overlay)
    }

    // ─── WebRTC Setup ───────────────────────────────────

    private fun initWebRTC() {
        try {
            castingReceiver = CastingReceiver(applicationContext)
            castingReceiver?.initialize()

            // Wire signaling: when CastingReceiver needs to send a message, send it via WebSocket
            castingReceiver?.onSignalingMessage = { message ->
                Log.d(TAG, "Sending signaling message: ${message.take(100)}")
                ws?.send(message)
            }

            Log.i(TAG, "WebRTC CastingReceiver initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC: ${e.message}", e)
        }
    }

    // ─── View Management ────────────────────────────────

    private fun status(msg: String) = runOnUiThread {
        overlay.visibility = View.VISIBLE
        webView.visibility = View.GONE
        webrtcSurface?.visibility = View.GONE
        statusTv.text = msg
    }

    private fun loadUrl(url: String) {
        Log.i(TAG, "Loading URL: $url")
        runOnUiThread {
            webView.visibility = View.VISIBLE
            overlay.visibility = View.GONE
            webrtcSurface?.visibility = View.GONE
            isCasting = false
            webView.loadUrl(url)
        }
    }

    private fun showWebRTCCast() {
        Log.i(TAG, "Showing WebRTC cast surface")
        runOnUiThread {
            isCasting = true
            webrtcSurface?.visibility = View.VISIBLE
            webView.visibility = View.GONE
            overlay.visibility = View.GONE
        }
    }

    private fun hideWebRTCCast() {
        Log.i(TAG, "Hiding WebRTC cast surface")
        runOnUiThread {
            isCasting = false
            webrtcSurface?.visibility = View.GONE
            overlay.visibility = View.VISIBLE
            statusTv.text = "Cast ended\nWaiting for content..."
        }
    }

    // ─── Message Handling ───────────────────────────────

    private fun handle(json: JSONObject) {
        val type = json.optString("type", "")
        val data = json.optJSONObject("data")
        val url = data?.optString("url", "")?.takeIf { it.isNotEmpty() }
        val title = data?.optString("title", "")?.takeIf { it.isNotEmpty() }
        val text = data?.optString("text", "")?.takeIf { it.isNotEmpty() }

        when (type) {
            "content_update" -> {
                if (url != null) loadUrl(url)
                else status(title ?: text ?: "No content")
            }
            "cast_start" -> {
                val sessionId = json.optString("session_id", "")
                Log.i(TAG, "Cast start received, session: $sessionId")
                showWebRTCCast()
                // The SDP offer will arrive in a separate message
            }
            "sdp_offer" -> {
                val sdp = json.optString("sdp", "")
                val sessionId = json.optString("session_id", "")
                if (sdp.isNotEmpty()) {
                    Log.i(TAG, "SDP offer received (${sdp.length} chars), session: $sessionId")
                    // Start receiving with the SurfaceViewRenderer
                    webrtcSurface?.let { surface ->
                        castingReceiver?.startReceiving(sessionId, surface, sdp)
                    }
                }
            }
            "ice_candidate" -> {
                val candidate = json.optString("candidate", "")
                val sdpMid = json.optString("sdp_mid", "0")
                val sdpMLineIndex = json.optInt("sdp_m_line_index", 0)
                if (candidate.isNotEmpty()) {
                    castingReceiver?.handleRemoteIceCandidate(sdpMid, sdpMLineIndex, candidate)
                }
            }
            "cast_stop" -> {
                Log.i(TAG, "Cast stop received")
                castingReceiver?.stopReceiving()
                hideWebRTCCast()
            }
            "emergency_override" -> {
                val eTitle = data?.optString("title", "EMERGENCY") ?: "EMERGENCY"
                val eMsg = data?.optString("message", "") ?: ""
                status("\u26A0\uFE0F $eTitle\n\n$eMsg")
            }
            "device_config", "heartbeat_ack", "connected" -> {
                Log.d(TAG, "System message: $type")
            }
            else -> {
                // Try as content update
                if (url != null) loadUrl(url)
                else if (title != null || text != null) status(title ?: text ?: "")
            }
        }
    }

    // ─── WebSocket ──────────────────────────────────────

    private fun connect() {
        val client = OkHttpClient.Builder()
            .pingInterval(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build()

        ws = client.newWebSocket(Request.Builder().url(WS_URL).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                status("\u2705 Connected to DishTV\nDevice: $DEVICE_ID\nWaiting for content...")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WS MSG: ${text.take(200)}")
                try {
                    handle(JSONObject(text))
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WS closed: $code")
                retry()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WS error: ${t.message}")
                status("\u274C Connection Failed\n${t.message}\nReconnecting...")
                retry()
            }
        })
    }

    private fun retry() {
        if (!reconnect) return
        root.postDelayed({
            if (reconnect) {
                status("\uD83D\uDD04 Reconnecting...")
                connect()
            }
        }, 5000)
    }
}
