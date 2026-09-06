package com.linkdeck.android.ui.qr

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.AttrRes

/**
 * Custom camera overlay view that draws a transparent rounded scanning box,
 * darkened background scrim, sleek continuous corner brackets, and an animated laser sweep.
 */
class QrScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val scrimPaint = Paint().apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }

    private val transparentPaint = Paint().apply {
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
        color = Color.parseColor("#44FFFFFF")
    }

    private val cornerPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val laserPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.5f)
    }

    val boxRect = RectF()
    private val boxCornerRadius = dpToPx(24f)
    private val cornerArmLength = dpToPx(16f)
    private var laserProgress = 0f

    private val cornerPath = Path()
    private var laserAnimator: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        val primaryColor = resolveColor(com.google.android.material.R.attr.colorPrimary, Color.WHITE)
        cornerPaint.color = primaryColor
        laserPaint.color = primaryColor
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val boxSize = (w.coerceAtMost(h) * 0.72f).coerceIn(dpToPx(200f), dpToPx(320f))
        val left = (w - boxSize) / 2f
        val top = (h - boxSize) / 2f - dpToPx(24f)
        boxRect.set(left, top, left + boxSize, top + boxSize)
        updateCornerPaths()
    }

    private fun updateCornerPaths() {
        cornerPath.reset()
        val l = boxRect.left
        val t = boxRect.top
        val r = boxRect.right
        val b = boxRect.bottom
        val rad = boxCornerRadius
        val arm = cornerArmLength
        val dia = rad * 2f

        // Top-Left corner bracket
        cornerPath.moveTo(l + rad + arm, t)
        cornerPath.lineTo(l + rad, t)
        cornerPath.arcTo(RectF(l, t, l + dia, t + dia), 270f, -90f)
        cornerPath.lineTo(l, t + rad + arm)

        // Top-Right corner bracket
        cornerPath.moveTo(r - rad - arm, t)
        cornerPath.lineTo(r - rad, t)
        cornerPath.arcTo(RectF(r - dia, t, r, t + dia), 270f, 90f)
        cornerPath.lineTo(r, t + rad + arm)

        // Bottom-Left corner bracket
        cornerPath.moveTo(l + rad + arm, b)
        cornerPath.lineTo(l + rad, b)
        cornerPath.arcTo(RectF(l, b - dia, l + dia, b), 90f, 90f)
        cornerPath.lineTo(l, b - rad - arm)

        // Bottom-Right corner bracket
        cornerPath.moveTo(r - rad - arm, b)
        cornerPath.lineTo(r - rad, b)
        cornerPath.arcTo(RectF(r - dia, b - dia, r, b), 90f, -90f)
        cornerPath.lineTo(r, b - rad - arm)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startLaserAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        laserAnimator?.cancel()
    }

    private fun startLaserAnimation() {
        laserAnimator?.cancel()
        laserAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                laserProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw darkened scrim over entire view
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        // 2. Punch out transparent rounded rectangle in center
        canvas.drawRoundRect(boxRect, boxCornerRadius, boxCornerRadius, transparentPaint)

        // 3. Draw subtle full rounded viewport border
        canvas.drawRoundRect(boxRect, boxCornerRadius, boxCornerRadius, borderPaint)

        // 4. Draw smooth continuous corner bracket accents
        canvas.drawPath(cornerPath, cornerPaint)

        // 5. Draw moving laser line inside box
        val laserY = boxRect.top + (boxRect.height() * laserProgress)
        val margin = dpToPx(12f)
        canvas.drawLine(boxRect.left + margin, laserY, boxRect.right - margin, laserY, laserPaint)
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }

    private fun resolveColor(@AttrRes attr: Int, fallback: Int): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(attr, typedValue, true)) {
            typedValue.data
        } else {
            fallback
        }
    }
}
