package com.appsfolder.livebridge.liveupdate

import android.app.Notification
import android.os.Bundle
import androidx.core.app.NotificationCompat

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

    fun applyToBuilder(
        builder: NotificationCompat.Builder,
        title: String,
        content: String,
        progressPercent: Int?,
        indeterminate: Boolean
    ) {
        val payload = extrasBundle(title, content, progressPercent, indeterminate)
        builder.addExtras(payload)
        builder.setRequestPromotedOngoing(true)
        builder.setShortCriticalText(chipText(title, content, progressPercent))
        builder.setCategory(NotificationCompat.CATEGORY_NAVIGATION)
        builder.setTicker(chipText(title, content, progressPercent))
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
