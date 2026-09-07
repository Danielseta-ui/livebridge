package com.appsfolder.livebridge.liveupdate

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

    fun apply(
        builder: NotificationCompat.Builder,
        title: String,
        content: String,
        progressPercent: Int?,
        indeterminate: Boolean
    ) {
        val primary = title.ifBlank { content }.ifBlank { "LiveBridge" }.take(96)
        val secondary = content.ifBlank { primary }.take(220)
        val extras = builder.extras
        extras.putInt(STYLE, 1)
        extras.putCharSequence(PRIMARY, primary)
        extras.putCharSequence(SECONDARY, secondary)
        extras.putCharSequence(NOWBAR_PRIMARY, primary)
        extras.putCharSequence(NOWBAR_SECONDARY, secondary)
        extras.putCharSequence(CHIP_TEXT, primary.take(24))
        extras.putInt(CHIP_BG, CHIP_COLOR)
        if (indeterminate) {
            extras.putInt(PROGRESS, 0)
            extras.putInt(PROGRESS_MAX, 100)
        } else if (progressPercent != null) {
            extras.putInt(PROGRESS, progressPercent.coerceIn(0, 100))
            extras.putInt(PROGRESS_MAX, 100)
        }
    }
}
