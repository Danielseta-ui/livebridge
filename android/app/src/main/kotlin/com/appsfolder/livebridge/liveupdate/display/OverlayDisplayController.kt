package com.appsfolder.livebridge.liveupdate.display

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import java.util.LinkedHashMap

internal data class OverlayMirrorState(
    val notificationId: Int,
    val mirrorKey: String,
    val appName: String,
    val title: String,
    val bodyText: String,
    val progressPercent: Int?,
    val indeterminate: Boolean,
    val otpCode: String?,
    val icon: Bitmap?,
    val contentIntent: PendingIntent?
)

internal object OverlayDisplayController {
    const val EXTRA_OTP = "livebridge.overlay.otp"
    const val EXTRA_PROGRESS = "livebridge.overlay.progress"
    const val EXTRA_INDETERMINATE = "livebridge.overlay.indeterminate"

    private const val TAG = "OverlayDisplay"
    private const val MAX_TRACKED = 8

    private val mainHandler = Handler(Looper.getMainLooper())
    private val states = LinkedHashMap<Int, OverlayMirrorState>()
    private val userHiddenIds = mutableSetOf<Int>()

    private var attachedContext: Context? = null
    private var windowManager: WindowManager? = null
    private var islandView: OverlayIslandView? = null
    private var windowShown = false

    fun attach(context: Context) {
        attachedContext = context.applicationContext
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun putMirrorExtras(
        builder: NotificationCompat.Builder,
        otpCode: String?,
        progressPercent: Int?,
        indeterminate: Boolean
    ) {
        val extras = builder.extras
        extras.putBoolean("livebridge.overlay", true)
        if (!otpCode.isNullOrBlank()) {
            extras.putString(EXTRA_OTP, otpCode)
        }
        if (progressPercent != null) {
            extras.putInt(EXTRA_PROGRESS, progressPercent)
        }
        extras.putBoolean(EXTRA_INDETERMINATE, indeterminate)
    }

    fun upsert(
        context: Context,
        notificationId: Int,
        mirrorKey: String,
        notification: Notification
    ) {
        if (!LiveUpdateSdk.requiresOverlayDisplay()) {
            return
        }
        val appContext = context.applicationContext
        attachedContext = appContext
        val state = stateFromNotification(appContext, notificationId, mirrorKey, notification)
        runOnMain {
            states.remove(notificationId)
            states[notificationId] = state
            while (states.size > MAX_TRACKED) {
                val oldest = states.entries.first()
                states.remove(oldest.key)
                userHiddenIds.remove(oldest.key)
            }
            if (notificationId in userHiddenIds) {
                return@runOnMain
            }
            renderLocked(appContext)
        }
    }

    fun remove(notificationId: Int) {
        runOnMain {
            states.remove(notificationId)
            userHiddenIds.remove(notificationId)
            val context = attachedContext
            if (context == null) {
                hideWindow()
                return@runOnMain
            }
            renderLocked(context)
        }
    }

    fun clear() {
        runOnMain {
            states.clear()
            userHiddenIds.clear()
            hideWindow()
        }
    }

    private fun renderLocked(context: Context) {
        val visible = states.entries.lastOrNull { it.key !in userHiddenIds }?.value
        if (visible == null) {
            hideWindow()
            return
        }
        if (!canDrawOverlays(context)) {
            hideWindow()
            return
        }
        showOrUpdate(context, visible)
    }

    private fun showOrUpdate(context: Context, state: OverlayMirrorState) {
        val wm = windowManager ?: (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        windowManager = wm
        val view = islandView ?: OverlayIslandView(context).also { created ->
            created.onDismissRequested = {
                val hiddenId = states.entries.lastOrNull { it.key !in userHiddenIds }?.key
                if (hiddenId != null) {
                    userHiddenIds.add(hiddenId)
                    renderLocked(context)
                }
            }
            islandView = created
        }
        view.bind(state)
        if (windowShown) {
            runCatching { wm.updateViewLayout(view, overlayLayoutParams(context)) }
            return
        }
        try {
            wm.addView(view, overlayLayoutParams(context))
            windowShown = true
        } catch (error: Throwable) {
            Log.w(TAG, "Unable to add overlay window", error)
            windowShown = false
        }
    }

    private fun hideWindow() {
        val view = islandView ?: return
        val wm = windowManager
        if (windowShown && wm != null) {
            runCatching { wm.removeViewImmediate(view) }
        }
        windowShown = false
        islandView = null
    }

    private fun overlayLayoutParams(context: Context): WindowManager.LayoutParams {
        val topInset = currentTopInset(context)
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topInset + dp(context, 6)
            title = "LiveBridge overlay"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun currentTopInset(context: Context): Int {
        val wm = windowManager ?: context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = wm.currentWindowMetrics.windowInsets
            val types = android.view.WindowInsets.Type.statusBars() or
                android.view.WindowInsets.Type.displayCutout()
            insets.getInsetsIgnoringVisibility(types).top
        } else {
            @Suppress("DEPRECATION")
            dp(context, 32)
        }
    }

    private fun stateFromNotification(
        context: Context,
        notificationId: Int,
        mirrorKey: String,
        notification: Notification
    ): OverlayMirrorState {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val appName = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val otp = extras.getString(EXTRA_OTP)
        val overlayProgress = if (extras.containsKey(EXTRA_PROGRESS)) {
            extras.getInt(EXTRA_PROGRESS)
        } else {
            null
        }
        val nativeMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val nativeProgress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val nativeIndeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val overlayIndeterminate = extras.getBoolean(EXTRA_INDETERMINATE, false)
        val percent = overlayProgress ?: if (nativeMax > 0 && !nativeIndeterminate) {
            ((nativeProgress.toFloat() / nativeMax.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else {
            null
        }
        return OverlayMirrorState(
            notificationId = notificationId,
            mirrorKey = mirrorKey,
            appName = appName,
            title = title,
            bodyText = text,
            progressPercent = percent,
            indeterminate = overlayIndeterminate || nativeIndeterminate,
            otpCode = otp,
            icon = resolveIcon(context, notification),
            contentIntent = notification.contentIntent
        )
    }

    private fun resolveIcon(context: Context, notification: Notification): Bitmap? {
        val large = notification.getLargeIcon()?.loadDrawable(context)
        val fromLarge = (large as? BitmapDrawable)?.bitmap ?: large?.toBitmap()
        if (fromLarge != null && !fromLarge.isRecycled) {
            return copyBitmap(fromLarge)
        }
        val small = notification.smallIcon?.loadDrawable(context)
        val fromSmall = (small as? BitmapDrawable)?.bitmap ?: small?.toBitmap()
        if (fromSmall != null && !fromSmall.isRecycled) {
            return copyBitmap(fromSmall)
        }
        return null
    }

    private fun copyBitmap(source: Bitmap): Bitmap? {
        val config = source.config
        val copyConfig = if (config == null || config == Bitmap.Config.HARDWARE) {
            Bitmap.Config.ARGB_8888
        } else {
            config
        }
        return runCatching { source.copy(copyConfig, false) }.getOrNull()
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
