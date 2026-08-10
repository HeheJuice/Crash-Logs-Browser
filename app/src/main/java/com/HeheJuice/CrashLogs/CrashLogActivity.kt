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
        // ... (identical to your existing setupUI, no changes needed) ...
        // For brevity, I'm keeping your existing setupUI – it's correct.
        // The filter pill touch listener already uses 0.33/0.66 thresholds.
        // The updateFilterPillPosition method is fixed below.
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

    // ========== FILTER PILL FUNCTIONS (FIXED THRESHOLDS) ==========
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

        // Fixed thirds distribution (0.0-0.33 = ALL, 0.33-0.66 = CRASH, 0.66-1.0 = ANR)
        filterAllBtn.setTextColor(if (p < 0.33f) primaryTextColor else secondaryTextColor)
        filterCrashBtn.setTextColor(if (p in 0.33f..0.66f) primaryTextColor else secondaryTextColor)
        filterAnrBtn.setTextColor(if (p > 0.66f) primaryTextColor else secondaryTextColor)
    }

    private fun animateFilterPillTo(targetProgress: Float, onEnd: () -> Unit) {
        // ... unchanged ...
    }

    private fun switchFilterTab(filter: Int) {
        // ... unchanged ...
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
    // ... (unchanged) ...

    // ========== LOG PARSING (with fixes) ==========
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
        // ... unchanged ...
    }

    private fun loadLogs() {
        allLogs.clear()

        // Use root if available to read logcat on Android 10+
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

    // === FIXED hasRoot() – checks common su paths ===
    private fun hasRoot(): Boolean {
        val suPaths = arrayOf(
            "/system/app/Superuser.apk",
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

    // === Optimized isRealCrashLine with early short‑circuit ===
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
        // ... unchanged ...
    }

    // === loadDropBoxLogs with per‑entry try‑catch ===
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
    // ... (unchanged) ...

    // ========== ROOT PERMISSION GRANT ==========
    // ... (unchanged) ...

    // ========== CUSTOM PERMISSION DIALOG ==========
    // ... (unchanged) ...

    // ========== DRAWABLES ==========
    // ... (unchanged) ...

    // ========== UI HELPERS ==========
    // ... (unchanged) ...
}

// ========== DATA CLASSES ==========
data class LogEntry(
    val timestamp: String,
    val appName: String,
    val type: String,
    val details: String = ""
)

// ========== LOG ADAPTER (with ripple via XML foreground) ==========
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

        fun bind(log: LogEntry, pm: PackageManager, defaultIcon: Drawable?, cardBg: Int, cardBorder: Int) {
            timestampText.text = log.timestamp
            val cleanPackage = log.appName.substringBefore(":")
            appNameText.text = cleanPackage

            // Card background
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

            // Load app icon
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

            // Badge color
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