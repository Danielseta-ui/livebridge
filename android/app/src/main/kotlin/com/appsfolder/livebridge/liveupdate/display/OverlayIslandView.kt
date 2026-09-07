package com.appsfolder.livebridge.liveupdate.display

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.view.isVisible

internal class OverlayIslandView(
    context: Context
) : FrameLayout(context) {
    private val card = LinearLayout(context)
    private val header = LinearLayout(context)
    private val iconView = ImageView(context)
    private val titleView = TextView(context)
    private val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val bodyView = TextView(context)
    private val otpView = TextView(context)

    private var expanded = false
    private var contentIntent: PendingIntent? = null
    private var otpCode: String? = null
    private var boundIcon: Bitmap? = null
    private var lastTapAt = 0L

    var onDismissRequested: (() -> Unit)? = null

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleTap()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val dx = if (e1 == null) 0f else e2.x - e1.x
                val dy = if (e1 == null) 0f else e2.y - e1.y
                val dismissHorizontally = kotlin.math.abs(dx) > dp(48f) && kotlin.math.abs(velocityX) > 800
                val dismissUp = dy < -dp(24f) && velocityY < -600
                if (dismissHorizontally || dismissUp) {
                    onDismissRequested?.invoke()
                    return true
                }
                return false
            }
        }
    )

    init {
        clipToPadding = false
        clipChildren = false

        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(dp(10), dp(8), dp(12), dp(8))
        card.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setColor(CARD_COLOR)
            setStroke(dp(1), STROKE_COLOR)
        }
        card.elevation = dp(8).toFloat()

        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL

        iconView.layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
            marginEnd = dp(8)
        }
        iconView.scaleType = ImageView.ScaleType.CENTER_CROP

        titleView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        titleView.setTextColor(Color.WHITE)
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        titleView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        titleView.maxLines = 1
        titleView.ellipsize = android.text.TextUtils.TruncateAt.END

        header.addView(iconView)
        header.addView(titleView)

        progressBar.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(3)
        ).apply {
            topMargin = dp(6)
        }
        progressBar.max = 100
        progressBar.progress = 0
        progressBar.isIndeterminate = false
        progressBar.progressDrawable = GradientDrawable().apply {
            cornerRadius = dp(2).toFloat()
            setColor(ACCENT_COLOR)
        }.let { fill ->
            android.graphics.drawable.LayerDrawable(
                arrayOf(
                    GradientDrawable().apply {
                        cornerRadius = dp(2).toFloat()
                        setColor(TRACK_COLOR)
                    },
                    android.graphics.drawable.ClipDrawable(
                        fill,
                        Gravity.START,
                        android.graphics.drawable.ClipDrawable.HORIZONTAL
                    )
                )
            ).apply {
                setId(0, android.R.id.background)
                setId(1, android.R.id.progress)
            }
        }

        bodyView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(6)
        }
        bodyView.setTextColor(MUTED_TEXT)
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        bodyView.maxLines = 3
        bodyView.ellipsize = android.text.TextUtils.TruncateAt.END
        bodyView.isVisible = false

        otpView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(8)
        }
        otpView.setPadding(dp(10), dp(4), dp(10), dp(4))
        otpView.setTextColor(Color.BLACK)
        otpView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        otpView.typeface = Typeface.MONOSPACE
        otpView.background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(ACCENT_COLOR)
        }
        otpView.isVisible = false

        card.addView(header)
        card.addView(progressBar)
        card.addView(bodyView)
        card.addView(otpView)

        val cardParams = LayoutParams(
            dp(COLLAPSED_WIDTH_DP),
            LayoutParams.WRAP_CONTENT
        )
        addView(card, cardParams)
        layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        )
        isClickable = true
        isFocusable = false
    }

    fun bind(state: OverlayMirrorState) {
        contentIntent = state.contentIntent
        otpCode = state.otpCode
        titleView.text = state.collapsedTitle()
        bodyView.text = state.bodyText
        bindIcon(state.icon)
        bindProgress(state)
        bindOtp(state.otpCode)
        applyExpandedLayout()
    }

    fun setExpanded(value: Boolean) {
        if (expanded == value) {
            return
        }
        expanded = value
        applyExpandedLayout(animate = true)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        boundIcon = null
        iconView.setImageDrawable(null)
    }

    private fun bindIcon(bitmap: Bitmap?) {
        boundIcon = bitmap
        if (bitmap == null || bitmap.isRecycled) {
            iconView.setImageDrawable(circlePlaceholder())
            return
        }
        val rounded = RoundedBitmapDrawableFactory.create(resources, bitmap)
        rounded.isCircular = true
        rounded.setAntiAlias(true)
        iconView.setImageDrawable(rounded)
    }

    private fun bindProgress(state: OverlayMirrorState) {
        when {
            state.indeterminate -> {
                progressBar.isVisible = true
                progressBar.isIndeterminate = true
            }
            state.progressPercent != null -> {
                progressBar.isVisible = true
                progressBar.isIndeterminate = false
                progressBar.progress = state.progressPercent.coerceIn(0, 100)
            }
            else -> {
                progressBar.isVisible = expanded && state.bodyText.isNotBlank()
                progressBar.isIndeterminate = false
                progressBar.progress = 0
                if (!expanded) {
                    progressBar.isVisible = false
                }
            }
        }
    }

    private fun bindOtp(code: String?) {
        if (code.isNullOrBlank()) {
            otpView.isVisible = false
            return
        }
        otpView.text = code
        otpView.isVisible = expanded
    }

    private fun applyExpandedLayout(animate: Boolean = false) {
        val width = dp(if (expanded) EXPANDED_WIDTH_DP else COLLAPSED_WIDTH_DP)
        val params = card.layoutParams
        params.width = width
        card.layoutParams = params
        titleView.maxLines = if (expanded) 2 else 1
        bodyView.isVisible = expanded && bodyView.text.isNotBlank()
        otpView.isVisible = expanded && !otpCode.isNullOrBlank()
        if (progressBar.isIndeterminate || progressBar.progress > 0) {
            progressBar.isVisible = true
        }
        if (animate) {
            card.requestLayout()
        }
    }

    private fun handleTap() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTapAt < 280L) {
            return
        }
        lastTapAt = now

        if (!expanded) {
            setExpanded(true)
            return
        }

        val otp = otpCode
        if (!otp.isNullOrBlank() && otpView.isVisible) {
            copyOtp(otp)
            return
        }

        val intent = contentIntent
        if (intent != null) {
            runCatching { intent.send() }
            return
        }
        setExpanded(false)
    }

    private fun copyOtp(code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("otp", code))
        otpView.text = "Copied"
        postDelayed({
            if (otpCode == code) {
                otpView.text = code
            }
        }, 1200L)
    }

    private fun circlePlaceholder(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ACCENT_COLOR)
        }
    }

    private fun dp(value: Int): Int = dp(value.toFloat())

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        ).toInt()
    }

    private fun OverlayMirrorState.collapsedTitle(): String {
        progressPercent?.let { percent ->
            val label = title.ifBlank { appName }.ifBlank { bodyText }
            return if (label.isBlank()) "$percent%" else "$label  $percent%"
        }
        otpCode?.let { return it }
        return title.ifBlank { appName }.ifBlank { bodyText }
    }

    companion object {
        private const val COLLAPSED_WIDTH_DP = 220
        private const val EXPANDED_WIDTH_DP = 280
        private const val CARD_COLOR = 0xF21C2626.toInt()
        private const val STROKE_COLOR = 0x33FFFFFF
        private const val TRACK_COLOR = 0x33FFFFFF
        private const val MUTED_TEXT = 0xB3FFFFFF.toInt()
        private const val ACCENT_COLOR = 0xFF7BBDB7.toInt()
    }
}
