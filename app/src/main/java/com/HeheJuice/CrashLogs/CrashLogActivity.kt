package com.HeheJuice.CrashLogs

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AppOpsManager
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class CrashLogActivity : Activity() {

    companion object {
        private const val TAB_LOGS = 0
        private const val TAB_INFO = 1
        private const val TAG = "CrashLogs"
        private const val FILTER_ALL = 0
        private const val FILTER_CRASH = 1
        private const val FILTER_ANR = 2
    }

    private var primaryTextColor: Int = 0
    private var secondaryTextColor: Int = 0
    private var accentColor: Int = 0
    private var inputBgColor: Int = 0
    private var cardBgColor: Int = 0
    private var cardBorderColor: Int = 0
    private var secondaryBtnColor: Int = 0
    private var redBtnColor: Int = 0
    private var backBtnBgColor: Int = 0
    private var buttonHeightPx: Int = 0
    private var isDark: Boolean = false

    private lateinit var recyclerView: RecyclerView
    private lateinit var logAdapter: LogAdapter
    private val allLogs = mutableListOf<LogEntry>()
    private var filteredLogs = mutableListOf<LogEntry>()

    private lateinit var logsLayout: LinearLayout
    private lateinit var infoLayout: LinearLayout

    // Bottom bar
    private lateinit var slidingPillView: View
    private lateinit var logsPillBtn: TextView
    private lateinit var infoPillBtn: TextView
    private var currentTab = TAB_LOGS

    // Filter pill (inside scroll)
    private lateinit var filterPillContainer: FrameLayout
    private lateinit var filterSlidingView: View
    private lateinit var filterAllBtn: TextView
    private lateinit var filterCrashBtn: TextView
    private lateinit var filterAnrBtn: TextView
    private var currentFilterTab = FILTER_ALL

    // Loading text view
    private lateinit var loadingText: TextView

    // Refresh timer
    private var refreshTimer: CountDownTimer? = null
    private lateinit var refreshButton: TextView

    private lateinit var rootFrameLayout: FrameLayout
    private lateinit var scrollView: NestedScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        initColors()
        buttonHeightPx = dpToPx(54f)

        if (!checkAllPermissions()) {
            showPermissionDialog()
            return
        }

        setupUI()
        loadLogsAsync {}
    }

    private fun setupUI() {
        val rootBgColor = if (isDark) Color.parseColor("#000000") else Color.parseColor("#F2F2F7")

        rootFrameLayout = FrameLayout(this).apply {
            setBackgroundColor(rootBgColor)
        }

        val statusBarHeight = getStatusBarHeight()

        // ========== SCROLL VIEW (NestedScrollView) ==========
        scrollView = NestedScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_ALWAYS
            clipToPadding = false
            isFillViewport = true
            setPadding(dpToPx(16f), statusBarHeight + dpToPx(60f), dpToPx(16f), dpToPx(140f))
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // ========== LOGS TAB ==========
        logsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // ---- FILTER PILL ----
        filterPillContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
                setColor(cardBgColor)
                setStroke(dpToPx(1f), cardBorderColor)
            }
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(44f)
            ).apply {
                bottomMargin = dpToPx(16f)
            }
        }

        val filterActiveBg = GradientDrawable().apply {
            setColor(secondaryBtnColor)
            cornerRadius = dpToPx(100f).toFloat()
        }
        filterSlidingView = View(this).apply {
            background = filterActiveBg
            layoutParams = FrameLayout.LayoutParams(0, dpToPx(36f))
        }

        val filterButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        filterAllBtn = TextView(this).apply {
            text = "ALL"
            textSize = 14f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36f), 1f)
            isClickable = false
        }
        filterCrashBtn = TextView(this).apply {
            text = "CRASH"
            textSize = 14f
            setTextColor(secondaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36f), 1f)
            isClickable = false
        }
        filterAnrBtn = TextView(this).apply {
            text = "ANR"
            textSize = 14f
            setTextColor(secondaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36f), 1f)
            isClickable = false
        }

        filterButtonsLayout.addView(filterAllBtn)
        filterButtonsLayout.addView(filterCrashBtn)
        filterButtonsLayout.addView(filterAnrBtn)

        filterPillContainer.addView(filterSlidingView)
        filterPillContainer.addView(filterButtonsLayout)

        filterPillContainer.post {
            filterSlidingView.layoutParams = filterSlidingView.layoutParams.apply {
                width = filterAllBtn.width
            }
            filterSlidingView.translationX = filterAllBtn.left.toFloat()
            filterSlidingView.requestLayout()
        }

        // Filter drag listener - synchronized with bottom bar logic
        filterPillContainer.setOnTouchListener { view, event ->
            val x0 = filterAllBtn.left.toFloat() + (filterAllBtn.width / 2f)
            val x1 = filterAnrBtn.left.toFloat() + (filterAnrBtn.width / 2f)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().cancel()
                    view.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .alpha(0.9f)
                        .setDuration(120)
                        .setInterpolator(DecelerateInterpolator(1.5f))
                        .start()

                    val touchX = event.x - filterPillContainer.paddingLeft
                    val progress = if (x1 > x0) ((touchX - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f
                    updateFilterPillPosition(progress)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val touchX = event.x - filterPillContainer.paddingLeft
                    val progress = if (x1 > x0) ((touchX - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f
                    updateFilterPillPosition(progress)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val touchX = event.x - filterPillContainer.paddingLeft
                    val progress = if (x1 > x0) ((touchX - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f
                    val targetProgress = when {
                        progress < 0.25f -> 0f
                        progress < 0.75f -> 0.5f
                        else -> 1f
                    }
                    val targetFilter = when (targetProgress) {
                        0f -> FILTER_ALL
                        0.5f -> FILTER_CRASH
                        else -> FILTER_ANR
                    }
                    animateFilterPillTo(targetProgress) {
                        switchFilterTab(targetFilter)
                    }
                    view.animate().cancel()
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        .setDuration(350)
                        .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f))
                        .start()
                    true
                }
                else -> false
            }
        }

        logsLayout.addView(filterPillContainer)

        // ---- Loading Text ----
        loadingText = TextView(this).apply {
            text = "Loading..."
            textSize = 18f
            setTextColor(secondaryTextColor)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                topMargin = dpToPx(40f)
                bottomMargin = dpToPx(40f)
            }
            visibility = View.GONE
        }
        logsLayout.addView(loadingText)

        // ---- RECYCLER VIEW ----
        recyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(this@CrashLogActivity)
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        logAdapter = LogAdapter(
            filteredLogs,
            this,
            packageManager,
            cardBgColor,
            cardBorderColor,
            ::onItemClick
        )
        recyclerView.adapter = logAdapter
        logsLayout.addView(recyclerView)

        // ========== INFO TAB ==========
        infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // ---- App Info Card ----
        val appInfoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground()
            setPadding(dpToPx(20f), dpToPx(24f), dpToPx(20f), dpToPx(24f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16f)
            }
        }

        val appNameTitle = TextView(this).apply {
            text = "Crash Logs Browser"
            textSize = 22f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
        }
        appInfoCard.addView(appNameTitle)

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
        val versionText = TextView(this).apply {
            text = "Version $versionName"
            textSize = 14f
            setTextColor(secondaryTextColor)
            setPadding(0, dpToPx(4f), 0, dpToPx(8f))
        }
        appInfoCard.addView(versionText)

        val descriptionText = TextView(this).apply {
            text = "View and monitor app crashes and ANRs"
            textSize = 14f
            setTextColor(secondaryTextColor)
            setPadding(0, 0, 0, dpToPx(16f))
        }
        appInfoCard.addView(descriptionText)

        val updatePill = TextView(this).apply {
            text = "Check for Updates"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16f), dpToPx(8f), dpToPx(16f), dpToPx(8f))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(100f).toFloat()
                setColor(accentColor)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@CrashLogActivity, DetailsActivity::class.java))
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        appInfoCard.addView(updatePill)

        infoLayout.addView(appInfoCard)

        // ---- Statistics Card ----
        val statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground()
            setPadding(dpToPx(20f), dpToPx(24f), dpToPx(20f), dpToPx(24f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16f)
            }
        }

        val statsTitle = TextView(this).apply {
            text = "Statistics"
            textSize = 20f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(16f))
        }
        statsCard.addView(statsTitle)

        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            tag = "statsRow"
        }
        statsRow.addView(createStatCard("Crashes", "0", redBtnColor, true))
        statsRow.addView(createStatCard("ANR", "0", accentColor, false))
        statsCard.addView(statsRow)

        // Refresh button
        refreshButton = createAnimatedButton("Refresh Logs", Color.WHITE, accentColor, buttonHeightPx) {
            performRefresh()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dpToPx(16f)
        }
        statsCard.addView(refreshButton)

        infoLayout.addView(statsCard)

        scrollContent.addView(logsLayout)
        scrollContent.addView(infoLayout)
        scrollView.addView(scrollContent)
        rootFrameLayout.addView(scrollView)

        // ========== TOP BAR ==========
        val topBarLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(rootBgColor)
            elevation = dpToPx(4f).toFloat()
            setPadding(dpToPx(16f), statusBarHeight + dpToPx(8f), dpToPx(16f), dpToPx(8f))
        }

        val topBarTitle = TextView(this).apply {
            text = "Crash Logs Browser"
            textSize = 16f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(backBtnBgColor)
                cornerRadius = dpToPx(100f).toFloat()
            }
            setPadding(dpToPx(20f), 0, dpToPx(20f), 0)
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(44f),
                Gravity.CENTER
            )
        }

        val backDrawable = createArrowBackDrawable()
        val backBtn = ImageView(this).apply {
            setImageDrawable(backDrawable)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(backBtnBgColor)
            }
            contentDescription = "Back"
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(dpToPx(44f), dpToPx(44f), Gravity.START or Gravity.CENTER_VERTICAL)
            setOnClickListener { finish() }
            setOnTouchListener(pressScaleTouchListener)
        }

        topBarLayout.addView(topBarTitle)
        topBarLayout.addView(backBtn)
        rootFrameLayout.addView(topBarLayout)

        // ========== BOTTOM BAR ==========
        val bottomBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dpToPx(16f)
            }
        }

        val tabPillContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
                setColor(cardBgColor)
                setStroke(dpToPx(1f), cardBorderColor)
            }
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val activeTabBg = GradientDrawable().apply {
            setColor(secondaryBtnColor)
            cornerRadius = dpToPx(100f).toFloat()
        }
        slidingPillView = View(this).apply {
            background = activeTabBg
            layoutParams = FrameLayout.LayoutParams(0, dpToPx(44f))
        }

        val tabButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        logsPillBtn = TextView(this).apply {
            text = "Logs"
            textSize = 14f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(44f)
            )
        }

        infoPillBtn = TextView(this).apply {
            text = "Info"
            textSize = 14f
            setTextColor(secondaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(44f)
            )
        }

        tabButtonsLayout.addView(logsPillBtn)
        tabButtonsLayout.addView(infoPillBtn)

        tabPillContainer.addView(slidingPillView)
        tabPillContainer.addView(tabButtonsLayout)

        tabPillContainer.post {
            slidingPillView.layoutParams = slidingPillView.layoutParams.apply {
                width = logsPillBtn.width
            }
            slidingPillView.translationX = logsPillBtn.left.toFloat()
            slidingPillView.requestLayout()
        }

        bottomBarLayout.addView(tabPillContainer)
        rootFrameLayout.addView(bottomBarLayout)

        // ====== BOTTOM PILL SWITCH ======
        val switchTab: (Int) -> Unit = { tab ->
            if (currentTab != tab) {
                currentTab = tab
                if (tab == TAB_LOGS) {
                    logsLayout.visibility = View.VISIBLE
                    infoLayout.visibility = View.GONE
                    logsLayout.alpha = 1f
                    logsLayout.translationY = 0f
                } else {
                    logsLayout.visibility = View.GONE
                    infoLayout.visibility = View.VISIBLE
                    infoLayout.alpha = 1f
                    infoLayout.translationY = 0f
                }
                scrollView.scrollTo(0, 0)
            }
        }

        tabPillContainer.setOnTouchListener { view, event ->
            val x0 = logsPillBtn.left.toFloat() + (logsPillBtn.width / 2f)
            val x1 = infoPillBtn.left.toFloat() + (infoPillBtn.width / 2f)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().cancel()
                    view.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .alpha(0.9f)
                        .setDuration(120)
                        .setInterpolator(DecelerateInterpolator(1.5f))
                        .start()

                    val touchX = event.x - tabPillContainer.paddingLeft
                    val progress = if (x1 > x0) ((touchX - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f
                    updatePillPosition(progress)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val touchX = event.x - tabPillContainer.paddingLeft
                    val progress = if (x1 > x0) ((touchX - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f
                    updatePillPosition(progress)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val touchX = event.x - tabPillContainer.paddingLeft
                    val midPoint = (x0 + x1) / 2f
                    val targetIsLogs = touchX < midPoint
                    val targetTab = if (targetIsLogs) TAB_LOGS else TAB_INFO
                    val targetProgress = if (targetIsLogs) 0f else 1f

                    animatePillTo(targetProgress) {
                        switchTab(targetTab)
                    }

                    view.animate().cancel()
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        .setDuration(350)
                        .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f))
                        .start()
                    true
                }
                else -> false
            }
        }

        // ========== SCROLL LISTENER ==========
        scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            val alpha = (scrollY / dpToPx(40f).toFloat()).coerceIn(0f, 1f)
            topBarTitle.alpha = alpha
        })

        // ========== WINDOW INSETS ==========
        rootFrameLayout.setOnApplyWindowInsetsListener { _, insets ->
            val topInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetTop
            }
            val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.ime()).bottom
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetBottom
            }
            val effectiveTop = if (topInset > 0) topInset else statusBarHeight

            topBarLayout.setPadding(dpToPx(16f), effectiveTop + dpToPx(8f), dpToPx(16f), dpToPx(8f))
            scrollView.setPadding(dpToPx(16f), effectiveTop + dpToPx(60f), dpToPx(16f), dpToPx(140f))
            (bottomBarLayout.layoutParams as FrameLayout.LayoutParams).bottomMargin = dpToPx(16f) + bottomInset
            insets
        }

        setContentView(rootFrameLayout)
    }

    // ========== REFRESH ==========
    private fun performRefresh() {
        if (refreshTimer != null) return

        refreshButton.isEnabled = false
        refreshButton.text = "Refreshing..."
        loadLogsAsync {
            refreshTimer = object : CountDownTimer(5000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val seconds = (millisUntilFinished / 1000).toInt()
                    refreshButton.text = "Cooldown $seconds"
                }
                override fun onFinish() {
                    refreshButton.text = "Refresh Logs"
                    refreshButton.isEnabled = true
                    refreshTimer = null
                }
            }.start()
        }
    }

    // ========== FILTER PILL FUNCTIONS ==========
    private fun updateFilterPillPosition(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        val x0 = filterAllBtn.left.toFloat()
        val x1 = filterAnrBtn.left.toFloat()
        val w0 = filterAllBtn.width.toFloat()
        val w1 = filterAnrBtn.width.toFloat()

        val currentX = x0 + (x1 - x0) * p
        val currentW = (w0 + (w1 - w0) * p).toInt()

        filterSlidingView.translationX = currentX
        if (filterSlidingView.layoutParams.width != currentW && currentW > 0) {
            filterSlidingView.layoutParams = filterSlidingView.layoutParams.apply { width = currentW }
            filterSlidingView.requestLayout()
        }

        filterAllBtn.setTextColor(if (p < 0.25f) primaryTextColor else secondaryTextColor)
        filterCrashBtn.setTextColor(if (p in 0.25f..0.75f) primaryTextColor else secondaryTextColor)
        filterAnrBtn.setTextColor(if (p > 0.75f) primaryTextColor else secondaryTextColor)
    }

    private fun animateFilterPillTo(targetProgress: Float, onEnd: () -> Unit) {
        val currentX = filterSlidingView.translationX
        val x0 = filterAllBtn.left.toFloat()
        val x1 = filterAnrBtn.left.toFloat()
        val currentProgress = if (x1 > x0) ((currentX - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f

        ValueAnimator.ofFloat(currentProgress, targetProgress).apply {
            duration = 220L
            interpolator = android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
            addUpdateListener { anim ->
                updateFilterPillPosition(anim.animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    updateFilterPillPosition(targetProgress)
                    onEnd()
                }
            })
            start()
        }
    }

    private fun switchFilterTab(filter: Int) {
        if (currentFilterTab != filter) {
            currentFilterTab = filter
            applyFilters()
        }
    }

    // ========== APPLY FILTERS ==========
    private fun applyFilters() {
        val result = when (currentFilterTab) {
            FILTER_CRASH -> allLogs.filter { it.type == "Crash" }
            FILTER_ANR -> allLogs.filter { it.type == "ANR" }
            else -> allLogs
        }
        filteredLogs.clear()
        filteredLogs.addAll(result)
        logAdapter.updateLogs(filteredLogs)
        recyclerView.alpha = 1f
        recyclerView.translationY = 0f
    }

    // ========== BOTTOM PILL FUNCTIONS ==========
    private fun updatePillPosition(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        val x0 = logsPillBtn.left.toFloat()
        val x1 = infoPillBtn.left.toFloat()
        val w0 = logsPillBtn.width.toFloat()
        val w1 = infoPillBtn.width.toFloat()

        val currentX = x0 + (x1 - x0) * p
        val currentW = (w0 + (w1 - w0) * p).toInt()

        slidingPillView.translationX = currentX
        if (slidingPillView.layoutParams.width != currentW && currentW > 0) {
            slidingPillView.layoutParams = slidingPillView.layoutParams.apply { width = currentW }
            slidingPillView.requestLayout()
        }

        if (p < 0.5f) {
            logsPillBtn.setTextColor(primaryTextColor)
            infoPillBtn.setTextColor(secondaryTextColor)
        } else {
            logsPillBtn.setTextColor(secondaryTextColor)
            infoPillBtn.setTextColor(primaryTextColor)
        }
    }

    private fun animatePillTo(targetProgress: Float, onEnd: () -> Unit) {
        val currentX = slidingPillView.translationX
        val x0 = logsPillBtn.left.toFloat()
        val x1 = infoPillBtn.left.toFloat()
        val currentProgress = if (x1 > x0) ((currentX - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f

        ValueAnimator.ofFloat(currentProgress, targetProgress).apply {
            duration = 220L
            interpolator = android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
            addUpdateListener { anim ->
                updatePillPosition(anim.animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            })
            start()
        }
    }

    // ========== LOG PARSING ==========
    private fun loadLogsAsync(onComplete: () -> Unit) {
        loadingText.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        Thread {
            loadLogs()
            runOnUiThread {
                loadingText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                applyFilters()
                updateStats()
                onComplete()
            }
        }.start()
    }

    private fun updateStats() {
        val statsRow = infoLayout.findViewWithTag<LinearLayout>("statsRow") ?: return
        for (i in 0 until statsRow.childCount) {
            val card = statsRow.getChildAt(i) as? LinearLayout ?: continue
            val valueTv = card.getChildAt(1) as? TextView ?: continue
            val label = (card.getChildAt(2) as? TextView)?.text.toString()
            when (label) {
                "Crashes" -> valueTv.text = allLogs.count { it.type == "Crash" }.toString()
                "ANR" -> valueTv.text = allLogs.count { it.type == "ANR" }.toString()
            }
        }
    }

    private fun loadLogs() {
        allLogs.clear()

        val logcatCmd = if (hasRoot()) {
            "su -c logcat -b crash -b main -b system -d -v time -t 5000"
        } else {
            "logcat -b crash -b main -b system -d -v time -t 5000"
        }

        if (checkReadLogsPermission() || hasRoot()) {
            try {
                val process = Runtime.getRuntime().exec(logcatCmd)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val lines = reader.readLines()
                process.waitFor()

                var i = 0
                while (i < lines.size) {
                    val line = lines[i]
                    if (isRealCrashLine(line)) {
                        val block = mutableListOf<String>()
                        block.add(line)
                        val timestamp = extractTimestamp(line)
                        val type = if (line.contains("ANR") || line.contains("ANR in")) "ANR" else "Crash"
                        i++
                        while (i < lines.size) {
                            val nextLine = lines[i]
                            if (nextLine.matches(Regex("\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}.*")) && isRealCrashLine(nextLine)) {
                                break
                            }
                            block.add(nextLine)
                            i++
                        }
                        var appName = extractPackageName(block)
                        if (appName != "System Process" && appName != "com.android.system") {
                            allLogs.add(LogEntry(timestamp, appName, type, block.joinToString("\n")))
                        }
                    } else {
                        i++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read logcat", e)
            }
        }

        if (checkDropBoxPermission()) {
            loadDropBoxLogs()
        }
    }

    private fun hasRoot(): Boolean {
        val suPaths = arrayOf(
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in suPaths) {
            if (java.io.File(path).exists()) return true
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine() != null
        } catch (e: Exception) {
            false
        }
    }

    private fun isRealCrashLine(line: String): Boolean {
        if (line.length < 10) return false
        return line.contains("FATAL EXCEPTION") ||
                line.contains("ANR in") ||
                line.contains("SIGABRT") ||
                line.contains("SIGSEGV") ||
                line.contains("Native crash") ||
                line.contains("signal 11") ||
                line.contains("signal 6") ||
                (line.contains("Abort message") && line.contains("FATAL")) ||
                (line.contains("backtrace:") && line.contains("pid:")) ||
                (line.contains("Process:") && line.contains(" crashed") && (line.contains("pid:") || line.contains("signal")))
    }

    private fun extractTimestamp(line: String): String {
        val pattern = Regex("\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")
        val match = pattern.find(line)
        return match?.value?.replace("-", "/") ?: "Unknown"
    }

    private fun extractPackageName(block: List<String>): String {
        for (line in block) {
            val processMatch = Regex("(?:Process|Package|Process name):\\s*([\\w.:]+)").find(line)
            if (processMatch != null) {
                val pkg = processMatch.groupValues[1].substringBefore(":")
                if (pkg.isNotBlank() && pkg != "System") return pkg
            }
            val bracketMatch = Regex(">>>\\s*([\\w.:]+)\\s*<<<").find(line)
            if (bracketMatch != null) {
                val pkg = bracketMatch.groupValues[1].substringBefore(":")
                if (pkg.isNotBlank()) return pkg
            }
            val cmdlineMatch = Regex("Cmdline:\\s*([^\\s]+)").find(line)
            if (cmdlineMatch != null) {
                val pkg = cmdlineMatch.groupValues[1].substringAfterLast("/").substringBefore(":")
                if (pkg.isNotBlank()) return pkg
            }
        }
        return "Unknown Process"
    }

    private fun loadDropBoxLogs() {
        try {
            val dropBox = getSystemService(Context.DROPBOX_SERVICE) as? android.os.DropBoxManager ?: return
            val tags = setOf(
                "system_app_native_crash",
                "system_app_crash",
                "data_app_crash",
                "system_app_anr",
                "data_app_anr",
                "SYSTEM_TOMBSTONE"
            )
            var entry = dropBox.getNextEntry(null, 0)
            while (entry != null) {
                try {
                    val tag = entry.tag
                    if (tags.contains(tag)) {
                        val text = entry.getText(65536) ?: ""
                        val type = if (tag.contains("anr", ignoreCase = true)) "ANR" else "Crash"
                        var pkg = extractPackageName(text.lines())
                        if (pkg == "Unknown Process") {
                            pkg = tag
                        }
                        if (pkg != "System Process" && !pkg.startsWith("com.android.system")) {
                            val timeStr = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
                                .format(Date(entry.timeMillis))
                            allLogs.add(0, LogEntry(timeStr, pkg, type, "[$tag]\n$text"))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Skipping corrupt DropBox entry", e)
                }
                val nextEntry = dropBox.getNextEntry(null, entry.timeMillis)
                entry.close()
                entry = nextEntry
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read DropBoxManager", e)
        }
    }

    // ========== PERMISSION CHECKS ==========
    private fun checkAllPermissions(): Boolean {
        return checkReadLogsPermission() && checkDropBoxPermission() && checkUsageStatsPermission()
    }

    private fun checkReadLogsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            checkCallingOrSelfPermission("android.permission.READ_LOGS") == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkDropBoxPermission(): Boolean {
        return checkCallingOrSelfPermission("android.permission.READ_DROPBOX_DATA") == PackageManager.PERMISSION_GRANTED
    }

    private fun checkUsageStatsPermission(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    packageName
                )
            }
            if (mode == AppOpsManager.MODE_DEFAULT) {
                checkCallingOrSelfPermission("android.permission.PACKAGE_USAGE_STATS") == PackageManager.PERMISSION_GRANTED
            } else {
                mode == AppOpsManager.MODE_ALLOWED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check usage stats permission", e)
            false
        }
    }

    private fun isPermissionGranted(permName: String): Boolean {
        return when (permName) {
            "READ_LOGS" -> checkReadLogsPermission()
            "READ_DROPBOX_DATA" -> checkDropBoxPermission()
            "PACKAGE_USAGE_STATS" -> checkUsageStatsPermission()
            else -> false
        }
    }

    // ========== ROOT PERMISSION GRANT ==========
    private fun grantPermissionsWithRoot() {
        Toast.makeText(this, "Requesting root permissions...", Toast.LENGTH_SHORT).show()
        Thread {
            val commands = listOf(
                "pm grant ${packageName} android.permission.READ_LOGS",
                "pm grant ${packageName} android.permission.READ_DROPBOX_DATA",
                "appops set ${packageName} GET_USAGE_STATS allow"
            )

            val success = runRootCommands(commands)

            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Permissions granted via root!", Toast.LENGTH_LONG).show()
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (checkAllPermissions()) {
                            setupUI()
                            loadLogsAsync {}
                        } else {
                            Toast.makeText(this, "Some permissions still not granted. Try rebooting.", Toast.LENGTH_LONG).show()
                            showPermissionDialog()
                        }
                    }, 500)
                } else {
                    Toast.makeText(this, "Root permission required or failed. Please grant manually in Magisk/KSU.", Toast.LENGTH_LONG).show()
                    showPermissionDialog()
                }
            }
        }.start()
    }

    private fun runRootCommands(commands: List<String>): Boolean {
        return try {
            val script = commands.joinToString(" ; ")
            val process = ProcessBuilder("su", "-c", script)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            Log.d(TAG, "Root output: $output")
            val exitCode = process.waitFor()
            Log.d(TAG, "Root exit code: $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Root command failed", e)
            false
        }
    }

    // ========== PERMISSION DIALOG ==========
    private fun showPermissionDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBgColor)
                cornerRadius = dpToPx(28f).toFloat()
                setStroke(dpToPx(1f), cardBorderColor)
            }
            setPadding(dpToPx(24f), dpToPx(28f), dpToPx(24f), dpToPx(24f))
        }

        val lockDrawable = ContextCompat.getDrawable(this, R.drawable.lock_24px)?.apply {
            setTint(primaryTextColor)
        }
        val iconIv = ImageView(this).apply {
            setImageDrawable(lockDrawable)
            layoutParams = LinearLayout.LayoutParams(dpToPx(60f), dpToPx(60f)).apply {
                gravity = Gravity.CENTER
                bottomMargin = dpToPx(8f)
            }
        }
        cardLayout.addView(iconIv)

        val titleTv = TextView(this).apply {
            text = "Permissions Required"
            textSize = 22f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(4f))
        }
        cardLayout.addView(titleTv)

        val subTv = TextView(this).apply {
            text = "This app needs the following permissions to read crash logs"
            textSize = 14f
            setTextColor(secondaryTextColor)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(16f))
        }
        cardLayout.addView(subTv)

        // ---- Permission status list ----
        val permissions = listOf(
            "READ_LOGS" to "Read system logs",
            "READ_DROPBOX_DATA" to "Access crash data",
            "PACKAGE_USAGE_STATS" to "App usage statistics"
        )

        for ((perm, desc) in permissions) {
            val permLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(inputBgColor)
                    cornerRadius = dpToPx(12f).toFloat()
                    setStroke(dpToPx(1f), cardBorderColor)
                }
                setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(8f)
                }
            }

            val granted = isPermissionGranted(perm)
            val iconRes = if (granted) R.drawable.check_circle_24px else R.drawable.cancel_24px
            val statusDrawable = ContextCompat.getDrawable(this@CrashLogActivity, iconRes)?.apply {
                setTint(if (granted) Color.parseColor("#4CAF50") else Color.parseColor("#F44336"))
            }
            val statusIv = ImageView(this).apply {
                setImageDrawable(statusDrawable)
                layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f)).apply {
                    marginEnd = dpToPx(12f)
                }
            }
            permLayout.addView(statusIv)

            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val permName = TextView(this).apply {
                text = perm
                textSize = 14f
                setTextColor(primaryTextColor)
                setTypeface(null, Typeface.BOLD)
            }
            textLayout.addView(permName)

            val permDesc = TextView(this).apply {
                text = desc
                textSize = 12f
                setTextColor(secondaryTextColor)
            }
            textLayout.addView(permDesc)

            permLayout.addView(textLayout)
            cardLayout.addView(permLayout)
        }

        // ---- ADB commands block with copy icon ----
        val adbLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(redBtnColor)
                setAlpha(30)
                cornerRadius = dpToPx(12f).toFloat()
                setStroke(dpToPx(1f), redBtnColor)
            }
            setPadding(dpToPx(16f), dpToPx(14f), dpToPx(16f), dpToPx(14f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12f)
                bottomMargin = dpToPx(12f)
            }
        }

        // Header: title + copy icon
        val adbHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val adbTitle = TextView(this).apply {
            text = "Grant via ADB (one by one)"
            textSize = 14f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        adbHeader.addView(adbTitle)

        val copyIcon = ImageView(this).apply {
            setImageResource(R.drawable.content_copy_24px)
            setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
            isClickable = true
            isFocusable = true
            setPadding(dpToPx(8f), dpToPx(8f), dpToPx(8f), dpToPx(8f))
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(32f),
                dpToPx(32f)
            )
            setOnClickListener {
                val commands = listOf(
                    "adb shell pm grant ${packageName} android.permission.READ_LOGS",
                    "adb shell pm grant ${packageName} android.permission.READ_DROPBOX_DATA",
                    "adb shell appops set ${packageName} GET_USAGE_STATS allow"
                )
                val textToCopy = commands.joinToString("\n")
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("ADB Commands", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@CrashLogActivity, "ADB commands copied", Toast.LENGTH_SHORT).show()
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        adbHeader.addView(copyIcon)

        adbLayout.addView(adbHeader)

        val adbCommand1 = TextView(this).apply {
            text = "adb shell pm grant ${packageName} android.permission.READ_LOGS"
            textSize = 11f
            setTextColor(accentColor)
            setTypeface(Typeface.MONOSPACE)
            setPadding(0, dpToPx(4f), 0, 0)
        }
        adbLayout.addView(adbCommand1)

        val adbCommand2 = TextView(this).apply {
            text = "adb shell pm grant ${packageName} android.permission.READ_DROPBOX_DATA"
            textSize = 11f
            setTextColor(accentColor)
            setTypeface(Typeface.MONOSPACE)
        }
        adbLayout.addView(adbCommand2)

        val adbCommand3 = TextView(this).apply {
            text = "adb shell appops set ${packageName} GET_USAGE_STATS allow"
            textSize = 11f
            setTextColor(accentColor)
            setTypeface(Typeface.MONOSPACE)
        }
        adbLayout.addView(adbCommand3)

        val adbNote = TextView(this).apply {
            text = "Some permissions may require a reboot to take effect"
            textSize = 11f
            setTextColor(secondaryTextColor)
            setPadding(0, dpToPx(8f), 0, 0)
        }
        adbLayout.addView(adbNote)

        cardLayout.addView(adbLayout)

        // ---- Grant with Root ----
        val rootGrantBtn = createAnimatedButton(
            "Grant with Root (Auto)",
            Color.WHITE,
            Color.parseColor("#FF6B00"),
            buttonHeightPx
        ) {
            dialog.dismiss()
            grantPermissionsWithRoot()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dpToPx(8f)
        }
        cardLayout.addView(rootGrantBtn)

        // ---- Button row: Exit App & Check Again ----
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                buttonHeightPx
            ).apply {
                topMargin = dpToPx(12f)
            }
        }

        val exitBtn = createAnimatedButton(
            "Exit App",
            primaryTextColor,
            secondaryBtnColor,
            LinearLayout.LayoutParams.MATCH_PARENT
        ) {
            dialog.dismiss()
            finish()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = dpToPx(6f)
            }
        }

        val checkBtn = createAnimatedButton(
            "Check Again",
            Color.WHITE,
            accentColor,
            LinearLayout.LayoutParams.MATCH_PARENT
        ) {
            if (checkAllPermissions()) {
                dialog.dismiss()
                setupUI()
                loadLogsAsync {}
            } else {
                Toast.makeText(this@CrashLogActivity, "Permissions still not granted. Please grant via ADB or Root.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
                showPermissionDialog()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dpToPx(6f)
            }
        }

        btnRow.addView(exitBtn)
        btnRow.addView(checkBtn)
        cardLayout.addView(btnRow)

        dialog.setContentView(cardLayout)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        dialog.setCancelable(false)
        dialog.show()
    }

    // ========== UI HELPERS ==========
    private fun initColors() {
        isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        cardBgColor = if (isDark) Color.parseColor("#1C1C1E") else Color.parseColor("#FFFFFF")
        cardBorderColor = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")
        primaryTextColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#000000")
        secondaryTextColor = if (isDark) Color.parseColor("#8E8E93") else Color.parseColor("#6C6C70")
        accentColor = if (isDark) Color.parseColor("#3E82F7") else Color.parseColor("#0066FF")
        secondaryBtnColor = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")
        redBtnColor = if (isDark) Color.parseColor("#FF453A") else Color.parseColor("#FF3B30")
        backBtnBgColor = if (isDark) Color.parseColor("#3A3A3C") else Color.parseColor("#E5E5EA")
        inputBgColor = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#F2F2F7")
    }

    private fun createCardBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(cardBgColor)
            cornerRadius = dpToPx(28f).toFloat()
            setStroke(dpToPx(1f), cardBorderColor)
        }
    }

    private fun createStatCard(label: String, value: String, color: Int, isError: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(inputBgColor)
                cornerRadius = dpToPx(16f).toFloat()
                setStroke(dpToPx(1f), cardBorderColor)
            }
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dpToPx(6f)
                marginEnd = dpToPx(6f)
            }

            val iconRes = if (isError) R.drawable.error_24px else R.drawable.warning_24px
            val iconDrawable = ContextCompat.getDrawable(context, iconRes)?.apply { setTint(color) }
            val iconView = ImageView(context).apply {
                setImageDrawable(iconDrawable)
                layoutParams = LinearLayout.LayoutParams(dpToPx(32f), dpToPx(32f)).apply {
                    gravity = Gravity.CENTER
                    bottomMargin = dpToPx(4f)
                }
            }
            addView(iconView)

            val valueTv = TextView(context).apply {
                text = value
                textSize = 22f
                setTextColor(color)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            addView(valueTv)

            val labelTv = TextView(context).apply {
                text = label
                textSize = 12f
                setTextColor(secondaryTextColor)
                gravity = Gravity.CENTER
            }
            addView(labelTv)
        }
    }

    private fun createAnimatedButton(textStr: String, textColor: Int, bgColor: Int, height: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = textStr
            textSize = 15f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dpToPx(100f).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            setOnTouchListener(pressScaleTouchListener)
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
                    .setInterpolator(DecelerateInterpolator(1.5f))
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

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dpToPx(36f)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    }

    private fun dpToPx(dp: Int): Int = dpToPx(dp.toFloat())

    private fun onItemClick(entry: LogEntry) {
        val intent = Intent(this, CrashDetailActivity::class.java).apply {
            putExtra("type", entry.type)
            putExtra("appName", entry.appName)
            putExtra("timestamp", entry.timestamp)
            putExtra("details", entry.details)
        }
        startActivity(intent)
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
}

// ========== DATA CLASSES ==========
data class LogEntry(
    val timestamp: String,
    val appName: String,
    val type: String,
    val details: String = ""
)

// ========== LOG ADAPTER ==========
class LogAdapter(
    private var logs: List<LogEntry>,
    private val context: Context,
    private val packageManager: PackageManager,
    private val cardBgColor: Int,
    private val cardBorderColor: Int,
    private val onItemClick: (LogEntry) -> Unit
) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val defaultIcon = ContextCompat.getDrawable(
        context,
        android.R.drawable.sym_def_app_icon
    )

    fun updateLogs(newLogs: List<LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(
            logs[position],
            packageManager,
            defaultIcon,
            cardBgColor,
            cardBorderColor
        )
    }

    override fun getItemCount(): Int = logs.size

    class LogViewHolder(
        itemView: View,
        private val onItemClick: (LogEntry) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val appNameText: TextView = itemView.findViewById(R.id.appNameText)
        private val typeBadge: TextView = itemView.findViewById(R.id.typeBadge)
        private val cardLayout: LinearLayout = itemView.findViewById(R.id.cardLayout)

        init {
            itemView.isClickable = true
            itemView.isFocusable = true
            val outValue = android.util.TypedValue()
            itemView.context.theme.resolveAttribute(
                android.R.attr.selectableItemBackground,
                outValue,
                true
            )
            if (outValue.resourceId != 0) {
                itemView.foreground = ContextCompat.getDrawable(itemView.context, outValue.resourceId)
            }
        }

        fun bind(log: LogEntry, pm: PackageManager, defaultIcon: Drawable?, cardBg: Int, cardBorder: Int) {
            timestampText.text = log.timestamp
            val cleanPackage = log.appName.substringBefore(":")
            appNameText.text = cleanPackage

            val cardDrawable = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    16f,
                    itemView.context.resources.displayMetrics
                )
                setStroke(
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        1f,
                        itemView.context.resources.displayMetrics
                    ).toInt(),
                    cardBorder
                )
            }
            cardLayout.background = cardDrawable

            val isMagisk = cleanPackage.equals("magisk", ignoreCase = true)
            val badgeText = if (isMagisk) "Magisk" else log.type
            typeBadge.text = badgeText

            var iconLoaded = false
            if (cleanPackage.isNotEmpty() && cleanPackage.contains(".")) {
                try {
                    val appInfo = pm.getApplicationInfo(cleanPackage, 0)
                    appIcon.setImageDrawable(pm.getApplicationIcon(appInfo))
                    iconLoaded = true
                } catch (e: PackageManager.NameNotFoundException) {
                    try {
                        val packages = pm.getInstalledApplications(0)
                        for (pkg in packages) {
                            if (pkg.packageName.equals(cleanPackage, ignoreCase = true)) {
                                appIcon.setImageDrawable(pm.getApplicationIcon(pkg))
                                iconLoaded = true
                                break
                            }
                        }
                    } catch (e2: Exception) { /* ignore */ }
                }
            }
            if (!iconLoaded) {
                appIcon.setImageDrawable(defaultIcon)
            }

            val radiusPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                100f,
                itemView.context.resources.displayMetrics
            )
            val badgeColor = when {
                isMagisk -> Color.parseColor("#00af9c")
                log.type == "Crash" -> ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark)
                log.type == "ANR" -> ContextCompat.getColor(itemView.context, android.R.color.holo_orange_dark)
                else -> ContextCompat.getColor(itemView.context, android.R.color.darker_gray)
            }
            val badgeDrawable = GradientDrawable().apply {
                cornerRadius = radiusPx
                setColor(badgeColor)
            }
            typeBadge.background = badgeDrawable
            typeBadge.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))

            itemView.setOnClickListener { onItemClick(log) }
        }
    }
}
