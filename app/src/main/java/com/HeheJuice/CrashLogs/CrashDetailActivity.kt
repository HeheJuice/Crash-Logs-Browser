package com.HeheJuice.CrashLogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class CrashDetailActivity : AppCompatActivity() {

    // Use var with default values instead of lateinit for primitive types
    private var primaryTextColor: Int = 0
    private var secondaryTextColor: Int = 0
    private var accentColor: Int = 0
    private var inputBgColor: Int = 0
    private var cardBgColor: Int = 0
    private var cardBorderColor: Int = 0
    private var backBtnBgColor: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        initColors()

        val type = intent.getStringExtra("type") ?: "Crash"
        val appName = intent.getStringExtra("appName") ?: "Unknown"
        val timestamp = intent.getStringExtra("timestamp") ?: "Unknown"
        val details = intent.getStringExtra("details") ?: "No details available."

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBgColor)
            }
            setPadding(dpToPx(20f), dpToPx(20f), dpToPx(20f), dpToPx(20f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Header: Close (left), Title (center), Copy (right)
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Left: Back arrow (close)
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
            }
        }
        headerLayout.addView(backBtn)

        // Center: Title
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

        // Right: Copy
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
                setPadding(dpToPx(8f), dpToPx(8f), dpToPx(8f), dpToPx(8f))
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
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(48f)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
            }
        }
        headerLayout.addView(copyBtn)

        rootLayout.addView(headerLayout)

        // Separator
        val sep = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1f)
            )
            setBackgroundColor(cardBorderColor)
        }
        rootLayout.addView(sep)

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

        // Scrollable details
        val scrollDetails = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
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
        }
        scrollDetails.addView(detailsTv)
        rootLayout.addView(scrollDetails)

        setContentView(rootLayout)
    }

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
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        cardBgColor = if (isDark) Color.parseColor("#1C1C1E") else Color.parseColor("#FFFFFF")
        cardBorderColor = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")
        primaryTextColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#000000")
        secondaryTextColor = if (isDark) Color.parseColor("#8E8E93") else Color.parseColor("#6C6C70")
        accentColor = if (isDark) Color.parseColor("#3E82F7") else Color.parseColor("#0066FF")
        backBtnBgColor = if (isDark) Color.parseColor("#3A3A3C") else Color.parseColor("#E5E5EA")
        inputBgColor = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#F2F2F7")
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    }
}