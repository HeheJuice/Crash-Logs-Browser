package com.HeheJuice.CrashLogs

import android.animation.Animator
import androidx.core.content.ContextCompat
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AppOpsManager
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Process
import android.util.Log
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.*
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

    // MiuiX Bottom Floating Nav Components
    private lateinit var navContainer: FrameLayout
    private lateinit var slidingPillView: View
    private lateinit var logsTabItem: LinearLayout
    private lateinit var infoTabItem: LinearLayout
    private lateinit var logsIconView: ImageView
    private lateinit var infoIconView: ImageView
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

    private lateinit var loadingText: TextView
    private var refreshTimer: CountDownTimer? = null
    private lateinit var refreshButton: TextView

    private lateinit var rootFrameLayout: FrameLayout
    private lateinit var scrollView: NestedScrollView

    // HyperOS / MiuiX Spring Curve
    private val miuixSpringInterpolator = PathInterpolator(0.2f, 1.0f, 0.36f, 1.0f)

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
        val rootBgColor = if (isDark) Color.parseColor("#0A0A0C") else Color.parseColor("#F2F2F7")

        rootFrameLayout = FrameLayout(this).apply {
            setBackgroundColor(rootBgColor)
        }

        val statusBarHeight = getStatusBarHeight()

        // ========== SCROLL VIEW ==========
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

        filterAllBtn = createFilterButton("ALL", primaryTextColor)
        filterCrashBtn = createFilterButton("CRASH", secondaryTextColor)
        filterAnrBtn = createFilterButton("ANR", secondaryTextColor)

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

        setupFilterTouchEvents()
        logsLayout.addView(filterPillContainer)

        // Loading Text
        loadingText = TextView(this).apply {
            text = "Loading..."
            textSize = 16f
            setTextColor(secondaryTextColor)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
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
            primaryTextColor,
            secondaryTextColor,
            accentColor,
            redBtnColor,
            ::onItemClick,
            this::dpToPx
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

        val appInfoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground()
            setPadding(dpToPx(20f), dpToPx(24f), dpToPx(20f), dpToPx(24f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16f) }
        }

        appInfoCard.addView(TextView(this).apply {
            text = "Crash Logs Browser"
            textSize = 22f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
        })

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }

        appInfoCard.addView(TextView(this).apply {
            text = "Version $versionName"
            textSize = 14f
            setTextColor(secondaryTextColor)
            setPadding(0, dpToPx(4f), 0, dpToPx(8f))
        })

        appInfoCard.addView(TextView(this).apply {
            text = "View and monitor system app crashes & ANRs"
            textSize = 14f
            setTextColor(secondaryTextColor)
            setPadding(0, 0, 0, dpToPx(16f))
        })

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
            setOnClickListener {
                startActivity(Intent(this@CrashLogActivity, DetailsActivity::class.java))
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        appInfoCard.addView(updatePill)
        infoLayout.addView(appInfoCard)

        // Statistics Card
        val statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground()
            setPadding(dpToPx(20f), dpToPx(24f), dpToPx(20f), dpToPx(24f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16f) }
        }

        statsCard.addView(TextView(this).apply {
            text = "Statistics"
            textSize = 20f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(16f))
        })

        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            tag = "statsRow"
        }
        statsRow.addView(createStatCard("Crashes", "0", redBtnColor))
        statsRow.addView(createStatCard("ANR", "0", accentColor))
        statsCard.addView(statsRow)

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
            text = "Crash Logs"
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

        val backBtn = ImageView(this).apply {
            setImageDrawable(createArrowBackDrawable())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(backBtnBgColor)
            }
            contentDescription = "Back"
            isClickable = true
            layoutParams = FrameLayout.LayoutParams(dpToPx(44f), dpToPx(44f), Gravity.START or Gravity.CENTER_VERTICAL)
            setOnClickListener { finish() }
            setOnTouchListener(pressScaleTouchListener)
        }

        topBarLayout.addView(topBarTitle)
        topBarLayout.addView(backBtn)
        rootFrameLayout.addView(topBarLayout)

        // ========== FULL MIUIX FLOATING NAVIGATION BAR ==========
        val bottomNavWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dpToPx(24f)
            }
        }

        navContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(28f).toFloat()
                // MiuiX Soft Glass translucent surface
                setColor(if (isDark) Color.parseColor("#E618181A") else Color.parseColor("#E6FFFFFF"))
                setStroke(dpToPx(1.2f), if (isDark) Color.parseColor("#33FFFFFF") else Color.parseColor("#1F000000"))
            }
            elevation = dpToPx(12f).toFloat()
            setPadding(dpToPx(6f), dpToPx(6f), dpToPx(6f), dpToPx(6f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Active Tab Capsule Surface
        val activeTabBg = GradientDrawable().apply {
            setColor(if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA"))
            cornerRadius = dpToPx(22f).toFloat()
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

        // --- Logs Tab ---
        logsTabItem = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(22f), 0, dpToPx(22f), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(44f))
        }

        logsIconView = ImageView(this).apply {
            setImageDrawable(createLogsNavIcon(primaryTextColor))
            layoutParams = LinearLayout.LayoutParams(dpToPx(18f), dpToPx(18f)).apply { marginEnd = dpToPx(8f) }
        }

        logsPillBtn = TextView(this).apply {
            text = "Logs"
            textSize = 14f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        logsTabItem.addView(logsIconView)
        logsTabItem.addView(logsPillBtn)

        // --- Info Tab ---
        infoTabItem = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(22f), 0, dpToPx(22f), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(44f))
        }

        infoIconView = ImageView(this).apply {
            setImageDrawable(createInfoNavIcon(secondaryTextColor))
            layoutParams = LinearLayout.LayoutParams(dpToPx(18f), dpToPx(18f)).apply { marginEnd = dpToPx(8f) }
        }

        infoPillBtn = TextView(this).apply {
            text = "Info"
            textSize = 14f
            setTextColor(secondaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        infoTabItem.addView(infoIconView)
        infoTabItem.addView(infoPillBtn)

        tabButtonsLayout.addView(logsTabItem)
        tabButtonsLayout.addView(infoTabItem)

        navContainer.addView(slidingPillView)
        navContainer.addView(tabButtonsLayout)

        navContainer.post {
            slidingPillView.layoutParams = slidingPillView.layoutParams.apply { width = logsTabItem.width }
            slidingPillView.translationX = logsTabItem.left.toFloat()
            slidingPillView.requestLayout()
        }

        bottomNavWrapper.addView(navContainer)
        rootFrameLayout.addView(bottomNavWrapper)

        // Setup touch gestures and physics spring transitions for MiuiX Floating Nav
        setupMiuiXNavTouchEvents()

        // ScrollListener
        scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            val alpha = (scrollY / dpToPx(40f).toFloat()).coerceIn(0f, 1f)
            topBarTitle.alpha = alpha
        })

        // Insets setup
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
            (bottomNavWrapper.layoutParams as FrameLayout.LayoutParams).bottomMargin = dpToPx(20f) + bottomInset
            insets
        }

        setContentView(rootFrameLayout)
    }

    // ========== MIUIX NAV TOUCH & INTERACTION PHYSICS ==========
    private fun setupMiuiXNavTouchEvents() {
        val switchTab: (Int) -> Unit = { tab ->
            if (currentTab != tab) {
                currentTab = tab
                if (tab == TAB_LOGS) {
                    logsLayout.visibility = View.VISIBLE
                    infoLayout.visibility = View.GONE
                } else {
                    logsLayout.visibility = View.GONE
                    infoLayout.visibility = View.VISIBLE
                }
                scrollView.scrollTo(0, 0)
            }
        }

        navContainer.setOnTouchListener { view, event ->
            val x0 = logsTabItem.left.toFloat() + (logsTabItem.width / 2f)
            val x1 = infoTabItem.left.toFloat() + (infoTabItem.width / 2f)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().cancel()
                    view.animate()
                        .scaleX(0.92f)
                        .scaleY(0.92f)
                        .alpha(0.9f)
                        .setDuration(120)
                        .setInterpolator(DecelerateInterpolator(1.5f))
                        .start()

                    val touchX = event.x - navContainer.paddingLeft
                    val progress = if (x1 > x0) ((touchX - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f
                    updateNavPillPosition(progress)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val touchX = event.x - navContainer.paddingLeft
                    val progress = if (x1 > x0) ((touchX - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f
                    updateNavPillPosition(progress)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val touchX = event.x - navContainer.paddingLeft
                    val midPoint = (x0 + x1) / 2f
                    val targetIsLogs = touchX < midPoint
                    val targetTab = if (targetIsLogs) TAB_LOGS else TAB_INFO
                    val targetProgress = if (targetIsLogs) 0f else 1f

                    animateNavPillTo(targetProgress) { switchTab(targetTab) }

                    view.animate().cancel()
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        .setDuration(350)
                        .setInterpolator(miuixSpringInterpolator)
                        .start()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateNavPillPosition(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        val x0 = logsTabItem.left.toFloat()
        val x1 = infoTabItem.left.toFloat()
        val w0 = logsTabItem.width.toFloat()
        val w1 = infoTabItem.width.toFloat()

        val currentX = x0 + (x1 - x0) * p
        val currentW = (w0 + (w1 - w0) * p).toInt()

        slidingPillView.translationX = currentX
        if (slidingPillView.layoutParams.width != currentW && currentW > 0) {
            slidingPillView.layoutParams = slidingPillView.layoutParams.apply { width = currentW }
            slidingPillView.requestLayout()
        }

        if (p < 0.5f) {
            logsPillBtn.setTextColor(primaryTextColor)
            logsIconView.setImageDrawable(createLogsNavIcon(primaryTextColor))
            infoPillBtn.setTextColor(secondaryTextColor)
            infoIconView.setImageDrawable(createInfoNavIcon(secondaryTextColor))
        } else {
            logsPillBtn.setTextColor(secondaryTextColor)
            logsIconView.setImageDrawable(createLogsNavIcon(secondaryTextColor))
            infoPillBtn.setTextColor(primaryTextColor)
            infoIconView.setImageDrawable(createInfoNavIcon(primaryTextColor))
        }
    }

    private fun animateNavPillTo(targetProgress: Float, onEnd: () -> Unit) {
        val currentX = slidingPillView.translationX
        val x0 = logsTabItem.left.toFloat()
        val x1 = infoTabItem.left.toFloat()
        val currentProgress = if (x1 > x0) ((currentX - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f

        ValueAnimator.ofFloat(currentProgress, targetProgress).apply {
            duration = 260L
            interpolator = miuixSpringInterpolator
            addUpdateListener { anim ->
                updateNavPillPosition(anim.animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            })
            start()
        }
    }

    // Filter pill setup
    private fun createFilterButton(label: String, initialColor: Int): TextView {
        return TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(initialColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36f), 1f)
            isClickable = false
        }
    }

    private fun setupFilterTouchEvents() {
        filterPillContainer.setOnTouchListener { view, event ->
            val x0 = filterAllBtn.left.toFloat()
            val x1 = filterAnrBtn.left.toFloat()
            val totalDistance = x1 - x0

            val computeProgress = { touchX: Float ->
                val relativeX = touchX - filterPillContainer.paddingLeft - (filterAllBtn.width / 2f)
                if (totalDistance > 0f) (relativeX / totalDistance).coerceIn(0f, 1f) else 0f
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    view.animate().cancel()
                    view.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.9f).setDuration(120).start()
                    updateFilterPillPosition(computeProgress(event.x))
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    updateFilterPillPosition(computeProgress(event.x))
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    val progress = computeProgress(event.x)
                    val targetProgress = when {
                        progress < 0.33f -> 0f
                        progress < 0.66f -> 0.5f
                        else -> 1f
                    }
                    val targetFilter = when (targetProgress) {
                        0f -> FILTER_ALL
                        0.5f -> FILTER_CRASH
                        else -> FILTER_ANR
                    }

                    animateFilterPillTo(targetProgress) { switchFilterTab(targetFilter) }
                    view.animate().cancel()
                    view.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(350).setInterpolator(miuixSpringInterpolator).start()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    view.animate().cancel()
                    view.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(200).start()
                    true
                }
                else -> false
            }
        }
    }

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
            interpolator = miuixSpringInterpolator
            addUpdateListener { anim -> updateFilterPillPosition(anim.animatedValue as Float) }
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

    private fun applyFilters() {
        val result = when (currentFilterTab) {
            FILTER_CRASH -> allLogs.filter { it.type == "Crash" }
            FILTER_ANR -> allLogs.filter { it.type == "ANR" }
            else -> allLogs
        }
        filteredLogs.clear()
        filteredLogs.addAll(result)
        logAdapter.updateLogs(filteredLogs)
    }

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
        val logcatCmd = if (hasRoot()) "su -c logcat -b crash -b main -b system -d -v time -t 5000"
        else "logcat -b crash -b main -b system -d -v time -t 5000"

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
                            if (nextLine.matches(Regex("\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}.*")) && isRealCrashLine(nextLine)) break
                            block.add(nextLine)
                            i++
                        }
                        val appName = extractPackageName(block)
                        if (appName != "System Process" && appName != "com.android.system") {
                            allLogs.add(LogEntry(timestamp, appName, type, block.joinToString("\n")))
                        }
                    } else { i++ }
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to read logcat", e) }
        }

        if (checkDropBoxPermission()) loadDropBoxLogs()
    }

    private fun hasRoot(): Boolean {
        val suPaths = arrayOf("/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su")
        for (path in suPaths) if (java.io.File(path).exists()) return true
        return false
    }

    private fun isRealCrashLine(line: String): Boolean {
        if (line.length < 10) return false
        return line.contains("FATAL EXCEPTION") || line.contains("ANR in") || line.contains("SIGABRT") || line.contains("SIGSEGV")
    }

    private fun extractTimestamp(line: String): String {
        val match = Regex("\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}").find(line)
        return match?.value?.replace("-", "/") ?: "Unknown"
    }

    private fun extractPackageName(block: List<String>): String {
        for (line in block) {
            val processMatch = Regex("(?:Process|Package|Process name):\\s*([\\w.:]+)").find(line)
            if (processMatch != null) return processMatch.groupValues[1].substringBefore(":")
        }
        return "Unknown Process"
    }

    private fun loadDropBoxLogs() {
        try {
            val dropBox = getSystemService(Context.DROPBOX_SERVICE) as? android.os.DropBoxManager ?: return
            val tags = setOf("system_app_native_crash", "system_app_crash", "data_app_crash", "system_app_anr", "data_app_anr")
            var entry = dropBox.getNextEntry(null, 0)
            while (entry != null) {
                if (tags.contains(entry.tag)) {
                    val text = entry.getText(65536) ?: ""
                    val type = if (entry.tag.contains("anr", ignoreCase = true)) "ANR" else "Crash"
                    val pkg = extractPackageName(text.lines())
                    if (pkg != "System Process" && !pkg.startsWith("com.android.system")) {
                        val timeStr = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()).format(Date(entry.timeMillis))
                        allLogs.add(0, LogEntry(timeStr, pkg, type, text))
                    }
                }
                val nextEntry = dropBox.getNextEntry(null, entry.timeMillis)
                entry.close()
                entry = nextEntry
            }
        } catch (e: Exception) { Log.e(TAG, "Failed to read DropBox", e) }
    }

    private fun checkAllPermissions() = checkReadLogsPermission() && checkDropBoxPermission() && checkUsageStatsPermission()
    private fun checkReadLogsPermission() = checkCallingOrSelfPermission("android.permission.READ_LOGS") == PackageManager.PERMISSION_GRANTED
    private fun checkDropBoxPermission() = checkCallingOrSelfPermission("android.permission.READ_DROPBOX_DATA") == PackageManager.PERMISSION_GRANTED
    private fun checkUsageStatsPermission(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }

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

        cardLayout.addView(TextView(this).apply {
            text = "Permissions Required"
            textSize = 20f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(8f))
        })

        cardLayout.addView(TextView(this).apply {
            text = "Please grant READ_LOGS, READ_DROPBOX_DATA, and USAGE_STATS via ADB or Root."
            textSize = 13f
            setTextColor(secondaryTextColor)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(16f))
        })

        val closeBtn = createAnimatedButton("Close", Color.WHITE, accentColor, buttonHeightPx) { dialog.dismiss(); finish() }
        cardLayout.addView(closeBtn)

        dialog.setContentView(cardLayout)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun initColors() {
        isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
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
            cornerRadius = dpToPx(24f).toFloat()
            setStroke(dpToPx(1f), cardBorderColor)
        }
    }

    private fun createStatCard(label: String, value: String, color: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(inputBgColor)
                cornerRadius = dpToPx(16f).toFloat()
            }
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(4f); marginEnd = dpToPx(4f)
            }
            addView(TextView(context).apply {
                text = value; textSize = 22f; setTextColor(color); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = label; textSize = 12f; setTextColor(secondaryTextColor); gravity = Gravity.CENTER
            })
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
            isClickable = true
            setOnClickListener { onClick() }
            setOnTouchListener(pressScaleTouchListener)
        }
    }

    private val pressScaleTouchListener = View.OnTouchListener { v, event ->
    when (event.action) {
        MotionEvent.ACTION_DOWN -> {
            v.animate().cancel()
            v.animate().scaleX(0.94f).scaleY(0.94f).alpha(0.85f).setDuration(120).start()
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            v.animate().cancel()
            v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(350).setInterpolator(miuixSpringInterpolator).start()
        }
    }
    false
}


    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dpToPx(36f)
    }

    private fun dpToPx(dp: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    private fun dpToPx(dp: Int): Int = dpToPx(dp.toFloat())

    private fun onItemClick(entry: LogEntry) {
        startActivity(Intent(this, CrashDetailActivity::class.java).apply {
            putExtra("type", entry.type)
            putExtra("appName", entry.appName)
            putExtra("timestamp", entry.timestamp)
            putExtra("details", entry.details)
        })
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

    private fun createLogsNavIcon(color: Int): Drawable {
        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = dpToPx(2f).toFloat()
                strokeCap = Paint.Cap.ROUND
            }
            override fun draw(canvas: Canvas) {
                val w = bounds.width().toFloat()
                val h = bounds.height().toFloat()
                if (w <= 0 || h <= 0) return
                val pad = dpToPx(3f).toFloat()
                val rect = RectF(pad, pad, w - pad, h - pad)
                canvas.drawRoundRect(rect, dpToPx(3f).toFloat(), dpToPx(3f).toFloat(), paint)

                val lineLeft = pad + dpToPx(3.5f)
                val lineRight = w - pad - dpToPx(3.5f)
                val y1 = pad + (h - 2 * pad) * 0.35f
                val y2 = pad + (h - 2 * pad) * 0.65f
                canvas.drawLine(lineLeft, y1, lineRight, y1, paint)
                canvas.drawLine(lineLeft, y2, lineRight - dpToPx(4f), y2, paint)
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
            @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }

    private fun createInfoNavIcon(color: Int): Drawable {
        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = dpToPx(2f).toFloat()
                strokeCap = Paint.Cap.ROUND
            }
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            }
            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val r = (minOf(bounds.width(), bounds.height()) / 2f) - dpToPx(2.5f)
                if (r <= 0) return
                canvas.drawCircle(cx, cy, r, paint)
                canvas.drawCircle(cx, cy - r * 0.4f, dpToPx(1.5f).toFloat(), fillPaint)
                canvas.drawLine(cx, cy - r * 0.05f, cx, cy + r * 0.5f, paint)
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha; fillPaint.alpha = alpha }
            override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf; fillPaint.colorFilter = cf }
            @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }
}

// Data Class
data class LogEntry(val timestamp: String, val appName: String, val type: String, val details: String = "")

// ========== PROGRAMMATIC RECYCLERVIEW ADAPTER (NO XML DEPENDENCY) ==========
class LogAdapter(
    private var logs: List<LogEntry>,
    private val context: Context,
    private val packageManager: PackageManager,
    private val cardBg: Int,
    private val cardBorder: Int,
    private val primaryTextColor: Int,
    private val secondaryTextColor: Int,
    private val accentColor: Int,
    private val redBtnColor: Int,
    private val onItemClick: (LogEntry) -> Unit,
    private val dpToPx: (Float) -> Int
) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val defaultIcon = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
    private val iconCache = LruCache<String, Bitmap>(50)

    fun updateLogs(newLogs: List<LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16f), dpToPx(14f), dpToPx(16f), dpToPx(14f))
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(10f) }
        }

        val appIcon = ImageView(context).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(dpToPx(40f), dpToPx(40f)).apply { marginEnd = dpToPx(12f) }
        }

        val textGroup = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val appNameText = TextView(context).apply {
            id = View.generateViewId()
            textSize = 15f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
        }

        val timestampText = TextView(context).apply {
            id = View.generateViewId()
            textSize = 12f
            setTextColor(secondaryTextColor)
        }

        textGroup.addView(appNameText)
        textGroup.addView(timestampText)

        val typeBadge = TextView(context).apply {
            id = View.generateViewId()
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(10f), dpToPx(4f), dpToPx(10f), dpToPx(4f))
        }

        cardLayout.addView(appIcon)
        cardLayout.addView(textGroup)
        cardLayout.addView(typeBadge)

        return LogViewHolder(cardLayout, appIcon.id, appNameText.id, timestampText.id, typeBadge.id, onItemClick)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position], cardBg, cardBorder, redBtnColor, accentColor, ::getScaledIcon)
    }

    override fun getItemCount(): Int = logs.size

    private fun getScaledIcon(packageName: String): Drawable? {
        val cached = iconCache.get(packageName)
        if (cached != null) return BitmapDrawable(context.resources, cached)

        val originalDrawable = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationIcon(appInfo)
        } catch (e: Exception) { defaultIcon } ?: defaultIcon

        val bitmap = drawableToBitmap(originalDrawable) ?: return originalDrawable
        val maxSize = dpToPx(40f)
        var scaled = bitmap
        if (bitmap.width > maxSize || bitmap.height > maxSize) {
            val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        }
        iconCache.put(packageName, scaled)
        return BitmapDrawable(context.resources, scaled)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable) return drawable.bitmap
        if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) return null
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    class LogViewHolder(
        itemView: View,
        iconId: Int,
        appNameId: Int,
        timeId: Int,
        badgeId: Int,
        private val onItemClick: (LogEntry) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(iconId)
        private val appNameText: TextView = itemView.findViewById(appNameId)
        private val timestampText: TextView = itemView.findViewById(timeId)
        private val typeBadge: TextView = itemView.findViewById(badgeId)

        fun bind(log: LogEntry, bg: Int, border: Int, redColor: Int, accentColor: Int, iconLoader: (String) -> Drawable?) {
            timestampText.text = log.timestamp
            val cleanPackage = log.appName.substringBefore(":")
            appNameText.text = cleanPackage

            (itemView as LinearLayout).background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, itemView.resources.displayMetrics)
                setStroke(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, itemView.resources.displayMetrics).toInt(), border)
            }

            typeBadge.text = log.type
            val badgeBgColor = if (log.type == "Crash") redColor else accentColor
            typeBadge.background = GradientDrawable().apply {
                cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100f, itemView.resources.displayMetrics)
                setColor(badgeBgColor)
            }
            typeBadge.setTextColor(Color.WHITE)

            appIcon.setImageDrawable(iconLoader(cleanPackage))
            itemView.setOnClickListener { onItemClick(log) }
        }
    }
}
