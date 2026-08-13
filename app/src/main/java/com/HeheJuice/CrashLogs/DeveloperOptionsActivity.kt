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
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.*
import com.google.android.material.materialswitch.MaterialSwitch

class DeveloperOptionsActivity : Activity() {

    private lateinit var sharedPrefs: SharedPreferences
    private var isDark: Boolean = false
    private var accentColor: Int = 0
    private var googleSansFlexTypeface: Typeface? = null

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

        val bgColor = if (isDark) Color.parseColor("#000000") else Color.parseColor("#F2F2F7")
        val cardBgColor = if (isDark) Color.parseColor("#1C1C1E") else Color.parseColor("#FFFFFF")
        val cardBorderColor = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")
        val primaryTextColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#000000")
        val secondaryTextColor = if (isDark) Color.parseColor("#8E8E93") else Color.parseColor("#6C6C70")
        accentColor = if (isDark) Color.parseColor("#3E82F7") else Color.parseColor("#0066FF")
        val backBtnBgColor = if (isDark) Color.parseColor("#3A3A3C") else Color.parseColor("#E5E5EA")

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

        // ----- 卡片（更紧凑的内边距） -----
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBgColor)
                cornerRadius = dpToPx(28f).toFloat()
                setStroke(dpToPx(1f), cardBorderColor)
            }
            // 上下内边距 16dp，左右 20dp
            setPadding(dpToPx(20f), dpToPx(16f), dpToPx(20f), dpToPx(16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 标题 + 开关行
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val label = TextView(this).apply {
            text = "Force Fake Update (v9.9)"
            textSize = 17f
            setTextColor(primaryTextColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)

        // ===== MaterialSwitch =====
        val themedContext = ContextThemeWrapper(
            this,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )

        val switch = LayoutInflater.from(themedContext)
            .inflate(R.layout.switch_material, null) as MaterialSwitch

        switch.isChecked = sharedPrefs.getBoolean("fake_update_enabled", false)

        // ---- 轨道颜色 ----
        val trackStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val trackColors = intArrayOf(
            accentColor,
            if (isDark) Color.parseColor("#494A4D") else Color.parseColor("#CCCCCC")
        )
        switch.trackTintList = ColorStateList(trackStates, trackColors)

        // ---- 拇指颜色（白色） ----
        switch.thumbTintList = ColorStateList.valueOf(Color.WHITE)

        // ---- 对勾/叉号图标颜色（开启 = accentColor，关闭 = 灰色） ----
        val iconStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val iconColors = intArrayOf(
            accentColor,
            if (isDark) Color.parseColor("#8E8E93") else Color.parseColor("#6C6C70")
        )
        switch.thumbIconTintList = ColorStateList(iconStates, iconColors)

        // ---- 监听变化 ----
        switch.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("fake_update_enabled", isChecked).apply()
            Toast.makeText(
                this@DeveloperOptionsActivity,
                if (isChecked) "Fake update enabled" else "Fake update disabled",
                Toast.LENGTH_SHORT
            ).show()
            switch.trackTintList = ColorStateList(trackStates, trackColors)
            switch.thumbIconTintList = ColorStateList(iconStates, iconColors)
        }

        row.addView(switch, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        card.addView(row)

        // ----- 描述文字（间距 4dp，更紧凑） -----
        val desc = TextView(this).apply {
            text = "When enabled, the app will show version 9.9 with dummy release notes."
            textSize = 14f
            setTextColor(secondaryTextColor)
            setPadding(0, dpToPx(4f), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(desc)

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

    // ========== 辅助方法 ==========
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
            val statusColor = if (isDark) Color.parseColor("#000000") else Color.parseColor("#F2F2F7")
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