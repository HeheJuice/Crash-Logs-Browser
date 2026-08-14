package com.HeheJuice.CrashLogs

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.*
import com.google.android.material.R as MaterialR
import com.google.android.material.materialswitch.MaterialSwitch

class DeveloperOptionsActivity : Activity() {

    private lateinit var sharedPrefs: SharedPreferences
    private var isDark: Boolean = false
    private var googleSansFlexTypeface: Typeface? = null

    // 颜色变量
    private var bgColor: Int = 0
    private var cardBgColor: Int = 0
    private var backBtnBgColor: Int = 0
    private var primaryTextColor: Int = 0
    private var secondaryTextColor: Int = 0
    private var accentColor: Int = 0
    private var trackOnColor: Int = 0
    private var trackOffColor: Int = 0
    private var thumbOnColor: Int = 0
    private var thumbOffColor: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        googleSansFlexTypeface = try {
            Typeface.createFromAsset(assets, "GoogleSansFlex.ttf")
        } catch (_: Exception) { null }

        isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        setStatusBarColors()

        // 获取颜色
        accentColor = MonetColorHelper.getColor(this, MaterialR.attr.colorPrimary)
        bgColor = MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceContainer)
        cardBgColor = if (isDark) {
            MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceContainerHigh)
        } else {
            MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceContainerLowest)
        }
        backBtnBgColor = MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceContainerHigh)
        primaryTextColor = MonetColorHelper.getColor(this, MaterialR.attr.colorOnSurface)
        secondaryTextColor = MonetColorHelper.getColor(this, MaterialR.attr.colorOnSurfaceVariant)

        // ★ 开关颜色（匹配系统设置）
        if (isDark) {
            // 深色模式：开启轨道使用 Primary 亮色，拇指使用 OnPrimary（深色），对勾使用 Primary
            trackOnColor = accentColor
            trackOffColor = MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceVariant)
            thumbOnColor = MonetColorHelper.getColor(this, MaterialR.attr.colorOnPrimary)
            thumbOffColor = MonetColorHelper.getColor(this, MaterialR.attr.colorOutline)
        } else {
            // 浅色模式：保持原样
            trackOnColor = accentColor
            trackOffColor = MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceVariant)
            thumbOnColor = Color.WHITE
            thumbOffColor = Color.WHITE
        }

        val dpToPx = { dp: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
        }

        sharedPrefs = getSharedPreferences("developer_prefs", Context.MODE_PRIVATE)

        val rootFrame = FrameLayout(this).apply { setBackgroundColor(bgColor) }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dpToPx(20f), getStatusBarHeight() + dpToPx(16f), dpToPx(20f), dpToPx(20f))
        }

        // 顶部标题栏
        val topBar = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, dpToPx(16f))
        }

        val titleText = TextView(this).apply {
            text = "Developer Options"
            textSize = 20f
            setTextColor(primaryTextColor)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && googleSansFlexTypeface != null) {
                typeface = Typeface.create(googleSansFlexTypeface, 800, false)
                fontVariationSettings = "'wght' 800, 'ROND' 100, 'opsz' 14"
            } else {
                typeface = googleSansFlexTypeface ?: Typeface.DEFAULT_BOLD
            }

            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        topBar.addView(titleText)

        val backBtn = ImageView(this).apply {
            setImageDrawable(createArrowBackDrawable(primaryTextColor, dpToPx))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(backBtnBgColor)
            }
            contentDescription = "Back"
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(48f),
                dpToPx(48f),
                Gravity.START or Gravity.CENTER_VERTICAL
            )
            setOnClickListener { finish() }
            setOnTouchListener(pressScaleTouchListener)
        }
        topBar.addView(backBtn)

        contentLayout.addView(topBar)

        // 卡片
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBgColor)
                cornerRadius = dpToPx(28f).toFloat()
            }
            setPadding(dpToPx(20f), dpToPx(8f), dpToPx(20f), dpToPx(8f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val label = TextView(this).apply {
            text = "Test Update Receiver"
            textSize = 17f
            setTextColor(primaryTextColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)

        // MaterialSwitch
        val switch = LayoutInflater.from(this)
            .inflate(R.layout.switch_material, null) as MaterialSwitch

        // 1. 轨道颜色
        val trackStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val trackColors = intArrayOf(trackOnColor, trackOffColor)
        switch.trackTintList = ColorStateList(trackStates, trackColors)

        // 2. 拇指颜色（开启/关闭不同颜色）
        val thumbStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val thumbColors = intArrayOf(thumbOnColor, thumbOffColor)
        switch.thumbTintList = ColorStateList(thumbStates, thumbColors)

        // 3. 对勾图标颜色
        val iconStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val iconColors = intArrayOf(
            accentColor,  // 开启时与轨道同色
            secondaryTextColor
        )
        switch.thumbIconTintList = ColorStateList(iconStates, iconColors)

        // 读取保存的状态并设置
        val savedState = sharedPrefs.getBoolean("fake_update_enabled", false)
        switch.isChecked = savedState

        // 监听器
        switch.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("fake_update_enabled", isChecked).apply()
            Toast.makeText(
                this@DeveloperOptionsActivity,
                if (isChecked) "Fake update enabled" else "Fake update disabled",
                Toast.LENGTH_SHORT
            ).show()
            // 刷新颜色
            switch.trackTintList = ColorStateList(trackStates, trackColors)
            switch.thumbIconTintList = ColorStateList(iconStates, iconColors)
        }

        row.addView(switch, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        card.addView(row)
        contentLayout.addView(card)

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        contentLayout.addView(spacer)

        rootFrame.addView(contentLayout)
        setContentView(rootFrame)
    }

    private fun createArrowBackDrawable(color: Int, dpToPx: (Float) -> Int): android.graphics.drawable.Drawable {
        return object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = dpToPx(2.5f).toFloat()
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            override fun draw(canvas: android.graphics.Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val size = dpToPx(6.5f)
                val path = android.graphics.Path().apply {
                    moveTo(cx + size * 0.4f, cy - size)
                    lineTo(cx - size * 0.5f, cy)
                    lineTo(cx + size * 0.4f, cy + size)
                }
                canvas.drawPath(path, paint)
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
            @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
    }

    private val pressScaleTouchListener = View.OnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().scaleX(0.94f).scaleY(0.94f).alpha(0.85f).setDuration(120).start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350).start()
            }
        }
        false
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dpToPx(36f)
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

    private fun setStatusBarColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            val statusColor = MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceContainer)
            window.statusBarColor = statusColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val flags = window.decorView.systemUiVisibility
                if (!isDark) {
                    window.decorView.systemUiVisibility = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    window.decorView.systemUiVisibility = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
            }
        }
    }
}