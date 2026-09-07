package com.appsfolder.livebridge.liveupdate

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat

internal object SamsungNowBarAdapter {
    private const val STYLE = "android.ongoingActivityNoti.style"
    private const val PRIMARY = "android.ongoingActivityNoti.primaryInfo"
    private const val SECONDARY = "android.ongoingActivityNoti.secondaryInfo"
    private const val NOWBAR_PRIMARY = "android.ongoingActivityNoti.nowbarPrimaryInfo"
    private const val NOWBAR_SECONDARY = "android.ongoingActivityNoti.nowbarSecondaryInfo"
    private const val CHIP_TEXT = "android.ongoingActivityNoti.chipExpandedText"
    private const val CHIP_BG = "android.ongoingActivityNoti.chipBgColor"
    private const val PROGRESS = "android.ongoingActivityNoti.progress"
    private const val PROGRESS_MAX = "android.ongoingActivityNoti.progressMax"
    private const val CHIP_COLOR = 0xFF7BBDB7.toInt()
    private const val SEM_FLAG_ONGOING_ACTIVITY = 32768
    private const val SESSION_TAG = "livebridge_nowbar"

    private val sessionLock = Any()
    private var mediaSession: MediaSessionCompat? = null

    fun applyToBuilder(
        context: Context,
        builder: NotificationCompat.Builder,
        title: String,
        content: String,
        progressPercent: Int?,
        indeterminate: Boolean,
        artwork: Bitmap? = null
    ) {
        val payload = extrasBundle(title, content, progressPercent, indeterminate)
        val chip = chipText(title, content, progressPercent)
        val session = ensureSession(context.applicationContext)
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title.ifBlank { chip })
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, content.ifBlank { title })
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title.ifBlank { chip })
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, content.ifBlank { title })
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
                .build()
        )
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE)
                .build()
        )
        session.isActive = true

        builder.addExtras(payload)
        builder.setRequestPromotedOngoing(true)
        builder.setShortCriticalText(chip)
        builder.setTicker(chip)
        builder.setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        builder.setStyle(
            MediaNotificationCompat.MediaStyle().setMediaSession(session.sessionToken)
        )
    }

    fun release() {
        synchronized(sessionLock) {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        }
    }

    private fun ensureSession(context: Context): MediaSessionCompat {
        synchronized(sessionLock) {
            mediaSession?.let { return it }
            return MediaSessionCompat(context, SESSION_TAG).also { created ->
                created.setCallback(object : MediaSessionCompat.Callback() {})
                created.isActive = true
                mediaSession = created
            }
        }
    }

    fun decoratePosted(notification: Notification) {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val content = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val progress = if (extras.containsKey(PROGRESS)) extras.getInt(PROGRESS) else null
        val indeterminate = extras.getInt(PROGRESS_MAX, -1) == 100 && extras.getInt(PROGRESS, -1) == 0 &&
            extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        extras.putAll(
            extrasBundle(
                title = title,
                content = content,
                progressPercent = progress,
                indeterminate = indeterminate
            )
        )
        applySemFlags(notification)
    }

    private fun extrasBundle(
        title: String,
        content: String,
        progressPercent: Int?,
        indeterminate: Boolean
    ): Bundle {
        val primary = title.ifBlank { content }.ifBlank { "LiveBridge" }.take(96)
        val secondary = content.ifBlank { primary }.take(220)
        return Bundle().apply {
            putInt(STYLE, 1)
            putString(PRIMARY, primary)
            putString(SECONDARY, secondary)
            putString(NOWBAR_PRIMARY, primary)
            putString(NOWBAR_SECONDARY, secondary)
            putString(CHIP_TEXT, chipText(primary, secondary, progressPercent))
            putInt(CHIP_BG, CHIP_COLOR)
            if (indeterminate) {
                putInt(PROGRESS, 0)
                putInt(PROGRESS_MAX, 100)
            } else if (progressPercent != null) {
                putInt(PROGRESS, progressPercent.coerceIn(0, 100))
                putInt(PROGRESS_MAX, 100)
            }
        }
    }

    private fun chipText(title: String, content: String, progressPercent: Int?): String {
        progressPercent?.let { return "$it%" }
        return title.ifBlank { content }.ifBlank { "LiveBridge" }.take(24)
    }

    private fun applySemFlags(notification: Notification) {
        try {
            val field = Notification::class.java.getDeclaredField("semFlags")
            field.isAccessible = true
            val current = field.getInt(notification)
            field.setInt(notification, current or SEM_FLAG_ONGOING_ACTIVITY)
        } catch (_: Throwable) {
        }
    }
}
