package com.HeheJuice.CrashLogs.util

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.HeheJuice.CrashLogs.R
import kotlin.math.abs

object OptimizeEngine {

    // ─────────────────────────────────────────────────────────────────
    // Density
    // ─────────────────────────────────────────────────────────────────
    @Volatile private var density: Float = 0f

    fun dpToPx(dp: Float): Int {
        val d = density.takeIf { it != 0f }
            ?: Resources.getSystem().displayMetrics.density.also { density = it }
        return (dp * d + 0.5f).toInt()
    }

    fun dpToPx(dp: Int): Int = dpToPx(dp.toFloat())

    fun View.dpToPx(dp: Float): Int = OptimizeEngine.dpToPx(dp)
    fun View.dpToPx(dp: Int): Int = OptimizeEngine.dpToPx(dp)

    // ─────────────────────────────────────────────────────────────────
    // Interpolators
    // ─────────────────────────────────────────────────────────────────
    val springBackInterpolator: PathInterpolator by lazy(LazyThreadSafetyMode.NONE) {
        PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
    }

    val decelerateInterpolator: DecelerateInterpolator by lazy(LazyThreadSafetyMode.NONE) {
        DecelerateInterpolator(1.5f)
    }

    // ─────────────────────────────────────────────────────────────────
    // 柔順動畫用 Interpolator（模擬彈簧曲線）
    // ─────────────────────────────────────────────────────────────────

    private val pressInterpolator: Interpolator by lazy(LazyThreadSafetyMode.NONE) {
        PathInterpolator(0.4f, 0f, 0.2f, 1f)
    }

    private val longPressInterpolator: Interpolator by lazy(LazyThreadSafetyMode.NONE) {
        PathInterpolator(0.34f, 1.4f, 0.64f, 1f)
    }

    /**
     * 釋放：緩入 + 中段輕度過衝 + 平滑收尾。
     *
     * 控制點 (0.22, 0.04) → (0.36, 1.06)：
     *   前 22% 只完成 4% 的移動 → 柔和啟動，但不會「卡一下」
     *   中段加速通過
     *   尾段過衝約 6% 後平滑落回 1
     */
    private val releaseInterpolator: Interpolator by lazy(LazyThreadSafetyMode.NONE) {
        PathInterpolator(0.22f, 0.04f, 0.36f, 1.06f)
    }

    // ─────────────────────────────────────────────────────────────────
    // Press-scale touch listener（輕量版，僅供簡單按鈕使用）
    // ─────────────────────────────────────────────────────────────────
    val pressScaleTouchListener: View.OnTouchListener by lazy(LazyThreadSafetyMode.NONE) {
        View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(0.95f).scaleY(0.95f).alpha(0.88f)
                        .setDuration(120)
                        .setInterpolator(decelerateInterpolator)
                        .withLayer()
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(1.0f).scaleY(1.0f).alpha(1.0f)
                        .setDuration(350)
                        .setInterpolator(springBackInterpolator)
                        .withLayer()
                        .start()
                }
            }
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // FeedbackTarget — 通用拖動 / 長按 / 釋放反饋
    // ─────────────────────────────────────────────────────────────────

    /**
     * 通用 feedback 目標。
     *
     * 動畫全部使用 PathInterpolator 模擬彈簧曲線：
     *   - 按下 180ms：緩入緩出
     *   - 長按 420ms：單端輕度過衝
     *   - 拖動：位移 + 沿拖動方向 squash & stretch
     *          （pivot 連續移動到拖動反側，變形用 effectiveDrag 從 0 平滑展開）
     *   - 釋放 460 / 520ms：pivot 補償歸位 + 緩入 + 過衝 + 平滑收尾
     *
     * 震動回饋（新增）：
     *   - 長按觸發 → vibrateThud（重震，明確意圖）
     *   - 拖動首次超過 slop → vibrateTick（輕震，邊界感）
     *   - 按下 / 鬆手 不震 → 避免高頻操作過度吵雜
     *   - 一次拖動只震一次（dragVibrated 標記）
     *
     * 特性：
     *   - 手指抖動防護：MOVE 超過 touch slop 才接管 scale
     *   - 反側不擴張：pivot 連續移動到拖動反側（無跳邊）
     *   - 拖動邊緣不跳動：變形從 0 平滑展開（effectiveDrag）
     *   - 釋放不跳動：pivot 歸位時補償 translationX / translationY
     *   - 釋放節奏緊湊：緩入段短、時長短
     */
    class FeedbackTarget(private val target: View) {

        private var dragging = false
        private var pointerId = -1
        private var startX = 0f
        private var startY = 0f
        private var longPressed = false
        private var dragVibrated = false

        private val longPressHandler = Handler(Looper.getMainLooper())
        private var longPressRunnable: Runnable? = null

        fun handleEvent(ev: MotionEvent) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val loc = IntArray(2)
                    target.getLocationOnScreen(loc)
                    val left = loc[0].toFloat()
                    val top = loc[1].toFloat()
                    val right = left + target.width
                    val bottom = top + target.height

                    if (ev.rawX in left..right && ev.rawY in top..bottom) {
                        dragging = true
                        pointerId = ev.getPointerId(0)
                        startX = ev.rawX
                        startY = ev.rawY

                        // 重置 pivot 至中心（防禦性設定）
                        target.pivotX = target.width / 2f
                        target.pivotY = target.height / 2f

                        target.animate().cancel()
                        animatePress()
                        scheduleLongPress()
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) return
                    val idx = ev.findPointerIndex(pointerId)
                    if (idx < 0) return

                    val dx = ev.getRawX(idx) - startX
                    val dy = ev.getRawY(idx) - startY

                    if (!longPressed && longPressRunnable != null) {
                        val slop = ViewConfiguration.get(target.context).scaledTouchSlop
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (dist > slop) {
                            cancelLongPressPending()
                        }
                    }

                    applyTranslation(dx, dy)
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    if (!dragging) return
                    val pid = ev.getPointerId(ev.actionIndex)
                    if (pid == pointerId) release()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) release()
                }
            }
        }

        private fun animatePress() {
            target.animate().cancel()
            target.animate()
                .scaleX(FEEDBACK_PRESS_SCALE)
                .scaleY(FEEDBACK_PRESS_SCALE)
                .setDuration(FEEDBACK_PRESS_MS)
                .setInterpolator(pressInterpolator)
                .withLayer()
                .start()
        }

        private fun scheduleLongPress() {
            cancelLongPressPending()

            val r = Runnable {
                longPressRunnable = null
                if (!dragging) return@Runnable
                longPressed = true
                animateLongPress()
            }
            longPressRunnable = r
            longPressHandler.postDelayed(r, FEEDBACK_LONG_PRESS_MS)
        }

        private fun cancelLongPressPending() {
            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
            longPressRunnable = null
        }

        private fun animateLongPress() {
            // ★ 長按觸發 → 重震一下（明確意圖）
            OptimizeEngine.vibrateThud(target.context)

            target.animate().cancel()
            target.animate()
                .scaleX(FEEDBACK_LONG_PRESS_SCALE)
                .scaleY(FEEDBACK_LONG_PRESS_SCALE)
                .setDuration(FEEDBACK_LONG_PRESS_ANIM_MS)
                .setInterpolator(longPressInterpolator)
                .withLayer()
                .start()
        }

        /**
         * 依拖動距離與方向計算位移 + 變形。
         *
         * 位移：每軸上限 = 該軸尺寸 × 2.5%（用原始 dx/dy，位置跟手）
         * 變形：沿拖動方向拉伸（最大 7%），垂直方向壓扁（最大 3.5%）
         *
         * **手指抖動防護**：
         *   只有當拖動距離超過 touch slop 時才取消動畫並接管 scale。
         *
         * **拖動邊界震動**：
         *   首次超過 slop 時輕震一下（dragVibrated 保證一次拖動只震一次）。
         *
         * **拖動邊緣不跳動**：
         *   變形用「超出 slop 的距離」計算（effectiveDrag）。
         *   剛超過 slop 時 effectiveDrag = 0 → scale = 1.0，連續展開。
         *
         * **反側不擴張**：
         *   pivot 移到拖動方向的反側，bar 只朝拖動方向延伸。
         */
        private fun applyTranslation(dx: Float, dy: Float) {
            val w = target.width
            val h = target.height
            if (w <= 0 || h <= 0) return

            val dragDistance = kotlin.math.sqrt(dx * dx + dy * dy)
            val slop = ViewConfiguration.get(target.context).scaledTouchSlop.toFloat()

            // ★ 有效拖動距離 = 超出 slop 的部分
            val effectiveDrag = (dragDistance - slop).coerceAtLeast(0f)
            val significantDrag = effectiveDrag > 0f

            if (significantDrag) {
                target.animate().cancel()
                // ★ 拖動首次啟動 → 輕震一下（一次拖動只震一次）
                if (!dragVibrated) {
                    dragVibrated = true
                    OptimizeEngine.vibrateTick(target.context)
                }
            }

            // ── 位移（仍用原始 dx/dy，讓位置跟手）──
            val maxOffsetX = w * FEEDBACK_DRAG_MAX_PCT
            val normX = (dx / (w * 0.5f)).coerceIn(-1f, 1f)
            val easedX = normX * (2f - abs(normX))
            target.translationX = maxOffsetX * easedX

            val maxOffsetY = h * FEEDBACK_DRAG_MAX_PCT
            val normY = (dy / (h * 0.5f)).coerceIn(-1f, 1f)
            val easedY = normY * (2f - abs(normY))
            target.translationY = maxOffsetY * easedY

            // ── 變形：用「超出 slop 的距離」計算，從 0 平滑展開 ──
            if (!significantDrag) return

            val refLength = minOf(w, h) * 0.5f
            val dragMagnitude = (effectiveDrag / refLength).coerceIn(0f, 1f)

            val absCosX: Float
            val absCosY: Float
            if (dragDistance > 0.001f) {
                absCosX = abs(dx) / dragDistance
                absCosY = abs(dy) / dragDistance
            } else {
                absCosX = 0f
                absCosY = 0f
            }

            val stretchAmount = dragMagnitude * FEEDBACK_DEFORM_STRETCH
            val squashAmount = dragMagnitude * FEEDBACK_DEFORM_SQUASH

            val scaleXDelta = stretchAmount * absCosX - squashAmount * absCosY
            val scaleYDelta = stretchAmount * absCosY - squashAmount * absCosX

            val baseScale = if (longPressed) FEEDBACK_LONG_PRESS_SCALE else 1f

            // ★ 連續 pivot：
            //   easedX = +1（右拖）→ pivotXf = 0（左邊）
            //   easedX = −1（左拖）→ pivotXf = 1（右邊）
            //   easedX =  0        → pivotXf = 0.5（中心）
            target.pivotX = w * (0.5f - easedX * 0.5f)
            target.pivotY = h * (0.5f - easedY * 0.5f)

            target.scaleX = baseScale * (1f + scaleXDelta)
            target.scaleY = baseScale * (1f + scaleYDelta)
        }

        /**
         * 鬆手：
         *   1. 把 pivot 歸回中心，同時補償 translation 保持視覺位置不變
         *   2. 位移 + 縮放一起 spring back
         */
        private fun release() {
            cancelLongPressPending()

            dragging = false
            pointerId = -1
            dragVibrated = false    // ★ 重置，下次拖動可再震

            val wasLongPressed = longPressed
            longPressed = false

            val w = target.width
            val h = target.height
            if (w > 0 && h > 0) {
                val newPivotX = w / 2f
                val newPivotY = h / 2f

                val newTransX = target.translationX +
                        (target.pivotX - newPivotX) * (1f - target.scaleX)
                val newTransY = target.translationY +
                        (target.pivotY - newPivotY) * (1f - target.scaleY)

                target.pivotX = newPivotX
                target.pivotY = newPivotY
                target.translationX = newTransX
                target.translationY = newTransY
            }

            target.animate().cancel()
            target.animate()
                .translationX(0f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(
                    if (wasLongPressed) FEEDBACK_RELEASE_LONG_PRESS_ANIM_MS
                    else FEEDBACK_RELEASE_ANIM_MS
                )
                .setInterpolator(releaseInterpolator)
                .withLayer()
                .start()
        }

        fun destroy() {
            longPressHandler.removeCallbacksAndMessages(null)
        }
    }

    // ── FeedbackTarget 內部常數 ──

    private const val FEEDBACK_PRESS_SCALE = 0.95f
    private const val FEEDBACK_PRESS_MS = 180L
    private const val FEEDBACK_LONG_PRESS_MS = 500L
    private const val FEEDBACK_LONG_PRESS_SCALE = 1.06f
    private const val FEEDBACK_LONG_PRESS_ANIM_MS = 420L
    private const val FEEDBACK_DRAG_MAX_PCT = 0.025f
    private const val FEEDBACK_DEFORM_STRETCH = 0.07f
    private const val FEEDBACK_DEFORM_SQUASH = 0.035f
    private const val FEEDBACK_RELEASE_ANIM_MS = 460L
    private const val FEEDBACK_RELEASE_LONG_PRESS_ANIM_MS = 520L

    // ─────────────────────────────────────────────────────────────────
    // Vibration
    // ─────────────────────────────────────────────────────────────────
    private const val FALLBACK_TICK_MS = 20L
    private const val FALLBACK_THUD_MS = 40L
    private const val FALLBACK_TICK_AMP = 120
    private const val FALLBACK_THUD_AMP = 255

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                        as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun supportsPrimitive(vibrator: Vibrator, primitiveId: Int): Boolean {
        return try {
            vibrator.areAllPrimitivesSupported(primitiveId)
        } catch (_: Exception) {
            false
        }
    }

    fun vibrateTick(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                supportsPrimitive(vibrator, VibrationEffect.Composition.PRIMITIVE_TICK)
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(
                        VibrationEffect.Composition.PRIMITIVE_TICK,
                        1.0f
                    )
                    .compose()
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(
                    FALLBACK_TICK_MS, FALLBACK_TICK_AMP
                )
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(FALLBACK_TICK_MS)
            }
        } catch (_: Exception) { }
    }

    fun vibrateThud(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                supportsPrimitive(vibrator, VibrationEffect.Composition.PRIMITIVE_THUD)
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(
                        VibrationEffect.Composition.PRIMITIVE_THUD,
                        1.0f
                    )
                    .compose()
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(
                    FALLBACK_THUD_MS, FALLBACK_THUD_AMP
                )
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(FALLBACK_THUD_MS)
            }
        } catch (_: Exception) { }
    }

    // ─────────────────────────────────────────────────────────────────
    // Gradient drawable cache
    // ─────────────────────────────────────────────────────────────────
    private val drawableStateCache = LruCache<String, Drawable.ConstantState>(128)

    fun groupItemBackground(color: Int, topRadius: Float, bottomRadius: Float): GradientDrawable {
        val key = "grp|$color|$topRadius|$bottomRadius"
        drawableStateCache.get(key)?.let { return it.newDrawable() as GradientDrawable }

        val d = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadii = floatArrayOf(
                topRadius, topRadius,
                topRadius, topRadius,
                bottomRadius, bottomRadius,
                bottomRadius, bottomRadius
            )
        }
        d.constantState?.let { drawableStateCache.put(key, it) }
        return d
    }

    fun monetHorizontalGradient(startColor: Int, endColor: Int, radius: Float): GradientDrawable {
        val key = "h|$startColor|$endColor|$radius"
        drawableStateCache.get(key)?.let { return it.newDrawable() as GradientDrawable }

        val d = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(startColor, endColor)
        ).apply { cornerRadius = radius }
        d.constantState?.let { drawableStateCache.put(key, it) }
        return d
    }

    fun monetDiagonalGradient(startColor: Int, endColor: Int, radius: Float): GradientDrawable {
        val key = "d|$startColor|$endColor|$radius"
        drawableStateCache.get(key)?.let { return it.newDrawable() as GradientDrawable }

        val d = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(startColor, endColor)
        ).apply { cornerRadius = radius }
        d.constantState?.let { drawableStateCache.put(key, it) }
        return d
    }

    fun pillBackground(color: Int, radius: Float = 10_000f): GradientDrawable {
        val key = "p|$color|$radius"
        drawableStateCache.get(key)?.let { return it.newDrawable() as GradientDrawable }

        val d = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
        d.constantState?.let { drawableStateCache.put(key, it) }
        return d
    }

    fun roundedBackground(color: Int, radius: Float): GradientDrawable {
        val key = "r|$color|$radius"
        drawableStateCache.get(key)?.let { return it.newDrawable() as GradientDrawable }

        val d = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
        d.constantState?.let { drawableStateCache.put(key, it) }
        return d
    }

    fun circleBackground(color: Int): GradientDrawable {
        val key = "c|$color"
        drawableStateCache.get(key)?.let { return it.newDrawable() as GradientDrawable }

        val d = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        d.constantState?.let { drawableStateCache.put(key, it) }
        return d
    }

    // ─────────────────────────────────────────────────────────────────
    // ColorStateList cache
    // ─────────────────────────────────────────────────────────────────
    private val colorStateListCache = LruCache<String, ColorStateList>(32)

    fun trackTintList(onColor: Int, offColor: Int): ColorStateList =
        colorStateListCache.get("t|$onColor|$offColor") ?: ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(onColor, offColor)
        ).also { colorStateListCache.put("t|$onColor|$offColor", it) }

    fun thumbTintList(onColor: Int, offColor: Int): ColorStateList =
        colorStateListCache.get("th|$onColor|$offColor") ?: ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(onColor, offColor)
        ).also { colorStateListCache.put("th|$onColor|$offColor", it) }

    fun thumbIconTintList(onColor: Int, offColor: Int): ColorStateList =
        colorStateListCache.get("ti|$onColor|$offColor") ?: ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(onColor, offColor)
        ).also { colorStateListCache.put("ti|$onColor|$offColor", it) }

    // ─────────────────────────────────────────────────────────────────
    // Back button
    // ─────────────────────────────────────────────────────────────────
    fun createBackButton(
        context: Context,
        backgroundColor: Int,
        contentDescription: String? = null,
        sizeDp: Float = 50f,
        paddingDp: Float = 12f,
        layoutParams: LinearLayout.LayoutParams? = null,
        onClick: () -> Unit
    ): ImageView {
        val size = dpToPx(sizeDp)
        val pad = dpToPx(paddingDp)
        return ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.arrow_back_24px))
            background = circleBackground(backgroundColor)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(pad, pad, pad, pad)
            this.layoutParams = layoutParams ?: LinearLayout.LayoutParams(size, size)
            isClickable = true
            isFocusable = true
            this.contentDescription = contentDescription
            setOnClickListener { onClick() }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Cache management
    // ─────────────────────────────────────────────────────────────────
    fun clearCaches() {
        drawableStateCache.evictAll()
        colorStateListCache.evictAll()
    }
}