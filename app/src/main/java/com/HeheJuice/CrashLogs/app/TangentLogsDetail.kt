package com.HeheJuice.CrashLogs.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.HeheJuice.CrashLogs.R
import com.HeheJuice.CrashLogs.util.MonetColorManager
import com.HeheJuice.CrashLogs.util.OptimizeEngine
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.io.OutputStream
import java.util.concurrent.Executors

class TangentLogsDetail : AppCompatActivity() {

    companion object {
        var cachedLogContent: String = ""
    }

    private var surfaceContainerColor: Int = 0
    private var surfaceBrightColor: Int = 0
    private var onSurfaceColor: Int = 0
    private var onSurfaceVariantColor: Int = 0
    private var primaryContainerColor: Int = 0
    private var onPrimaryContainerColor: Int = 0
    private var primaryColor: Int = 0

    private val feedbackTargets = mutableListOf<OptimizeEngine.FeedbackTarget>()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var logContent: String
    private lateinit var logTitle: String

    private fun dpToPx(dp: Float): Int = OptimizeEngine.dpToPx(dp)
    private fun dpToPx(dp: Int): Int = OptimizeEngine.dpToPx(dp)

    private val createFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
                outputStream?.use { it.write(logContent.toByteArray()) }
                Toast.makeText(this, getString(R.string.log_saved), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.log_save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        logContent = cachedLogContent.ifEmpty { "No content" }
        logTitle = intent.getStringExtra("log_title") ?: "Unknown"

        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark

        MonetColorManager.applyToActivity(this)

        surfaceContainerColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurfaceContainer,
            if (isDark) Color.parseColor("#1C1B1F") else Color.parseColor("#FEF7FF"))
        surfaceBrightColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurfaceBright,
            if (isDark) Color.parseColor("#3B3B3E") else Color.parseColor("#FFFFFF"))
        onSurfaceColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurface,
            if (isDark) Color.parseColor("#E6E1E5") else Color.parseColor("#1C1B1F"))
        onSurfaceVariantColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            Color.parseColor("#49454F"))
        primaryContainerColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorPrimaryContainer,
            Color.parseColor("#E8DEF8"))
        onPrimaryContainerColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            Color.parseColor("#4F378B"))
        primaryColor = MaterialColors.getColor(this,
            androidx.appcompat.R.attr.colorPrimary,
            if (isDark) Color.parseColor("#D0BCFF") else Color.parseColor("#6750A4"))

        val mainContainer = FrameLayout(this).apply {
            setBackgroundColor(surfaceContainerColor)
            clipChildren = false
            clipToPadding = false
        }

        // --- 內容區 (初始為隱藏或透明，等加載完顯示) ---
        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            visibility = View.INVISIBLE
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
        }

        val contentView = TextView(this).apply {
            textSize = 12f
            setTextColor(onSurfaceColor)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setLineSpacing(dpToPx(2f).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(16f)
                bottomMargin = dpToPx(64f)
            }
        }

        root.addView(contentView)
        scrollView.addView(root)

        // --- 頂部按鈕列 ---
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            clipChildren = false
            clipToPadding = false
        }

        val btnSize = dpToPx(50f)
        val btnPad = dpToPx(12f)

        val backBtn = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@TangentLogsDetail, R.drawable.arrow_back_24px))
            background = OptimizeEngine.circleBackground(surfaceBrightColor)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(btnPad, btnPad, btnPad, btnPad)
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.back_content_desc)
            setOnClickListener {
                OptimizeEngine.vibrateTick(this@TangentLogsDetail)
                finish()
            }
        }
        topBar.addView(backBtn)
        feedbackTargets += OptimizeEngine.FeedbackTarget(backBtn)

        val titleTv = TextView(this).apply {
            text = logTitle
            textSize = 18f
            setTextColor(onSurfaceColor)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(16f)
                marginEnd = dpToPx(16f)
            }
        }
        topBar.addView(titleTv)

        val actionPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = OptimizeEngine.pillBackground(surfaceBrightColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, btnSize)
            clipToOutline = true
        }
        feedbackTargets += OptimizeEngine.FeedbackTarget(actionPill)

        val copyBtn = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@TangentLogsDetail, R.drawable.content_copy_24px))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(btnPad, btnPad, btnPad, btnPad)
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.log_copy)
            setOnClickListener {
                OptimizeEngine.vibrateTick(this@TangentLogsDetail)
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("log_content", logContent))
                Toast.makeText(this@TangentLogsDetail, getString(R.string.log_copied), Toast.LENGTH_SHORT).show()
            }
        }
        actionPill.addView(copyBtn)

        val extractBtn = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@TangentLogsDetail, R.drawable.news_24px))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(btnPad, btnPad, btnPad, btnPad)
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.log_extract)
            setOnClickListener {
                OptimizeEngine.vibrateTick(this@TangentLogsDetail)
                val safeName = logTitle.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(20)
                createFileLauncher.launch("crash_${safeName}_${System.currentTimeMillis()}.txt")
            }
        }
        actionPill.addView(extractBtn)

        topBar.addView(actionPill)

        val topScrim = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(80f))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    ColorUtils.setAlphaComponent(surfaceContainerColor, 250),
                    ColorUtils.setAlphaComponent(surfaceContainerColor, 250),
                    ColorUtils.setAlphaComponent(surfaceContainerColor, 0)
                )
            )
        }

        // --- ★ 全新全屏加載浮層 ---
        val loadingOverlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            // 背景保持透明，讓主背景色透出來
            setBackgroundColor(Color.TRANSPARENT)
        }

        val wavyThemeContext = ContextThemeWrapper(
            this,
            com.google.android.material.R.style.Widget_Material3Expressive_CircularProgressIndicator_Wavy
        )
        val loadingView = CircularProgressIndicator(wavyThemeContext).apply {
            isIndeterminate = true
            trackThickness = dpToPx(4f)
            setTrackColor(ColorUtils.setAlphaComponent(onSurfaceVariantColor, 80))
            setIndicatorColor(primaryColor)
            layoutParams = FrameLayout.LayoutParams(dpToPx(64f), dpToPx(64f)).apply {
                // 絕對居中
                gravity = Gravity.CENTER
            }
        }
        loadingOverlay.addView(loadingView)

        mainContainer.addView(scrollView)
        mainContainer.addView(topScrim)
        mainContainer.addView(loadingOverlay) // 加載圈在最上層
        mainContainer.addView(topBar)         // 頂部按鈕在加載圈之上

        setContentView(mainContainer)

        // ★ 核心優化：在後台線程處理巨型文本
        ioExecutor.execute {
            var displayContent = logContent
            if (displayContent.length > 500000) {
                displayContent = displayContent.take(500000) + "\n\n...[Truncated due to size]"
            }
            
            // 保證加載圈至少顯示一瞬間，避免閃爍
            Thread.sleep(200)

            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                
                contentView.text = displayContent
                
                // 淡出加載圈，淡入內容
                scrollView.visibility = View.VISIBLE
                scrollView.alpha = 0f
                scrollView.animate().alpha(1f).setDuration(300).start()
                
                loadingOverlay.animate().alpha(0f).setDuration(300).withEndAction {
                    mainContainer.removeView(loadingOverlay)
                }.start()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            topBar.setPadding(
                dpToPx(16f),
                statusBarTop + dpToPx(8f),
                dpToPx(16f),
                dpToPx(8f)
            )

            val scrimHeight = statusBarTop + dpToPx(8f + 50f + 8f + 12f)
            val scrimParams = topScrim.layoutParams as FrameLayout.LayoutParams
            scrimParams.height = scrimHeight
            topScrim.layoutParams = scrimParams

            val totalTopBarHeight = statusBarTop + dpToPx(8f + 50f + 8f)
            root.setPadding(
                dpToPx(16f),
                totalTopBarHeight,
                dpToPx(16f),
                navBarBottom + dpToPx(16f)
            )
            insets
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        for (t in feedbackTargets) t.handleEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        feedbackTargets.forEach { it.destroy() }
        feedbackTargets.clear()
        super.onDestroy()
    }
}