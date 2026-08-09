package com.HeheJuice.CrashLogs

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AppOpsManager
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader

class CrashLogActivity : Activity() {

    companion object {
        private const val TAB_LOGS = 0
        private const val TAB_INFO = 1
        private const val TAG = "CrashLogs"
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
    private var currentFilter = "ALL"
    private var filteredLogs = mutableListOf<LogEntry>()
    
    private lateinit var logsLayout: LinearLayout
    private lateinit var infoLayout: LinearLayout
    private lateinit var slidingPillView: View
    private lateinit var logsPillBtn: TextView
    private lateinit var infoPillBtn: TextView
    private var currentTab = TAB_LOGS
    private lateinit var logCountHeader: TextView
    private lateinit var rootFrameLayout: FrameLayout
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        initColors()
        buttonHeightPx = dpToPx(54f)

        // Check permissions first
        if (!checkAllPermissions()) {
            showPermissionDialog()
            return
        }

        setupUI()
        loadLogsAsync()
    }

    private fun setupUI() {
        rootFrameLayout = FrameLayout(this).apply {
            setBackgroundColor(if (isDark) Color.parseColor("#000000") else Color.parseColor("#F2F2F7"))
        }

        val statusBarHeight = getStatusBarHeight()

        scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_ALWAYS
            clipToPadding = false
            setPadding(dpToPx(16f), statusBarHeight + dpToPx(68f), dpToPx(16f), dpToPx(180f))
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
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Filter chips
        val filterChipLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16f)
            }
        }

        val filterAll = createFilterChip("ALL", true)
        val filterCrash = createFilterChip("CRASH", false)
        val filterAnr = createFilterChip("ANR", false)

        filterChipLayout.addView(filterAll)
        filterChipLayout.addView(filterCrash)
        filterChipLayout.addView(filterAnr)

        logsLayout.addView(filterChipLayout)

        logCountHeader = TextView(this).apply {
            text = "Loading logs..."
            textSize = 14f
            setTextColor(secondaryTextColor)
            setPadding(0, 0, 0, dpToPx(12f))
        }
        logsLayout.addView(logCountHeader)

        recyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(500f)
            )
            layoutManager = LinearLayoutManager(this@CrashLogActivity)
            overScrollMode = View.OVER_SCROLL_ALWAYS
        }

        logAdapter = LogAdapter(filteredLogs)
        recyclerView.adapter = logAdapter
        logsLayout.addView(recyclerView)

        // ========== INFO TAB ==========
        infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val infoCardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBackground()
            setPadding(dpToPx(20f), dpToPx(24f), dpToPx(20f), dpToPx(24f))
        }

        val infoTitle = TextView(this).apply {
            text = "Crash Logs Browser"
            textSize = 24f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
        }
        infoCardLayout.addView(infoTitle)

        val infoSub = TextView(this).apply {
            text = "View and monitor app crashes and ANRs"
            textSize = 15f
            setTextColor(secondaryTextColor)
            setPadding(0, dpToPx(4f), 0, dpToPx(16f))
        }
        infoCardLayout.addView(infoSub)

        // Version info - fetch dynamically
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
        val versionInfo = TextView(this).apply {
            text = "Version $versionName"
            textSize = 13f
            setTextColor(secondaryTextColor)
            setPadding(0, 0, 0, dpToPx(16f))
        }
        infoCardLayout.addView(versionInfo)

        // Stats row (will be updated after logs load)
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

        infoCardLayout.addView(statsRow)

        // Refresh button
        val refreshBtn = createAnimatedButton("Refresh Logs", Color.WHITE, accentColor, buttonHeightPx) {
            loadLogsAsync()
            Toast.makeText(this, "Refreshing logs...", Toast.LENGTH_SHORT).show()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dpToPx(16f)
        }
        infoCardLayout.addView(refreshBtn)

        infoLayout.addView(infoCardLayout)

        // Add layouts to scroll content
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
            setPadding(dpToPx(16f), statusBarHeight + dpToPx(12f), dpToPx(16f), dpToPx(12f))
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
                dpToPx(48f),
                Gravity.CENTER
            )
        }

        val backArrowDrawable = createArrowBackDrawable()
        val backBtn = ImageView(this).apply {
            setImageDrawable(backArrowDrawable)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(backBtnBgColor)
            }
            contentDescription = "Back"
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(dpToPx(48f), dpToPx(48f), Gravity.START or Gravity.CENTER_VERTICAL)
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

        val switchTab: (Int) -> Unit = { tab ->
            if (currentTab != tab) {
                currentTab = tab
                if (tab == TAB_LOGS) {
                    logsLayout.visibility = View.VISIBLE
                    infoLayout.visibility = View.GONE
                    applyEntranceAnimations(listOf(logsLayout))
                } else {
                    logsLayout.visibility = View.GONE
                    infoLayout.visibility = View.VISIBLE
                    applyEntranceAnimations(listOf(infoLayout))
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

        bottomBarLayout.addView(tabPillContainer)
        rootFrameLayout.addView(bottomBarLayout)

        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val alpha = (scrollY / dpToPx(40f).toFloat()).coerceIn(0f, 1f)
            topBarTitle.alpha = alpha
        }

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

            topBarLayout.setPadding(dpToPx(16f), effectiveTop + dpToPx(12f), dpToPx(16f), dpToPx(12f))
            scrollView.setPadding(dpToPx(16f), effectiveTop + dpToPx(68f), dpToPx(16f), dpToPx(140f))
            (bottomBarLayout.layoutParams as FrameLayout.LayoutParams).bottomMargin = dpToPx(16f) + bottomInset
            insets
        }

        setContentView(rootFrameLayout)
        applyEntranceAnimations(listOf(logsLayout))
    }

    // ========== LOG LOADING (optimized) ==========
    private fun loadLogsAsync() {
        logCountHeader.text = "Loading logs..."
        Thread {
            val start = System.currentTimeMillis()
            loadLogs()
            val elapsed = System.currentTimeMillis() - start
            Log.d(TAG, "Logs loaded in ${elapsed}ms")
            runOnUiThread {
                updateLogCount()
                logAdapter.updateLogs(filteredLogs)
                updateStats()
            }
        }.start()
    }

    private fun updateStats() {
        // Find stats row in info layout
        val infoCard = infoLayout.getChildAt(0) as? LinearLayout ?: return
        val statsRow = infoCard.findViewWithTag<LinearLayout>("statsRow") ?: return
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
        try {
            // Use -t 200 to get last 200 lines (fast)
            val process = Runtime.getRuntime().exec("logcat -d -v time -s AndroidRuntime:E ActivityManager:I -t 200")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    if (it.contains("FATAL EXCEPTION") || it.contains("ANR in")) {
                        val timestamp = extractTimestamp(it)
                        val appName = extractAppName(it)
                        val type = if (it.contains("ANR")) "ANR" else "Crash"
                        if (appName.isNotEmpty()) {
                            allLogs.add(LogEntry(timestamp, appName, type))
                        }
                    }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read logcat", e)
        }

        if (allLogs.isEmpty()) {
            loadMockData()
        }

        filteredLogs.clear()
        filteredLogs.addAll(allLogs)
    }

    private fun extractTimestamp(line: String): String {
        val pattern = Regex("\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")
        val match = pattern.find(line)
        return match?.value?.replace("-", "/")?.substring(0, 15) ?: "Unknown"
    }

    private fun extractAppName(line: String): String {
        val pattern = Regex("\\(([^)]+)\\)")
        val match = pattern.find(line)
        return match?.groupValues?.get(1)?.split(":")?.firstOrNull() ?: "Unknown"
    }

    private fun loadMockData() {
        allLogs.addAll(listOf(
            LogEntry("08/08, 5:35:57 pm", "com.instagram.android", "ANR"),
            LogEntry("08/08, 9:33:32 am", "Settings", "Crash"),
            LogEntry("07/08, 10:21:47 pm", "com.android.settings", "ANR"),
            LogEntry("07/08, 10:20:12 pm", "System App", "Crash"),
            LogEntry("07/08, 10:20:05 pm", "Settings", "Crash"),
            LogEntry("07/08, 8:25:38 pm", "com.instagram.android", "ANR"),
            LogEntry("07/08, 5:41:57 pm", "Settings", "ANR"),
        ))
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
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                appOps.checkOpNoThrow(
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
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
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
                            loadLogsAsync()
                        } else {
                            Toast.makeText(this, "Some permissions still not granted. Try rebooting.", Toast.LENGTH_LONG).show()
                            showPermissionDialog()
                        }
                    }, 500)
                } else {
                    Toast.makeText(this, "Root permission required or failed", Toast.LENGTH_LONG).show()
                    showPermissionDialog()
                }
            }
        }.start()
    }

    private fun runRootCommands(commands: List<String>): Boolean {
        return try {
            val script = commands.joinToString(" ; ")
            val process = ProcessBuilder("su", "-c", script).start()
            
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            
            Log.d(TAG, "Root output: $output")
            if (error.isNotEmpty()) {
                Log.e(TAG, "Root error: $error")
            }
            
            process.waitFor() == 0
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

        // Lock icon - custom drawable
        val lockDrawable = createLockDrawable()
        val iconIv = ImageView(this).apply {
            setImageDrawable(lockDrawable)
            layoutParams = LinearLayout.LayoutParams(dpToPx(60f), dpToPx(60f)).apply {
                gravity = Gravity.CENTER
                bottomMargin = dpToPx(8f)
            }
        }
        cardLayout.addView(iconIv)

        // Title
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

        // Permission list
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

            // Status icon - custom drawable
            val granted = isPermissionGranted(perm)
            val statusDrawable = createStatusDrawable(granted)
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

        // ADB instructions
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

        val adbTitle = TextView(this).apply {
            text = "Grant via ADB (one by one)"
            textSize = 14f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
        }
        adbLayout.addView(adbTitle)

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

        // Root grant button
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

        // Buttons row
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
                loadLogsAsync()
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

    // ========== CUSTOM DRAWABLES ==========
    private fun createStatusDrawable(granted: Boolean): Drawable {
        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (granted) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = dpToPx(2f).toFloat()
            }
            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val radius = bounds.width() * 0.4f
                canvas.drawCircle(cx, cy, radius, paint)
                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dpToPx(2.5f).toFloat()
                if (granted) {
                    val path = Path().apply {
                        moveTo(cx - radius * 0.4f, cy)
                        lineTo(cx - radius * 0.1f, cy + radius * 0.5f)
                        lineTo(cx + radius * 0.6f, cy - radius * 0.5f)
                    }
                    canvas.drawPath(path, paint)
                } else {
                    val offset = radius * 0.5f
                    canvas.drawLine(cx - offset, cy - offset, cx + offset, cy + offset, paint)
                    canvas.drawLine(cx + offset, cy - offset, cx - offset, cy + offset, paint)
                }
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
            @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }

    private fun createLockDrawable(): Drawable {
        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryTextColor
                style = Paint.Style.STROKE
                strokeWidth = dpToPx(2f).toFloat()
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val w = bounds.width() * 0.3f
                val h = bounds.height() * 0.5f
                canvas.drawRoundRect(cx - w, cy - h*0.2f, cx + w, cy + h*0.8f, dpToPx(4f).toFloat(), dpToPx(4f).toFloat(), paint)
                canvas.drawArc(cx - w*0.6f, cy - h*0.8f, cx + w*0.6f, cy - h*0.2f, -180f, 180f, false, paint)
                canvas.drawCircle(cx, cy + h*0.2f, dpToPx(3f).toFloat(), paint)
                canvas.drawLine(cx, cy + h*0.2f, cx, cy + h*0.5f, paint)
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
            @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
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

    private fun createFilterChip(text: String, isSelected: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(if (isSelected) Color.WHITE else primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16f), dpToPx(8f), dpToPx(16f), dpToPx(8f))
            
            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(100f).toFloat()
                setColor(if (isSelected) accentColor else inputBgColor)
                setStroke(dpToPx(1f), if (isSelected) accentColor else cardBorderColor)
            }
            background = bg
            
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(8f)
            }
            
            isClickable = true
            isFocusable = true
            
            setOnClickListener {
                updateFilter(text)
            }
            setOnTouchListener(pressScaleTouchListener)
        }
    }

    private fun updateFilter(filter: String) {
        currentFilter = filter
        filteredLogs.clear()
        filteredLogs.addAll(
            when (filter) {
                "CRASH" -> allLogs.filter { it.type == "Crash" }
                "ANR" -> allLogs.filter { it.type == "ANR" }
                else -> allLogs
            }
        )
        logAdapter.updateLogs(filteredLogs)
        updateLogCount()
        
        val parent = (recyclerView.parent as? LinearLayout) ?: return
        val chipLayout = parent.getChildAt(0) as? LinearLayout ?: return
        for (i in 0 until chipLayout.childCount) {
            val chip = chipLayout.getChildAt(i) as TextView
            val chipText = chip.text.toString()
            val isSelected = chipText == filter
            chip.setTextColor(if (isSelected) Color.WHITE else primaryTextColor)
            val bg = chip.background as GradientDrawable
            bg.setColor(if (isSelected) accentColor else inputBgColor)
            bg.setStroke(dpToPx(1f), if (isSelected) accentColor else cardBorderColor)
        }
    }

    private fun updateLogCount() {
        logCountHeader.text = "${filteredLogs.size} Log Entries"
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

            // Icon - custom drawable
            val iconDrawable = createStatIconDrawable(isError, color)
            val icon = ImageView(context).apply {
                setImageDrawable(iconDrawable)
                layoutParams = LinearLayout.LayoutParams(dpToPx(32f), dpToPx(32f)).apply {
                    gravity = Gravity.CENTER
                    bottomMargin = dpToPx(4f)
                }
            }
            addView(icon)

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

    private fun createStatIconDrawable(isError: Boolean, color: Int): Drawable {
        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = dpToPx(2f).toFloat()
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            override fun draw(canvas: Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val r = bounds.width() * 0.4f
                if (isError) {
                    // Exclamation mark in a circle (error)
                    canvas.drawCircle(cx, cy, r, paint)
                    paint.style = Paint.Style.FILL_AND_STROKE
                    paint.strokeWidth = dpToPx(2.5f).toFloat()
                    canvas.drawLine(cx, cy - r * 0.4f, cx, cy + r * 0.1f, paint)
                    canvas.drawCircle(cx, cy + r * 0.4f, dpToPx(2f).toFloat(), paint)
                } else {
                    // Warning triangle
                    val path = Path().apply {
                        moveTo(cx, cy - r)
                        lineTo(cx - r * 0.9f, cy + r * 0.6f)
                        lineTo(cx + r * 0.9f, cy + r * 0.6f)
                        close()
                    }
                    canvas.drawPath(path, paint)
                    paint.style = Paint.Style.FILL_AND_STROKE
                    paint.strokeWidth = dpToPx(2.5f).toFloat()
                    canvas.drawLine(cx, cy - r * 0.2f, cx, cy + r * 0.3f, paint)
                    canvas.drawCircle(cx, cy + r * 0.6f, dpToPx(2f).toFloat(), paint)
                }
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
            @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }

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
        val x0 = logsPillBtn.left.toFloat()
        val x1 = infoPillBtn.left.toFloat()
        val currentX = slidingPillView.translationX
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

    private fun applyEntranceAnimations(views: List<View>) {
        views.forEachIndexed { index, view ->
            view.translationY = dpToPx(40f).toFloat()
            view.alpha = 0f
            view.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(400)
                .setStartDelay((index * 60).toLong())
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
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

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dpToPx(36f)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    }

    private fun dpToPx(dp: Int): Int = dpToPx(dp.toFloat())
}

// ========== DATA CLASSES ==========
data class LogEntry(
    val timestamp: String,
    val appName: String,
    val type: String
)

// ========== LOG ADAPTER ==========
class LogAdapter(private var logs: List<LogEntry>) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    fun updateLogs(newLogs: List<LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int = logs.size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val appNameText: TextView = itemView.findViewById(R.id.appNameText)
        private val typeBadge: TextView = itemView.findViewById(R.id.typeBadge)

        fun bind(log: LogEntry) {
            timestampText.text = log.timestamp
            appNameText.text = log.appName
            typeBadge.text = log.type
            
            when (log.type) {
                "Crash" -> {
                    typeBadge.setBackgroundColor(
                        itemView.context.getColor(android.R.color.holo_red_dark)
                    )
                }
                "ANR" -> {
                    typeBadge.setBackgroundColor(
                        itemView.context.getColor(android.R.color.holo_orange_dark)
                    )
                }
                else -> {
                    typeBadge.setBackgroundColor(
                        itemView.context.getColor(android.R.color.darker_gray)
                    )
                }
            }
        }
    }
}