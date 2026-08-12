package com.HeheJuice.CrashLogs

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*
import androidx.core.content.ContextCompat

class CrashDetailActivity : Activity() {

    private var primaryTextColor: Int = 0
    private var secondaryTextColor: Int = 0
    private var accentColor: Int = 0
    private var inputBgColor: Int = 0
    private var cardBgColor: Int = 0
    private var cardBorderColor: Int = 0
    private var backBtnBgColor: Int = 0
    private var isDark: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        initColors()
        setStatusBarColors() // Fix issue 2

        // Get status bar height
        val statusBarHeight = getStatusBarHeight()

        val type = intent.getStringExtra("type") ?: "Crash"
        val appName = intent.getStringExtra("appName") ?: "Unknown"
        val timestamp = intent.getStringExtra("timestamp") ?: "Unknown"
        val details = intent.getStringExtra("details") ?: "No details available."

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBgColor)
            }
            setPadding(dpToPx(20f), statusBarHeight + dpToPx(12f), dpToPx(20f), dpToPx(20f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Header – back | title | open | copy
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Back button – fixed 48dp
        val backBtn = ImageView(this).apply {
            setImageDrawable(createArrowBackDrawable())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(backBtnBgColor)
            }
            setPadding(dpToPx(8f), dpToPx(8f), dpToPx(8f), dpToPx(8f))
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
            setOnTouchListener(pressScaleTouchListener)
            layoutParams = LinearLayout.LayoutParams(dpToPx(48f), dpToPx(48f)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = dpToPx(8f)
            }
        }
        headerLayout.addView(backBtn)

        // Title – takes remaining space
        val titleTv = TextView(this).apply {
            text = "$type Details"
            textSize = 22f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(8f)
                marginEnd = dpToPx(8f)
            }
        }
        headerLayout.addView(titleTv)

        // ----- OPEN APP BUTTON -----
        val openDrawable = ContextCompat.getDrawable(this, R.drawable.open_in_new_24px)
        val openBtn: View
        if (openDrawable != null) {
            openBtn = ImageView(this).apply {
                setImageDrawable(openDrawable)
                setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(backBtnBgColor)
                }
                setPadding(dpToPx(14f), dpToPx(14f), dpToPx(14f), dpToPx(14f))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    try {
                        val launchIntent = packageManager.getLaunchIntentForPackage(appName)
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        } else {
                            Toast.makeText(this@CrashDetailActivity, "Cannot launch app", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@CrashDetailActivity, "Cannot launch app", Toast.LENGTH_SHORT).show()
                    }
                }
                setOnTouchListener(pressScaleTouchListener)
                layoutParams = LinearLayout.LayoutParams(dpToPx(48f), dpToPx(48f)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginEnd = dpToPx(8f)
                }
            }
        } else {
            // Fallback text button if icon missing
            openBtn = TextView(this).apply {
                text = "Open"
                textSize = 14f
                setTextColor(primaryTextColor)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(backBtnBgColor)
                }
                setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(8f))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    try {
                        val launchIntent = packageManager.getLaunchIntentForPackage(appName)
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        } else {
                            Toast.makeText(this@CrashDetailActivity, "Cannot launch app", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@CrashDetailActivity, "Cannot launch app", Toast.LENGTH_SHORT).show()
                    }
                }
                setOnTouchListener(pressScaleTouchListener)
                layoutParams = LinearLayout.LayoutParams(dpToPx(48f), dpToPx(48f)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginEnd = dpToPx(8f)
                }
            }
        }
        headerLayout.addView(openBtn)

        // ----- COPY BUTTON -----
        val copyDrawable = ContextCompat.getDrawable(this, R.drawable.content_copy_24px)
        val copyBtn: View
        if (copyDrawable != null) {
            copyBtn = ImageView(this).apply {
                setImageDrawable(copyDrawable)
                setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(backBtnBgColor)
                }
                setPadding(dpToPx(14f), dpToPx(14f), dpToPx(14f), dpToPx(14f))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                isClickable = true
                isFocusable = true
                setOnClickListener { copyToClipboard(details) }
                setOnTouchListener(pressScaleTouchListener)
                layoutParams = LinearLayout.LayoutParams(dpToPx(48f), dpToPx(48f)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
            }
        } else {
            copyBtn = TextView(this).apply {
                text = "Copy"
                textSize = 14f
                setTextColor(primaryTextColor)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(backBtnBgColor)
                }
                setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(8f))
                isClickable = true
                isFocusable = true
                setOnClickListener { copyToClipboard(details) }
                setOnTouchListener(pressScaleTouchListener)
                layoutParams = LinearLayout.LayoutParams(dpToPx(48f), dpToPx(48f)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
            }
        }
        headerLayout.addView(copyBtn)

        rootLayout.addView(headerLayout)

        // Info row (package and time)
        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(12f), 0, dpToPx(12f))
        }
        val pkgTv = TextView(this).apply {
            text = "Package: $appName"
            textSize = 14f
            setTextColor(secondaryTextColor)
        }
        infoLayout.addView(pkgTv)
        val timeTv = TextView(this).apply {
            text = "Time: $timestamp"
            textSize = 14f
            setTextColor(secondaryTextColor)
        }
        infoLayout.addView(timeTv)
        rootLayout.addView(infoLayout)

        // Scrollable details – full content, no truncation
        val scrollDetails = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setFillViewport(true)
        }
        val detailsTv = TextView(this).apply {
            text = details
            textSize = 12f
            setTextColor(primaryTextColor)
            setTypeface(Typeface.MONOSPACE)
            setPadding(dpToPx(8f), dpToPx(8f), dpToPx(8f), dpToPx(8f))
            background = GradientDrawable().apply {
                setColor(inputBgColor)
                cornerRadius = dpToPx(8f).toFloat()
                setStroke(dpToPx(1f), cardBorderColor)
            }
            movementMethod = ScrollingMovementMethod.getInstance()
            maxLines = Int.MAX_VALUE
            // Disable horizontal scrolling so long lines wrap
            setHorizontallyScrolling(false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollDetails.addView(detailsTv)
        rootLayout.addView(scrollDetails)

        setContentView(rootLayout)
    }

    // ========== STATUS BAR COLOR (Fix issue 2) ==========
    private fun setStatusBarColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            val statusColor = if (isDark) Color.parseColor("#000000") else Color.parseColor("#F2F2F7")
            window.statusBarColor = statusColor
            // For light status bar icons on light theme (Android 6.0+)
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

    // ========== CONFIGURATION CHANGE (dark/light mode) ==========
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newDark = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (newDark != isDark) {
            showRestartDialog()
        }
    }

    private fun showRestartDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val cardBg = if (isDark) Color.parseColor("#1C1C1E") else Color.parseColor("#FFFFFF")
        val cardBorder = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")
        val primaryText = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#000000")
        val secondaryText = if (isDark) Color.parseColor("#8E8E93") else Color.parseColor("#6C6C70")
        val accent = if (isDark) Color.parseColor("#3E82F7") else Color.parseColor("#0066FF")
        val inputBg = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#F2F2F7")

        val dpToPx = { dp: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt() }

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dpToPx(28f).toFloat()
                setStroke(dpToPx(1f), cardBorder)
            }
            setPadding(dpToPx(24f), dpToPx(28f), dpToPx(24f), dpToPx(24f))
        }

        val titleTv = TextView(this).apply {
            text = "Theme Changed"
            textSize = 22f
            setTextColor(primaryText)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(4f))
        }
        cardLayout.addView(titleTv)

        val messageTv = TextView(this).apply {
            text = "The system dark/light mode has changed.\nPlease restart the app to apply the new theme."
            textSize = 15f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(16f))
        }
        cardLayout.addView(messageTv)

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val restartBtn = TextView(this).apply {
            text = "Restart Now"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(accent)
                cornerRadius = dpToPx(100f).toFloat()
            }
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(54f), 1f)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
                // Restart activity
                val intent = intent
                finish()
                startActivity(intent)
                overridePendingTransition(0, 0)
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        btnLayout.addView(restartBtn)

        val laterBtn = TextView(this).apply {
            text = "Later"
            textSize = 15f
            setTextColor(primaryText)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(inputBg)
                cornerRadius = dpToPx(100f).toFloat()
                setStroke(dpToPx(1f), cardBorder)
            }
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(54f), 1f).apply {
                marginStart = dpToPx(8f)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        btnLayout.addView(laterBtn)

        cardLayout.addView(btnLayout)

        dialog.setContentView(cardLayout)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        dialog.setCancelable(false)
        dialog.show()
    }

    // ========== EXISTING METHODS (unchanged) ==========
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Crash Log", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun createArrowBackDrawable(): Drawable {
        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryTextColor
                style = Paint.Style.STROKE
                strokeWidth = dpToPx(2.5f).toFloat()
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val size = dpToPx(6.5f)
                val path = Path().apply {
                    moveTo(cx + size * 0.4f, cy - size)
                    lineTo(cx - size * 0.5f, cy)
                    lineTo(cx + size * 0.4f, cy + size)
                }
                canvas.drawPath(path, paint)
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
            @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }

    private val pressScaleTouchListener = View.OnTouchListener { v, event ->
        val springBackInterpolator = android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().cancel()
                v.animate()
                    .scaleX(0.94f)
                    .scaleY(0.94f)
                    .alpha(0.85f)
                    .setDuration(120)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                    .start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().cancel()
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(350)
                    .setInterpolator(springBackInterpolator)
                    .start()
            }
        }
        false
    }

    private fun initColors() {
        isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        cardBgColor = if (isDark) Color.parseColor("#1C1C1E") else Color.parseColor("#FFFFFF")
        cardBorderColor = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")
        primaryTextColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#000000")
        secondaryTextColor = if (isDark) Color.parseColor("#8E8E93") else Color.parseColor("#6C6C70")
        accentColor = if (isDark) Color.parseColor("#3E82F7") else Color.parseColor("#0066FF")
        backBtnBgColor = if (isDark) Color.parseColor("#3A3A3C") else Color.parseColor("#E5E5EA")
        inputBgColor = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#F2F2F7")
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dpToPx(36f)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    }
}