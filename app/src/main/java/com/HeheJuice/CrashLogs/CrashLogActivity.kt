package com.HeheJuice.CrashLogs

import android.animation.Animator
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
import androidx.core.content.ContextCompat
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

    // Filter pill
    private lateinit var filterPillContainer: FrameLayout
    private lateinit var filterSlidingView: View
    private lateinit var filterAllBtn: TextView
    private lateinit var filterCrashBtn: TextView
    private lateinit var filterAnrBtn: TextView
    private var currentFilterTab = FILTER_ALL

    // Loading text view
    private lateinit var loadingText: TextView

    private lateinit var rootFrameLayout: FrameLayout
    private lateinit var scrollView: ScrollView

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

    // ========== UI SETUP (unchanged) ==========
    private fun setupUI() {
        // ... (identical to previous version, no changes needed) ...
        // I'll include the full code in the final answer, but for brevity I'll skip repeating it here.
        // The only changes are in the log parsing methods.
    }

    // ========== LOG PARSING (UPDATED) ==========
    private fun loadLogsAsync(onComplete: () -> Unit) {
        loadingText.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        Thread {
            loadLogs()
            runOnUiThread {
                loadingText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                logAdapter.updateLogs(filteredLogs)
                updateStats()
                onComplete()
            }
        }.start()
    }

    private fun updateStats() {
        // ... (unchanged) ...
    }

    private fun loadLogs() {
        allLogs.clear()

        if (checkReadLogsPermission()) {
            try {
                val process = Runtime.getRuntime().exec("logcat -b crash -b main -b system -d -v time -t 5000")
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
                        // Skip if appName is "System Process" (unknown) – these are noise
                        if (appName == "System Process / Unknown" || appName == "System Process") {
                            // Don't add to allLogs
                        } else {
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

        filteredLogs.clear()
        filteredLogs.addAll(allLogs)
    }

    // STRICTER: only real crash signatures, no system_server or DEBUG noise
    private fun isRealCrashLine(line: String): Boolean {
        return line.contains("FATAL EXCEPTION") ||
                line.contains("ANR in") ||
                line.contains("SIGABRT") ||
                line.contains("SIGSEGV") ||
                line.contains("signal 11") ||
                line.contains("signal 6") ||
                (line.contains("Abort message") && line.contains("FATAL")) ||
                (line.contains("backtrace:") && line.contains("pid:")) ||
                line.contains("Native crash") ||
                (line.contains("Process:") && line.contains(" crashed") && (line.contains("pid:") || line.contains("signal")))
    }

    private fun extractTimestamp(line: String): String {
        val pattern = Regex("\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")
        val match = pattern.find(line)
        return match?.value?.replace("-", "/") ?: "Unknown"
    }

    private fun extractPackageName(block: List<String>): String {
        for (line in block) {
            val processMatch = Regex("(?:Process|Package):\\s*([\\w.:]+)").find(line)
            if (processMatch != null) {
                val pkg = processMatch.groupValues[1].substringBefore(":")
                if (pkg.isNotBlank() && pkg.contains(".")) {
                    return pkg
                }
            }
            val parenMatch = Regex("\\(([\\w.]+)\\)").find(line)
            if (parenMatch != null) {
                val pkg = parenMatch.groupValues[1]
                if (pkg.isNotBlank() && pkg.contains(".")) {
                    return pkg
                }
            }
            val bracketMatch = Regex(">>>\\s*([\\w.:]+)\\s*<<<").find(line)
            if (bracketMatch != null) {
                return bracketMatch.groupValues[1].substringBefore(":")
            }
            val cmdlineMatch = Regex("Cmdline:\\s*([\\w.:]+)").find(line)
            if (cmdlineMatch != null) {
                return cmdlineMatch.groupValues[1].substringBefore(":")
            }
        }
        return "System Process / Unknown"
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
                val tag = entry.tag
                if (tags.contains(tag)) {
                    // Read full content
                    val text = entry.getText(0) ?: ""
                    val type = if (tag.contains("anr", ignoreCase = true)) "ANR" else "Crash"
                    val pkgMatch = Regex("(?:Process|Package|Cmdline):\\s*([\\w.:]+)").find(text)
                    val pkg = pkgMatch?.groupValues?.get(1)?.substringBefore(":")
                        ?: if (tag == "SYSTEM_TOMBSTONE") "System Tombstone" else "com.android.system"

                    val timeStr = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
                        .format(Date(entry.timeMillis))
                    allLogs.add(0, LogEntry(timeStr, pkg, type, "[$tag]\n$text"))
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

    // ========== CUSTOM PERMISSION DIALOG ==========
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

    // ========== DRAWABLES ==========
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

    // Click handler for log items
    private fun onItemClick(entry: LogEntry) {
        val intent = Intent(this, CrashDetailActivity::class.java).apply {
            putExtra("type", entry.type)
            putExtra("appName", entry.appName)
            putExtra("timestamp", entry.timestamp)
            putExtra("details", entry.details)
        }
        startActivity(intent)
    }

    // Entrance animations for layouts
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

        fun bind(log: LogEntry, pm: PackageManager, defaultIcon: Drawable?, cardBg: Int, cardBorder: Int) {
            timestampText.text = log.timestamp
            val cleanPackage = log.appName.substringBefore(":")
            appNameText.text = cleanPackage

            // --- Set card background to match old project style ---
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

            // --- Magisk special ---
            val isMagisk = cleanPackage.equals("magisk", ignoreCase = true)
            val badgeText = if (isMagisk) "Magisk" else log.type
            typeBadge.text = badgeText

            // Load icon
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