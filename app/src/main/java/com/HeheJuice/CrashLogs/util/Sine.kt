package com.HeheJuice.CrashLogs.util

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.sin

/**
 * A per-character animated text view.
 *
 * Inspired by AnimatedTextLayout's "Lr2" strategy (drop-in per character), but
 * here every character enters along a sine-shaped vertical path so the whole
 * string looks like a wave rolling in.
 *
 * Usage:
 *   val sine = Sine(context)
 *   sine.textSizeSp = 32f
 *   sine.typeface   = Typeface.DEFAULT_BOLD
 *   sine.setText("Hello", color)
 *   parent.addView(sine)
 *   sine.post { sine.animateSineWave() }
 */
class Sine @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val charViews = ArrayList<AppCompatTextView>()
    private var animatorSet: AnimatorSet? = null

    var textSizeSp: Float = 32f
        set(value) {
            field = value
            charViews.forEach { it.textSize = value }
        }

    var typeface: Typeface? = Typeface.DEFAULT_BOLD
        set(value) {
            field = value
            charViews.forEach { it.typeface = value }
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        clipChildren = false
        clipToPadding = false
    }

    // ────────────────────────────────────────────────────────────────
    // Text setup
    // ────────────────────────────────────────────────────────────────

    /** Solid-colour text. */
    fun setText(text: CharSequence, color: Int) {
        buildCharViews(text)
        val csl = ColorStateList.valueOf(color)
        charViews.forEach { it.setTextColor(csl) }
    }

    /**
     * Gradient text. The gradient is measured across the full string width
     * (not per character) and the shader is shifted per character — this is
     * exactly what Lr2.b([I) does in the original smali.
     */
    fun setTextGradient(text: CharSequence, colors: IntArray) {
        require(colors.size >= 2) { "Gradient needs at least 2 stops" }
        buildCharViews(text)
        applyGradient(colors)
    }

    private fun buildCharViews(text: CharSequence) {
        clear()
        if (text.isEmpty()) return

        for (ch in text) {
            val tv = AppCompatTextView(context).apply {
                this.text = ch.toString()
                textSize = textSizeSp
                typeface = this@Sine.typeface
                includeFontPadding = false
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
                )
            }
            addView(tv)
            charViews += tv
        }
    }

    private fun applyGradient(colors: IntArray) {
        if (charViews.isEmpty()) return

        // 1) Measure every character and the total width
        var totalWidth = 0f
        val widths = FloatArray(charViews.size)
        charViews.forEachIndexed { i, tv ->
            val w = tv.paint.measureText(tv.text.toString())
            widths[i] = w
            totalWidth += w
        }
        if (totalWidth <= 0f) return

        // 2) Build the gradient across the full width, then shift per char
        var offsetX = 0f
        charViews.forEachIndexed { i, tv ->
            val shader = LinearGradient(
                0f, 0f, totalWidth, 0f,
                colors, null, Shader.TileMode.CLAMP
            )
            val m = Matrix().apply { setTranslate(-offsetX, 0f) }
            shader.setLocalMatrix(m)
            tv.paint.shader = shader
            tv.invalidate()
            offsetX += widths[i]
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Entrance animation
    // ────────────────────────────────────────────────────────────────

    /**
     * Sine-wave entrance.
     *
     * Each visible character starts at a Y offset derived from a sine of its
     * index, then animates down to 0 with a staggered delay. The result looks
     * like a wave rolling left → right across the string.
     *
     * @param durationMs    per-character animation length
     * @param staggerMs     delay between consecutive characters
     * @param amplitudeDp   peak vertical displacement (before density scale)
     * @param phasePerChar  radians of phase advance per character
     * @param overshoot     if true, offset = sin(phase) * amp (some chars start above, some below)
     *                      if false, offset = -amp * (1 - i/n) (monotonic fall)
     */
    fun animateSineWave(
        durationMs: Long = 750L,
        staggerMs: Long = 45L,
        amplitudeDp: Float = 22f,
        phasePerChar: Float = 0.7f,
        overshoot: Boolean = true
    ) {
        animatorSet?.cancel()
        if (charViews.isEmpty()) return

        val density = resources.displayMetrics.density
        val amp = amplitudeDp * density

        val moveInterp = PathInterpolator(0.22f, 0.25f, 0f, 1f)
        val fadeInterp = PathInterpolator(0.33f, 0f, 0.67f, 1f)

        val animators = ArrayList<Animator>(charViews.size * 2)
        var visibleIdx = 0
        val visibleCount = charViews.count { it.text.toString().isNotBlank() }
            .coerceAtLeast(1)

        charViews.forEach { tv ->
            if (tv.text.toString().isBlank()) {
                tv.alpha = 1f
                tv.translationY = 0f
                return@forEach
            }

            val phase = visibleIdx * phasePerChar
            val startY = if (overshoot) {
                (sin(phase.toDouble()).toFloat()) * amp
            } else {
                -amp * (1f - visibleIdx / visibleCount.toFloat())
            }

            tv.translationY = startY
            tv.alpha = 0f

            val move = ObjectAnimator
                .ofFloat(tv, "translationY", startY, 0f)
                .apply {
                    duration = durationMs
                    startDelay = visibleIdx * staggerMs
                    interpolator = moveInterp
                }

            val fade = ObjectAnimator
                .ofFloat(tv, "alpha", 0f, 1f)
                .apply {
                    duration = durationMs
                    startDelay = visibleIdx * staggerMs
                    interpolator = fadeInterp
                }

            animators += move
            animators += fade
            visibleIdx++
        }

        animatorSet = AnimatorSet().apply {
            playTogether(animators)
            start()
        }
    }

    /**
     * 估算一次 sine wave 動畫的總時長（ms）。
     *
     * 計算方式：`durationMs + (可見字元數 - 1) × staggerMs`。
     * 空白字元不計入（與 [animateSineWave] 內部邏輯一致）。
     *
     * 供外部做「動畫進行中忽略新播放請求」的冷卻時間使用。
     */
    fun estimateTotalAnimationDuration(
        durationMs: Long,
        staggerMs: Long
    ): Long {
        val visible = charViews.count { it.text.toString().isNotBlank() }
            .coerceAtLeast(1)
        return durationMs + (visible - 1).coerceAtLeast(0) * staggerMs
    }

    fun clear() {
        animatorSet?.cancel()
        animatorSet = null
        charViews.clear()
        removeAllViews()
    }

    override fun onDetachedFromWindow() {
        animatorSet?.cancel()
        super.onDetachedFromWindow()
    }
}