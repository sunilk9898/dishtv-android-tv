package com.dishtv.commandcenter.util

/**
 * Centralised constants for the DishTV AI Command Center Android TV app.
 */
object Constants {

    // -----------------------------------------------------------------------
    // Server endpoints
    // -----------------------------------------------------------------------
    const val BASE_URL = "https://api.dishtv.io/"
    const val WS_BASE_URL = "wss://api.dishtv.io/ws/"

    // REST paths (relative to BASE_URL)
    const val PATH_REGISTER = "api/devices/register"
    const val PATH_DEVICE_CONFIG = "api/devices/{deviceId}/config"
    const val PATH_DEVICE_STATUS = "api/devices/{deviceId}/status"
    const val PATH_SCREENSHOT = "api/devices/{deviceId}/screenshot"
    const val PATH_HEALTH = "api/devices/{deviceId}/health"
    const val PATH_CHECK_UPDATE = "api/devices/{deviceId}/update"

    // -----------------------------------------------------------------------
    // Timeouts (millis)
    // -----------------------------------------------------------------------
    const val HTTP_CONNECT_TIMEOUT_MS = 15_000L
    const val HTTP_READ_TIMEOUT_MS = 30_000L
    const val HTTP_WRITE_TIMEOUT_MS = 30_000L
    const val WS_PING_INTERVAL_MS = 10_000L

    // -----------------------------------------------------------------------
    // Intervals (millis)
    // -----------------------------------------------------------------------
    const val HEARTBEAT_INTERVAL_MS = 15_000L
    const val SCREENSHOT_INTERVAL_MS = 5_000L
    const val HEALTH_REPORT_INTERVAL_MS = 15_000L

    // -----------------------------------------------------------------------
    // WebSocket reconnection (exponential back-off)
    // -----------------------------------------------------------------------
    const val WS_RECONNECT_BASE_MS = 1_000L
    const val WS_RECONNECT_MAX_MS = 30_000L
    const val WS_RECONNECT_MULTIPLIER = 2.0

    // -----------------------------------------------------------------------
    // Content priorities (mirrors ContentType enum values)
    // -----------------------------------------------------------------------
    const val PRIORITY_WALLPAPER = 1
    const val PRIORITY_WELCOME = 2
    const val PRIORITY_MEETING = 3
    const val PRIORITY_DASHBOARD = 4
    const val PRIORITY_ANNOUNCEMENT = 5
    const val PRIORITY_BROADCAST = 6
    const val PRIORITY_EMERGENCY = 7

    // -----------------------------------------------------------------------
    // Screenshot
    // -----------------------------------------------------------------------
    const val SCREENSHOT_QUALITY = 60
    const val SCREENSHOT_FORMAT = "jpeg"

    // -----------------------------------------------------------------------
    // SharedPreferences
    // -----------------------------------------------------------------------
    const val PREFS_NAME = "dishtv_command_center"
    const val PREF_DEVICE_ID = "device_id"
    const val PREF_DEVICE_NAME = "device_name"
    const val PREF_DEPARTMENT = "department"
    const val PREF_LOCATION = "location"
    const val PREF_REGISTERED = "is_registered"
    const val PREF_LAST_CONTENT_JSON = "last_content_json"
    const val PREF_LAST_CONTENT_TYPE = "last_content_type"
    const val PREF_OFFLINE_QUEUE = "offline_command_queue"
    const val PREF_DEFAULT_WALLPAPER_URL = "default_wallpaper_url"
    const val PREF_APP_START_TIME = "app_start_time"
    const val PREF_SERVER_BASE_URL = "server_base_url"
    const val PREF_WS_BASE_URL = "ws_base_url"

    // -----------------------------------------------------------------------
    // Device defaults
    // -----------------------------------------------------------------------
    const val DEFAULT_DEVICE_ID = "TV001"
    const val DEFAULT_DEPARTMENT = "lobby"
    const val DEFAULT_DEVICE_NAME = "DishTV Display"

    // -----------------------------------------------------------------------
    // Logging tags
    // -----------------------------------------------------------------------
    const val TAG_WS = "WS_Manager"
    const val TAG_CMD = "CmdHandler"
    const val TAG_PLAYER = "PlayerMgr"
    const val TAG_RENDERER = "ContentRenderer"
    const val TAG_HEALTH = "HealthReporter"
    const val TAG_SCREENSHOT = "ScreenshotSvc"
    const val TAG_OFFLINE = "OfflineMgr"
    const val TAG_REPO = "DeviceRepo"
    const val TAG_MAIN = "MainActivity"
    const val TAG_BOOT = "BootReceiver"

    // -----------------------------------------------------------------------
    // Miscellaneous
    // -----------------------------------------------------------------------
    const val MAX_COMMAND_QUEUE_SIZE = 200
    const val CONTENT_HISTORY_MAX = 50
    const val EMERGENCY_OVERLAY_Z = 1000f
}
