package com.HeheJuice.CrashLogs

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import androidx.appcompat.app.AlertDialog
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AppOpsManager
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR
import com.google.android.material.materialswitch.MaterialSwitch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
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

        private const val EXPECTED_SIGNATURE_HASH = "cd04972b4d1edd5a2a6e11e0a4fa6119cc3da1b49a59c922b165fbe844a7c36b"
        private const val OFFICIAL_SOURCE_URL = "https://github.com/HeheJuice/Crash-Logs-Browser/releases"
    }

    // 颜色变量（与 DeveloperOptionsActivity 一致）
    private var primaryTextColor: Int = 0
    private var secondaryTextColor: Int = 0
    private var accentColor: Int = 0
    private var onPrimaryColor: Int = 0
    private var inputBgColor: Int = 0
    private var cardBgColor: Int = 0
    private var cardBorderColor: Int = 0
    private var secondaryBtnColor: Int = 0
    private var activePillBgColor: Int = 0
    private var redBtnColor: Int = 0
    private var backBtnBgColor: Int = 0
    private var buttonHeightPx: Int = 0
    private var isDark: Boolean = false
    private var googleSansFlexTypeface: Typeface? = null

    // 开关颜色（与 DeveloperOptions 一致）
    private var trackOnColor: Int = 0
    private var trackOffColor: Int = 0
    private var thumbOnColor: Int = 0
    private var thumbOffColor: Int = 0

    private lateinit var recyclerView: RecyclerView
    private lateinit var logAdapter: LogAdapter
    private val allLogs = mutableListOf<LogEntry>()
    private var filteredLogs = mutableListOf<LogEntry>()

    private lateinit var logsLayout: LinearLayout
    private lateinit var infoLayout: LinearLayout

    private lateinit var emptyStateText: TextView

    private lateinit var slidingPillView: View
    private lateinit var logsPillBtn: TextView
    private lateinit var infoPillBtn: TextView
    private var currentTab = TAB_LOGS

    private lateinit var filterPillContainer: FrameLayout
    private lateinit var filterSlidingView: View
    private lateinit var filterAllBtn: TextView
    private lateinit var filterCrashBtn: TextView
    private lateinit var filterAnrBtn: TextView
    private var currentFilterTab = FILTER_ALL

    private lateinit var loadingText: TextView

    private var cooldownTimer: CountDownTimer? = null
    private var isCooldown = false
    private var remainingSeconds = 0

    private lateinit var refreshButton: TextView
    private lateinit var topBarRefreshContainer: FrameLayout
    private lateinit var topBarRefreshIcon: ImageView
    private lateinit var topBarRefreshCountdown: TextView

    private lateinit var rootFrameLayout: FrameLayout
    private lateinit var scrollView: NestedScrollView

    // 自动刷新相关
    private var autoRefreshSwitch: MaterialSwitch? = null
    private val autoRefreshHandler = Handler(Looper.getMainLooper())
    private var autoRefreshRunnable: Runnable? = null
    private var isConfirmingAutoRefresh = false

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        if (!checkSignature()) {
            showTamperedDialog()
            return
        }

        initColors()

        googleSansFlexTypeface = try {
            Typeface.createFromAsset(assets, "GoogleSansFlex.ttf")
        } catch (_: Exception) { null }

        setStatusBarColors()
        buttonHeightPx = dpToPx(54f)

        if (!checkAllPermissions()) {
            showPermissionDialog()
            return
        }

        setupUI()
        loadLogsAsync {}
    }

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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newDark = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (newDark != isDark) {
            showThemeRestartDialog()
        }
    }

    private fun showThemeRestartDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)

        val dpToPx = { dp: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt() }
        val cardBg = if (isDark) Color.parseColor("#1C1C1E") else Color.parseColor("#FFFFFF")
        val cardBorder = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")
        val primaryText = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#000000")
        val secondaryText = if (isDark) Color.parseColor("#8E8E93") else Color.parseColor("#6C6C70")
        val accent = if (isDark) Color.parseColor("#3E82F7") else Color.parseColor("#0066FF")

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
            setPadding(0, 0, 0, dpToPx(8f))
        }
        cardLayout.addView(titleTv)

        val messageTv = TextView(this).apply {
            text = "System dark/light mode has changed. Please restart the app to apply the new theme."
            textSize = 15f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(16f))
        }
        cardLayout.addView(messageTv)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54f)
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
            setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54f)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
                recreate()
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        btnRow.addView(restartBtn)

        cardLayout.addView(btnRow)

        dialog.setContentView(cardLayout)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    private fun checkSignature(): Boolean {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }

            val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (certificates == null || certificates.isEmpty()) return false

            val cert = certificates[0]
            val certBytes = cert.toByteArray()
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(certBytes)
            val hexHash = hash.joinToString("") { "%02x".format(it) }
            hexHash.equals(EXPECTED_SIGNATURE_HASH, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Signature check failed", e)
            false
        }
    }

    private fun showTamperedDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val cardBg = if (isDark) Color.parseColor("#1C1C1E") else Color.parseColor("#FFFFFF")
        val cardBorder = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")
        val primaryText = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#000000")
        val secondaryText = if (isDark) Color.parseColor("#8E8E93") else Color.parseColor("#6C6C70")
        val accent = if (isDark) Color.parseColor("#3E82F7") else Color.parseColor("#0066FF")
        val red = if (isDark) Color.parseColor("#FF453A") else Color.parseColor("#FF3B30")

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

        val warnDrawable = ContextCompat.getDrawable(this, android.R.drawable.stat_notify_error)?.apply {
            setTint(red)
        }
        val iconIv = ImageView(this).apply {
            setImageDrawable(warnDrawable)
            layoutParams = LinearLayout.LayoutParams(dpToPx(60f), dpToPx(60f)).apply {
                gravity = Gravity.CENTER
                bottomMargin = dpToPx(8f)
            }
        }
        cardLayout.addView(iconIv)

        val titleTv = TextView(this).apply {
            text = "Security Alert"
            textSize = 22f
            setTextColor(primaryText)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(4f))
        }
        cardLayout.addView(titleTv)

        val messageTv = TextView(this).apply {
            text = "This application has been modified by an unknown third party. Its authenticity and integrity cannot be verified. Using it may pose a security risk. Please download the official version from the trusted source."
            textSize = 15f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(16f))
        }
        cardLayout.addView(messageTv)

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val downloadBtn = TextView(this).apply {
            text = "Download from Official Source"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(accent)
                cornerRadius = dpToPx(100f).toFloat()
            }
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54f)
            ).apply {
                bottomMargin = dpToPx(8f)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_SOURCE_URL)))
                finish()
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        btnLayout.addView(downloadBtn)

        val exitUninstallBtn = TextView(this).apply {
            text = "Exit and Uninstall"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(red)
                cornerRadius = dpToPx(100f).toFloat()
            }
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(54f)
            ).apply {
                topMargin = dpToPx(8f)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
                try {
                    val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@CrashLogActivity, "Unable to uninstall", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        btnLayout.addView(exitUninstallBtn)

        cardLayout.addView(btnLayout)

        dialog.setContentView(cardLayout)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        dialog.setCancelable(false)
        dialog.show()
    }

    private fun showPermissionDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBgColor)
                cornerRadius = dpToPx(28f).toFloat()
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

        // ADB SECTION
        val adbShellLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(redBtnColor)
                setAlpha(30)
                cornerRadius = dpToPx(12f).toFloat()
            }
            setPadding(dpToPx(16f), dpToPx(14f), dpToPx(16f), dpToPx(14f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12f)
                bottomMargin = dpToPx(8f)
            }
        }

        val shellHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val shellTitle = TextView(this).apply {
            text = "Enter ADB shell"
            textSize = 14f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        shellHeader.addView(shellTitle)

        val copyShellIcon = ImageView(this).apply {
            setImageResource(R.drawable.content_copy_24px)
            setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
            isClickable = true
            isFocusable = true
            setPadding(dpToPx(8f), dpToPx(8f), dpToPx(8f), dpToPx(8f))
            layoutParams = LinearLayout.LayoutParams(dpToPx(32f), dpToPx(32f))
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("ADB Shell Command", "adb shell")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@CrashLogActivity, "ADB shell command copied", Toast.LENGTH_SHORT).show()
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        shellHeader.addView(copyShellIcon)

        adbShellLayout.addView(shellHeader)

        val shellCommand = TextView(this).apply {
            text = "adb shell"
            textSize = 11f
            setTextColor(accentColor)
            setTypeface(Typeface.MONOSPACE)
            setPadding(0, dpToPx(4f), 0, 0)
        }
        adbShellLayout.addView(shellCommand)
        cardLayout.addView(adbShellLayout)

        val adbCommandsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(redBtnColor)
                setAlpha(30)
                cornerRadius = dpToPx(12f).toFloat()
            }
            setPadding(dpToPx(16f), dpToPx(14f), dpToPx(16f), dpToPx(14f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8f)
                bottomMargin = dpToPx(12f)
            }
        }

        val commandsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val commandsTitle = TextView(this).apply {
            text = "Run these commands inside ADB shell"
            textSize = 14f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        commandsHeader.addView(commandsTitle)

        val copyCommandsIcon = ImageView(this).apply {
            setImageResource(R.drawable.content_copy_24px)
            setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
            isClickable = true
            isFocusable = true
            setPadding(dpToPx(8f), dpToPx(8f), dpToPx(8f), dpToPx(8f))
            layoutParams = LinearLayout.LayoutParams(dpToPx(32f), dpToPx(32f))
            setOnClickListener {
                val commands = listOf(
                    "pm grant ${packageName} android.permission.READ_LOGS",
                    "pm grant ${packageName} android.permission.READ_DROPBOX_DATA",
                    "appops set ${packageName} GET_USAGE_STATS allow"
                )
                val textToCopy = commands.joinToString("\n")
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("ADB Commands", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@CrashLogActivity, "ADB commands copied", Toast.LENGTH_SHORT).show()
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        commandsHeader.addView(copyCommandsIcon)

        adbCommandsLayout.addView(commandsHeader)

        val adbCommand1 = TextView(this).apply {
            text = "pm grant ${packageName} android.permission.READ_LOGS"
            textSize = 11f
            setTextColor(accentColor)
            setTypeface(Typeface.MONOSPACE)
            setPadding(0, dpToPx(4f), 0, 0)
        }
        adbCommandsLayout.addView(adbCommand1)

        val adbCommand2 = TextView(this).apply {
            text = "pm grant ${packageName} android.permission.READ_DROPBOX_DATA"
            textSize = 11f
            setTextColor(accentColor)
            setTypeface(Typeface.MONOSPACE)
        }
        adbCommandsLayout.addView(adbCommand2)

        val adbCommand3 = TextView(this).apply {
            text = "appops set ${packageName} GET_USAGE_STATS allow"
            textSize = 11f
            setTextColor(accentColor)
            setTypeface(Typeface.MONOSPACE)
        }
        adbCommandsLayout.addView(adbCommand3)

        val adbNote = TextView(this).apply {
            text = "Some permissions may require a reboot to take effect"
            textSize = 11f
            setTextColor(secondaryTextColor)
            setPadding(0, dpToPx(8f), 0, 0)
        }
        adbCommandsLayout.addView(adbNote)

        cardLayout.addView(adbCommandsLayout)

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

    private fun setupUI() {
        val rootBgColor = MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceContainer)

        rootFrameLayout = FrameLayout(this).apply {
            setBackgroundColor(rootBgColor)
        }

        val statusBarHeight = getStatusBarHeight()

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

        // LOGS TAB
        logsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 过滤 Pill
        filterPillContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
                setColor(cardBgColor)
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
            setColor(activePillBgColor)
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
                    view.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .alpha(0.9f)
                        .setDuration(120)
                        .setInterpolator(DecelerateInterpolator(1.5f))
                        .start()
                    updateFilterPillPosition(computeProgress(event.x))
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
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
                MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    view.animate().cancel()
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        .setDuration(200)
                        .start()
                    val activeProgress = when (currentFilterTab) {
                        FILTER_ALL -> 0f
                        FILTER_CRASH -> 0.5f
                        else -> 1f
                    }
                    animateFilterPillTo(activeProgress) {}
                    true
                }
                else -> false
            }
        }

        logsLayout.addView(filterPillContainer)

        loadingText = TextView(this).apply {
            text = "Loading Full Logs (Might Take 5 to 10 Seconds the first time)"
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

        emptyStateText = TextView(this).apply {
            text = "No logs found.\nTry refresh?"
            textSize = 18f
            setTextColor(secondaryTextColor)
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(80f), 0, dpToPx(80f))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        logsLayout.addView(emptyStateText)

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
            ::onItemClick,
            this::dpToPx
        )
        recyclerView.adapter = logAdapter
        logsLayout.addView(recyclerView)

        // INFO TAB
        infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 应用信息卡片
        val appInfoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBgColor)
                cornerRadius = dpToPx(28f).toFloat()
            }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && googleSansFlexTypeface != null) {
                typeface = Typeface.create(googleSansFlexTypeface, 800, false)
                fontVariationSettings = "'wght' 800, 'ROND' 100, 'opsz' 14"
            } else {
                typeface = googleSansFlexTypeface ?: Typeface.DEFAULT_BOLD
            }
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
            setTextColor(accentColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(16f), dpToPx(8f), dpToPx(16f), dpToPx(8f))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(100f).toFloat()
                setColor(onPrimaryColor)
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

        // 统计卡片（包含计数器和刷新按钮）
        val statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBgColor)
                cornerRadius = dpToPx(28f).toFloat()
            }
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

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(16f)
            }
        }

        refreshButton = createAnimatedButton("Refresh Logs", onPrimaryColor, accentColor, LinearLayout.LayoutParams.MATCH_PARENT) {
            performRefresh()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, buttonHeightPx, 1f).apply {
                marginEnd = dpToPx(6f)
            }
        }
        buttonRow.addView(refreshButton)

        val clearCacheBtn = createAnimatedButton("Clear Cache", Color.WHITE, redBtnColor, LinearLayout.LayoutParams.MATCH_PARENT) {
            clearCache()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, buttonHeightPx, 1f).apply {
                marginStart = dpToPx(6f)
            }
        }
        buttonRow.addView(clearCacheBtn)

        statsCard.addView(buttonRow)
        infoLayout.addView(statsCard)
// ===== 独立的开关卡片 =====
val autoRefreshCard = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = GradientDrawable().apply {
        setColor(cardBgColor)
        cornerRadius = dpToPx(28f).toFloat()
    }
    setPadding(dpToPx(20f), dpToPx(12f), dpToPx(20f), dpToPx(12f))
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        bottomMargin = dpToPx(16f)
    }
}

val autoRefreshRow = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
val autoRefreshLabel = TextView(this).apply {
    text = "Auto-refresh every 5s"
    textSize = 15f
    setTextColor(primaryTextColor)
    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
}
autoRefreshRow.addView(autoRefreshLabel)

// ★ 使用 inflate 方式加载 MaterialSwitch（与 DeveloperOptions 一致）
val switch = LayoutInflater.from(this).inflate(R.layout.switch_material, null) as MaterialSwitch

// 轨道颜色
val trackStates = arrayOf(
    intArrayOf(android.R.attr.state_checked),
    intArrayOf()
)
val trackColors = intArrayOf(trackOnColor, trackOffColor)
switch.trackTintList = ColorStateList(trackStates, trackColors)

// 拇指颜色
val thumbStates = arrayOf(
    intArrayOf(android.R.attr.state_checked),
    intArrayOf()
)
val thumbColors = intArrayOf(thumbOnColor, thumbOffColor)
switch.thumbTintList = ColorStateList(thumbStates, thumbColors)

// 对勾/叉号图标颜色
val iconStates = arrayOf(
    intArrayOf(android.R.attr.state_checked),
    intArrayOf()
)
val iconColors = intArrayOf(accentColor, trackOffColor)
switch.thumbIconTintList = ColorStateList(iconStates, iconColors)

// 读取保存的状态，默认关闭
val prefs = getSharedPreferences("auto_refresh_prefs", Context.MODE_PRIVATE)
val saved = prefs.getBoolean("auto_refresh_enabled", false)
switch.isChecked = saved

switch.setOnCheckedChangeListener { _, isChecked ->
    if (isChecked) {
        // 如果是确认过程中触发的，直接放行
        if (isConfirmingAutoRefresh) {
            isConfirmingAutoRefresh = false
            return@setOnCheckedChangeListener
        }
        // 用户尝试开启，取消勾选并弹窗
        switch.isChecked = false
        showAutoRefreshConfirmDialog(switch, prefs)
    } else {
        // 用户主动关闭
        prefs.edit().putBoolean("auto_refresh_enabled", false).apply()
        stopAutoRefresh()
    }
}
switch.layoutParams = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.WRAP_CONTENT,
    LinearLayout.LayoutParams.WRAP_CONTENT
)
autoRefreshRow.addView(switch)
autoRefreshCard.addView(autoRefreshRow)

infoLayout.addView(autoRefreshCard)

// 保存引用
autoRefreshSwitch = switch

if (autoRefreshSwitch?.isChecked == true) {
    startAutoRefresh()
}
// ====================================
        scrollContent.addView(logsLayout)
        scrollContent.addView(infoLayout)
        scrollView.addView(scrollContent)
        rootFrameLayout.addView(scrollView)

        // TOP BAR
        val topBarLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(rootBgColor)
            elevation = 0f
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

        topBarRefreshContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(backBtnBgColor)
            }
            layoutParams = FrameLayout.LayoutParams(dpToPx(44f), dpToPx(44f), Gravity.END or Gravity.CENTER_VERTICAL)
            isClickable = true
            isFocusable = true
            setOnClickListener { performRefresh() }
            setOnTouchListener(pressScaleTouchListener)
        }

        topBarRefreshIcon = ImageView(this).apply {
            setImageResource(R.drawable.refresh_24px)
            setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dpToPx(6f), dpToPx(6f), dpToPx(6f), dpToPx(6f))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }

        topBarRefreshCountdown = TextView(this).apply {
            visibility = View.GONE
            textSize = 20f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }

        topBarRefreshContainer.addView(topBarRefreshIcon)
        topBarRefreshContainer.addView(topBarRefreshCountdown)

        topBarLayout.addView(topBarTitle)
        topBarLayout.addView(backBtn)
        topBarLayout.addView(topBarRefreshContainer)
        rootFrameLayout.addView(topBarLayout)

        // BOTTOM BAR
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
            }
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val activeTabBg = GradientDrawable().apply {
            setColor(activePillBgColor)
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
            val icon = ContextCompat.getDrawable(this@CrashLogActivity, R.drawable.assignment_20px)?.apply {
                setTint(primaryTextColor)
                setBounds(0, 0, dpToPx(20f), dpToPx(20f))
            }
            setCompoundDrawables(icon, null, null, null)
            compoundDrawablePadding = dpToPx(8f)
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
            val icon = ContextCompat.getDrawable(this@CrashLogActivity, R.drawable.info_20px)?.apply {
                setTint(secondaryTextColor)
                setBounds(0, 0, dpToPx(20f), dpToPx(20f))
            }
            setCompoundDrawables(icon, null, null, null)
            compoundDrawablePadding = dpToPx(8f)
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

        // 底部切换逻辑
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

        scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            val alpha = (scrollY / dpToPx(40f).toFloat()).coerceIn(0f, 1f)
            topBarTitle.alpha = alpha
        })

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

    private fun performRefresh() {
        if (isCooldown) return

        refreshButton.isEnabled = false
        refreshButton.text = "Refreshing..."

        topBarRefreshIcon.visibility = View.GONE
        topBarRefreshCountdown.visibility = View.VISIBLE
        topBarRefreshCountdown.text = "5"
        topBarRefreshContainer.isEnabled = false

        loadLogsAsync {
            startCooldown()
        }
    }

    private fun startCooldown() {
        isCooldown = true
        remainingSeconds = 5

        cooldownTimer?.cancel()
        cooldownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = (millisUntilFinished / 1000).toInt()
                refreshButton.text = "Cooldown $remainingSeconds"
                topBarRefreshCountdown.text = remainingSeconds.toString()
                refreshButton.isEnabled = false
                topBarRefreshContainer.isEnabled = false
            }

            override fun onFinish() {
                isCooldown = false
                refreshButton.text = "Refresh Logs"
                refreshButton.isEnabled = true
                topBarRefreshContainer.isEnabled = true
                topBarRefreshIcon.visibility = View.VISIBLE
                topBarRefreshCountdown.visibility = View.GONE
                cooldownTimer = null
            }
        }.start()
    }

    override fun onDestroy() {
        cooldownTimer?.cancel()
        stopAutoRefresh()
        super.onDestroy()
    }

    private fun startAutoRefresh() {
        stopAutoRefresh()
        autoRefreshRunnable = object : Runnable {
            override fun run() {
                if (currentTab == TAB_LOGS && autoRefreshSwitch?.isChecked == true) {
                    loadLogsAsync {}
                }
                if (autoRefreshSwitch?.isChecked == true) {
                    autoRefreshHandler.postDelayed(this, 5000L)
                }
            }
        }
        autoRefreshRunnable?.let { autoRefreshHandler.postDelayed(it, 5000L) }
    }

    private fun stopAutoRefresh() {
        autoRefreshRunnable?.let { autoRefreshHandler.removeCallbacks(it) }
        autoRefreshRunnable = null
    }

private fun showAutoRefreshConfirmDialog(switch: MaterialSwitch, prefs: SharedPreferences) {
    val dialog = Dialog(this)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
    dialog.setCancelable(false)

    val dpToPx = { dp: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt() }

    // 卡片背景（与权限菜单一致）
    val cardLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(cardBgColor)
            cornerRadius = dpToPx(28f).toFloat()
            setStroke(dpToPx(1f), cardBorderColor)
        }
        setPadding(dpToPx(24f), dpToPx(28f), dpToPx(24f), dpToPx(24f))
    }

    // 标题
    val titleTv = TextView(this).apply {
        text = "Note"
        textSize = 22f
        setTextColor(primaryTextColor)
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dpToPx(8f))
    }
    cardLayout.addView(titleTv)

    // 描述文字
    val messageTv = TextView(this).apply {
        text = "Auto Refresh REQUIRED a device that can handle, enable on a weak device is UNSTABLE\n\n" +
                "Facing bugs after enable? Disable this option and press \"Clear Cache\""
        textSize = 15f
        setTextColor(secondaryTextColor)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dpToPx(16f))
    }
    cardLayout.addView(messageTv)

    // 按钮行
    val btnRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(54f)
        )
    }

    // Leave 按钮（左）
    val leaveBtn = TextView(this).apply {
        text = "Leave"
        textSize = 15f
        setTextColor(primaryTextColor)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(secondaryBtnColor)
            cornerRadius = dpToPx(100f).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            marginEnd = dpToPx(6f)
        }
        isClickable = true
        isFocusable = true
        setOnClickListener {
            dialog.dismiss()
        }
        setOnTouchListener(pressScaleTouchListener)
    }
    btnRow.addView(leaveBtn)

    // Enable 按钮（右）
    val enableBtn = TextView(this).apply {
        text = "Enable"
        textSize = 15f
        setTextColor(accentColor)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(onPrimaryColor)
            cornerRadius = dpToPx(100f).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            marginStart = dpToPx(6f)
        }
        isClickable = true
        isFocusable = true
        setOnClickListener {
            dialog.dismiss()
            // 用户确认启用
            isConfirmingAutoRefresh = true
            switch.isChecked = true
            prefs.edit().putBoolean("auto_refresh_enabled", true).apply()
            startAutoRefresh()
        }
        setOnTouchListener(pressScaleTouchListener)
    }
    btnRow.addView(enableBtn)

    cardLayout.addView(btnRow)

    dialog.setContentView(cardLayout)

    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT)
    }

    dialog.show()
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

    private fun applyFilters() {
        val result = when (currentFilterTab) {
            FILTER_CRASH -> allLogs.filter { it.type == "Crash" }
            FILTER_ANR -> allLogs.filter { it.type == "ANR" }
            else -> allLogs
        }
        filteredLogs.clear()
        filteredLogs.addAll(result)
        logAdapter.updateLogs(filteredLogs)

        if (filteredLogs.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
        }
        recyclerView.alpha = 1f
        recyclerView.translationY = 0f
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

        val logsIcon = logsPillBtn.compoundDrawables[0]
        val infoIcon = infoPillBtn.compoundDrawables[0]
        logsIcon?.setTint(if (p < 0.5f) primaryTextColor else secondaryTextColor)
        infoIcon?.setTint(if (p < 0.5f) secondaryTextColor else primaryTextColor)
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

    private fun getCacheFile(): File {
        return File(filesDir, "crash_logs_cache.json")
    }

    private fun saveLogsToCache() {
        try {
            val jsonArray = JSONArray()
            for (log in allLogs) {
                val obj = JSONObject()
                obj.put("timestamp", log.timestamp)
                obj.put("appName", log.appName)
                obj.put("type", log.type)
                obj.put("details", log.details)
                jsonArray.put(obj)
            }
            getCacheFile().writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache", e)
        }
    }

    private fun loadLogsFromCache(): List<LogEntry>? {
        return try {
            val file = getCacheFile()
            if (!file.exists()) return null
            val content = file.readText()
            val jsonArray = JSONArray(content)
            val list = mutableListOf<LogEntry>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val timestamp = obj.getString("timestamp")
                val appName = obj.getString("appName")
                val type = obj.getString("type")
                val details = obj.getString("details")
                list.add(LogEntry(timestamp, appName, type, details))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cache", e)
            null
        }
    }

    private fun clearCache() {
        try {
            val file = getCacheFile()
            if (file.exists()) file.delete()
            allLogs.clear()
            filteredLogs.clear()
            logAdapter.updateLogs(filteredLogs)
            applyFilters()
            updateStats()
            Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache", e)
        }
    }

    private fun loadLogsAsync(onComplete: () -> Unit) {
        val cachedLogs = loadLogsFromCache()
        if (cachedLogs != null) {
            allLogs.clear()
            allLogs.addAll(cachedLogs)
            applyFilters()
            updateStats()
            recyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
            loadingText.visibility = View.GONE
            Thread {
                loadLogs()
                runOnUiThread {
                    applyFilters()
                    updateStats()
                    saveLogsToCache()
                    onComplete()
                }
            }.start()
        } else {
            loadingText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyStateText.visibility = View.GONE
            Thread {
                loadLogs()
                runOnUiThread {
                    loadingText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    emptyStateText.visibility = View.GONE
                    applyFilters()
                    updateStats()
                    saveLogsToCache()
                    onComplete()
                }
            }.start()
        }
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

    private fun initColors() {
        isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        cardBgColor = if (isDark) {
            MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceContainerHigh)
        } else {
            MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceContainerLowest)
        }
        cardBorderColor = MonetColorHelper.getColor(this, MaterialR.attr.colorOutlineVariant)
        primaryTextColor = MonetColorHelper.getColor(this, MaterialR.attr.colorOnSurface)
        secondaryTextColor = MonetColorHelper.getColor(this, MaterialR.attr.colorOnSurfaceVariant)
        accentColor = MonetColorHelper.getColor(this, MaterialR.attr.colorPrimary)
        onPrimaryColor = MonetColorHelper.getColor(this, MaterialR.attr.colorOnPrimary)
        secondaryBtnColor = MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceContainerHigh)

        activePillBgColor = if (isDark) {
            MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceContainerHighest)
        } else {
            secondaryBtnColor
        }

        backBtnBgColor = MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceContainerHigh)
        inputBgColor = if (isDark) {
            MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceContainer)
        } else {
            MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceContainerLowest)
        }
        redBtnColor = if (isDark) Color.parseColor("#FF453A") else Color.parseColor("#FF3B30")

        // 开关颜色
        trackOnColor = accentColor
        trackOffColor = if (isDark) {
            MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceContainer)
        } else {
            MonetColorHelper.getColor(this, MaterialR.attr.colorSurfaceContainerHigh)
        }
        thumbOnColor = if (isDark) {
            MonetColorHelper.getColor(this, MaterialR.attr.colorOnPrimary)
        } else {
            Color.WHITE
        }
        thumbOffColor = MonetColorHelper.getColor(this, MaterialR.attr.colorOutline)
    }

    private fun createCardBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(cardBgColor)
            cornerRadius = dpToPx(28f).toFloat()
        }
    }

    private fun createStatCard(label: String, value: String, color: Int, isError: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(inputBgColor)
                cornerRadius = dpToPx(16f).toFloat()
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

// ========== DATA CLASS & ADAPTER ==========
data class LogEntry(
    val timestamp: String,
    val appName: String,
    val type: String,
    val details: String = ""
)

class LogAdapter(
    private var logs: List<LogEntry>,
    private val context: Context,
    private val packageManager: PackageManager,
    private val cardBgColor: Int,
    private val cardBorderColor: Int,
    private val onItemClick: (LogEntry) -> Unit,
    private val dpToPx: (Float) -> Int
) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val defaultIcon = ContextCompat.getDrawable(
        context,
        android.R.drawable.sym_def_app_icon
    )

    private val iconCache = LruCache<String, Bitmap>(50)
    private val MAX_ICON_SIZE_PX = dpToPx(40f)

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
            cardBorderColor,
            ::getScaledIcon
        )
    }

    override fun getItemCount(): Int = logs.size

    private fun getScaledIcon(packageName: String): Drawable? {
        val cached = iconCache.get(packageName)
        if (cached != null) {
            return BitmapDrawable(context.resources, cached)
        }

        val originalDrawable = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationIcon(appInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            defaultIcon
        } ?: defaultIcon

        val nonNullDrawable = originalDrawable ?: return null
        val bitmap = drawableToBitmap(nonNullDrawable)
        if (bitmap == null) {
            return nonNullDrawable
        }

        val width = bitmap.width
        val height = bitmap.height
        var scaledBitmap: Bitmap? = null

        if (width > MAX_ICON_SIZE_PX || height > MAX_ICON_SIZE_PX) {
            val scale = MAX_ICON_SIZE_PX.toFloat() / maxOf(width, height)
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()

            scaledBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(scaledBitmap)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bitmap, Rect(0, 0, width, height),
                Rect(0, 0, newWidth, newHeight), paint)

            if (scaledBitmap != bitmap) {
                bitmap.recycle()
            }
        } else {
            scaledBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            if (scaledBitmap != bitmap) {
                bitmap.recycle()
            }
        }

        scaledBitmap?.let {
            iconCache.put(packageName, it)
            return BitmapDrawable(context.resources, it)
        }

        return nonNullDrawable
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        if (width <= 0 || height <= 0) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    inner class LogViewHolder(
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

            appIcon.scaleType = ImageView.ScaleType.CENTER_CROP
            appIcon.clipToOutline = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                appIcon.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val size = view.width.coerceAtMost(view.height)
                        if (size > 0) {
                            outline.setOval(0, 0, size, size)
                        } else {
                            outline.setOval(0, 0, view.width, view.height)
                        }
                    }
                }
            }
        }

        fun bind(
            log: LogEntry,
            pm: PackageManager,
            defaultIcon: Drawable?,
            cardBg: Int,
            cardBorder: Int,
            iconLoader: (String) -> Drawable?
        ) {
            timestampText.text = log.timestamp
            val cleanPackage = log.appName.substringBefore(":")
            appNameText.text = cleanPackage

            // ★ 移除边框：不再调用 setStroke
            val cardDrawable = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    16f,
                    itemView.context.resources.displayMetrics
                )
            }
            cardLayout.background = cardDrawable

            val isMagisk = cleanPackage.equals("magisk", ignoreCase = true)
            val badgeText = if (isMagisk) "Magisk" else log.type
            typeBadge.text = badgeText

            val iconDrawable = if (cleanPackage.isNotEmpty() && cleanPackage.contains(".")) {
                iconLoader(cleanPackage) ?: defaultIcon
            } else {
                defaultIcon
            }
            appIcon.setImageDrawable(iconDrawable)

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