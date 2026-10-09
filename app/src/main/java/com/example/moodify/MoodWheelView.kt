package com.example.moodify

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A spinning wheel with one colored slice per mood. A fixed white pointer
 * sits at the top; spin() rotates the wheel and slows it down until it
 * stops on a random mood, then reports which one through onSpinFinished.
 */
class MoodWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // One color per mood, in the same order as ALL_MOODS
    private val sliceColors = listOf(
        "#60A5FA", // Chill - soft blue
        "#F97316", // Hype - orange
        "#FACC15", // Happy - yellow
        "#6366F1", // Sad - indigo
        "#EF4444", // Angry - red
        "#10B981"  // Focused - green
    ).map { Color.parseColor(it) }

    private val moods = ALL_MOODS
    private val sliceAngle = 360f / moods.size

    // Current rotation of the wheel in degrees
    private var rotationDegrees = 0f
    private var animator: ValueAnimator? = null

    var isSpinning = false
        private set

    // Called with the mood's index once the wheel comes to a stop
    var onSpinFinished: ((Int) -> Unit)? = null

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#12121A")
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(34f)
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#12121A")
    }
    private val hubRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(dp(3f), 0f, dp(2f), Color.parseColor("#80000000"))
    }

    private val wheelBounds = RectF()
    private val pointerPath = Path()

    init {
        // Needed so the shadow on the pointer actually renders
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    // Keep the wheel a perfect square, sized by its width
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, width)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val pointerHeight = dp(22f)
        val size = min(width, height).toFloat()
        val radius = size / 2f - pointerHeight / 2f - dp(4f)
        val centerX = width / 2f
        val centerY = height / 2f + pointerHeight / 4f

        wheelBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        // ---- the spinning part ----
        canvas.save()
        canvas.rotate(rotationDegrees, centerX, centerY)

        moods.forEachIndexed { index, mood ->
            // Slice 0 is centered straight up (-90 degrees) before any rotation
            val startAngle = -90f - sliceAngle / 2f + index * sliceAngle

            slicePaint.color = sliceColors[index]
            canvas.drawArc(wheelBounds, startAngle, sliceAngle, true, slicePaint)
            canvas.drawArc(wheelBounds, startAngle, sliceAngle, true, dividerPaint)

            // Place the mood's emoji in the middle of the slice
            val middleAngle = Math.toRadians((startAngle + sliceAngle / 2f).toDouble())
            val emojiDistance = radius * 0.6f

            val emojiX = centerX + (emojiDistance * cos(middleAngle)).toFloat()
            val emojiY = centerY + (emojiDistance * sin(middleAngle)).toFloat()
            canvas.drawText(mood.emoji, emojiX, emojiY - (emojiPaint.ascent() + emojiPaint.descent()) / 2f, emojiPaint)
        }

        canvas.restore()

        // ---- the parts that stay still ----
        // Center hub
        canvas.drawCircle(centerX, centerY, radius * 0.14f, hubPaint)
        canvas.drawCircle(centerX, centerY, radius * 0.14f, hubRingPaint)

        // Pointer at the top, pointing down into the wheel
        val pointerHalfWidth = dp(14f)
        val pointerTop = centerY - radius - pointerHeight / 2f
        pointerPath.reset()
        pointerPath.moveTo(centerX - pointerHalfWidth, pointerTop)
        pointerPath.lineTo(centerX + pointerHalfWidth, pointerTop)
        pointerPath.lineTo(centerX, pointerTop + pointerHeight)
        pointerPath.close()
        canvas.drawPath(pointerPath, pointerPaint)
    }

    /**
     * Spins the wheel several full turns and lands it on a random mood.
     * Does nothing if a spin is already in progress.
     */
    fun spin() {
        if (isSpinning) return
        isSpinning = true

        val targetIndex = Random.nextInt(moods.size)

        // Rotating the wheel by -(index * sliceAngle) brings that slice under the pointer.
        // A small random nudge keeps it from always stopping dead-center in the slice.
        val wobble = Random.nextFloat() * sliceAngle * 0.6f - sliceAngle * 0.3f
        val landingAngle = -(targetIndex * sliceAngle) + wobble

        // Spin forward 5 to 7 full turns from wherever the wheel is now
        val currentNormalized = ((rotationDegrees % 360f) + 360f) % 360f
        var delta = ((landingAngle - currentNormalized) % 360f + 360f) % 360f
        delta += 360f * Random.nextInt(5, 8)

        val start = rotationDegrees
        animator = ValueAnimator.ofFloat(start, start + delta).apply {
            duration = 3800L
            interpolator = DecelerateInterpolator(2.2f)
            addUpdateListener {
                rotationDegrees = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isSpinning = false
                    onSpinFinished?.invoke(targetIndex)
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        // Stop the animation if the screen goes away mid-spin
        animator?.removeAllListeners()
        animator?.cancel()
        isSpinning = false
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}