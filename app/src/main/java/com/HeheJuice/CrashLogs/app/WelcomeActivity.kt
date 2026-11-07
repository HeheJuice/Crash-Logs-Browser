package com.HeheJuice.CrashLogs.app

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.HeheJuice.CrashLogs.R
import com.HeheJuice.CrashLogs.util.MonetColorManager
import com.HeheJuice.CrashLogs.util.OptimizeEngine
import com.HeheJuice.CrashLogs.util.PermissionHelper
import com.HeheJuice.CrashLogs.util.Sine
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.loadingindicator.LoadingIndicator
import com.google.android.material.radiobutton.MaterialRadioButton
import java.security.MessageDigest

class WelcomeActivity : AppCompatActivity() {

    companion object {
        private const val KEY_PENDING_LANG_FADE = "pending_language_fade_in"
        private const val KEY_USER_LANGUAGE_TAG = "user_language_tag"
        private const val KEY_WELCOME_LAUNCHED = "welcome_launched"
        private const val KEY_WELCOME_VERSION = "welcome_version"
        private const val CARD_EXTRA_HEIGHT_DP = 400f

        const val EXTRA_FORCE_UPGRADE = "force_upgrade_preview"

        private const val PAGE4_LOADING_MS = 2400L
        private const val PAGE_SWITCH_ANIM_MS = 380L

        // ★ 請替換為你自己的簽名 SHA-256（去掉冒號、小寫）
        private const val EXPECTED_SIGNATURE_SHA256 =
            "bbd053c0a67dbfc7938a3086d13325bfa212d550d6e6cbf39e48edf6b52bfc0d"

        private const val REPO_HTML =
            "https://github.com/HeheJuice/TangentUI-Sample"

        private const val REPO_RELEASES =
            "https://github.com/HeheJuice/TangentUI-Sample/releases/latest"
    }

    private var primaryContainerColor: Int = 0
    private var secondaryContainerColor: Int = 0
    private var onSecondaryContainerColor: Int = 0
    private var onPrimaryContainerColor: Int = 0
    private var surfaceContainerColor: Int = 0
    private var cardSurfaceColor: Int = 0
    private var surfaceBrightColor: Int = 0
    private var onSurfaceColor: Int = 0
    private var onSurfaceVariantColor: Int = 0

    private val feedbackTargets = mutableListOf<OptimizeEngine.FeedbackTarget>()

    private lateinit var root: FrameLayout
    private lateinit var topBar: LinearLayout
    private lateinit var contentLayer: FrameLayout
    private lateinit var cardContainer: FrameLayout
    private lateinit var animWrapper: FrameLayout
    private lateinit var cardLinearLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var backBtnContainer: FrameLayout
    private lateinit var backBtnIcon: ImageView
    private lateinit var languageBtn: ImageView
    private lateinit var refreshBtn: ImageView
    private lateinit var handleTouchArea: FrameLayout

    private var scrollDownBtn: ImageView? = null
    private var continueBtn: View? = null
    private var currentPage: Int = 1

    private var dragStartY: Float = 0f
    private var isDragging: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var page4LoadingRunnable: Runnable? = null

    private var page4Loaded: Boolean = false
    private var isPageSwitchAnimating: Boolean = false
    private var isUpgrade: Boolean = false

    // ── Permission page (Page 3) ──
    private data class PermRowRef(
        val permission: String,
        val row: LinearLayout,
        val statusIcon: ImageView
    )
    private val permRowRefs = mutableListOf<PermRowRef>()
    private var permContinueBtn: MaterialButton? = null
    private var permStatusSummary: TextView? = null
    private var rootGrantBtn: MaterialButton? = null
    private var isRootGranting: Boolean = false

    private fun dpToPx(dp: Float): Int = OptimizeEngine.dpToPx(dp)
    private fun dpToPx(dp: Int): Int = OptimizeEngine.dpToPx(dp)

    private fun getAppVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
    } catch (_: Exception) { "1.0.0" }

    private fun applyMd3DisabledColors(button: MaterialButton) {
        val disabledBg = ColorUtils.setAlphaComponent(onSurfaceColor, 31)
        val disabledFg = ColorUtils.setAlphaComponent(onSurfaceColor, 97)
        button.backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(primaryContainerColor, disabledBg))
        button.setTextColor(ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(onPrimaryContainerColor, disabledFg)))
    }

    private fun makeRowRipple(contentColor: Int, topRadius: Float, bottomRadius: Float): RippleDrawable {
        val content = OptimizeEngine.groupItemBackground(contentColor, topRadius, bottomRadius)
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(
                topRadius, topRadius, topRadius, topRadius,
                bottomRadius, bottomRadius, bottomRadius, bottomRadius)
            setColor(Color.WHITE)
        }
        val rippleColor = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(onSurfaceVariantColor, 40))
        return RippleDrawable(rippleColor, content, mask)
    }

    private fun makeRipple(contentDrawable: Drawable, radius: Float): RippleDrawable {
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.WHITE)
        }
        val rippleColor = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(onSurfaceVariantColor, 40))
        return RippleDrawable(rippleColor, contentDrawable, mask)
    }

    private fun isSignatureOfficial(): Boolean {
        return try {
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION") pi.signatures
            }
            if (signatures == null || signatures.isEmpty()) return false
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(signatures[0].toByteArray())
            val fp = digest.joinToString("") { "%02x".format(it) }
            fp.equals(EXPECTED_SIGNATURE_SHA256, ignoreCase = true)
        } catch (_: Exception) { false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isPageSwitchAnimating) return
                when (currentPage) {
                    5, 4 -> switchToPage(3)
                    3 -> switchToPage(2)
                    2 -> switchToPage(1)
                    else -> finishAffinity()
                }
            }
        })

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark

        MonetColorManager.applyToActivity(this)

        primaryContainerColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorPrimaryContainer, Color.parseColor("#E8DEF8"))
        onPrimaryContainerColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnPrimaryContainer, Color.parseColor("#4F378B"))
        secondaryContainerColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSecondaryContainer,
            if (isDark) Color.parseColor("#4A4458") else Color.parseColor("#E8DEF8"))
        onSecondaryContainerColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSecondaryContainer,
            if (isDark) Color.parseColor("#E8DEF8") else Color.parseColor("#1D192B"))
        surfaceContainerColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurfaceContainer,
            if (isDark) Color.parseColor("#1C1B1F") else Color.parseColor("#FEF7FF"))
        cardSurfaceColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            if (isDark) Color.parseColor("#2B2930") else Color.parseColor("#ECE6F0"))
        surfaceBrightColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurfaceBright,
            if (isDark) Color.parseColor("#3B3B3E") else Color.parseColor("#FFFFFF"))
        onSurfaceColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurface,
            if (isDark) Color.parseColor("#E6E1E5") else Color.parseColor("#1C1B1F"))
        onSurfaceVariantColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.parseColor("#49454F"))

        root = FrameLayout(this).apply {
            setBackgroundColor(surfaceContainerColor)
            clipChildren = false; clipToPadding = false
        }

        contentLayer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            clipChildren = false; clipToPadding = false
        }
        root.addView(contentLayer)

        cardContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    dpToPx(28f).toFloat(), dpToPx(28f).toFloat(),
                    dpToPx(28f).toFloat(), dpToPx(28f).toFloat(),
                    0f, 0f, 0f, 0f)
                setColor(cardSurfaceColor)
            }
            clipToOutline = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        contentLayer.addView(cardContainer)

        animWrapper = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            clipChildren = false; clipToPadding = false
        }
        cardContainer.addView(animWrapper)

        cardLinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            clipChildren = false; clipToPadding = false
        }

        backBtnContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(surfaceBrightColor)
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(dpToPx(50f), dpToPx(50f))
            isClickable = true; isFocusable = true
            contentDescription = getString(R.string.back_content_desc)
            setOnClickListener {
                if (isPageSwitchAnimating) return@setOnClickListener
                when (currentPage) {
                    5, 4 -> switchToPage(3)
                    3 -> switchToPage(2)
                    2 -> switchToPage(1)
                    else -> finishAffinity()
                }
            }
        }

        backBtnIcon = ImageView(this).apply {
            setImageResource(R.drawable.close_24px)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = FrameLayout.LayoutParams(dpToPx(24f), dpToPx(24f)).apply {
                gravity = Gravity.CENTER
            }
            setColorFilter(onSurfaceVariantColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
        }
        backBtnContainer.addView(backBtnIcon)
        topBar.addView(backBtnContainer)
        feedbackTargets += OptimizeEngine.FeedbackTarget(backBtnContainer)

        topBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        languageBtn = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@WelcomeActivity, R.drawable.language_24px))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(surfaceBrightColor)
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val size = dpToPx(50f); val pad = dpToPx(12f)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(size, size)
            isClickable = true; isFocusable = true
            contentDescription = getString(R.string.settings_language)
            setOnClickListener {
                if (isPageSwitchAnimating) return@setOnClickListener
                OptimizeEngine.vibrateTick(this@WelcomeActivity)
                showLanguagePickerDialog()
            }
        }
        topBar.addView(languageBtn)
        feedbackTargets += OptimizeEngine.FeedbackTarget(languageBtn)

        refreshBtn = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@WelcomeActivity, R.drawable.refresh_24px))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(surfaceBrightColor)
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val size = dpToPx(50f); val pad = dpToPx(12f)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = dpToPx(8f)
            }
            isClickable = true; isFocusable = true
            contentDescription = getString(R.string.welcome_perm_refresh)
            setOnClickListener {
                if (isPageSwitchAnimating) return@setOnClickListener
                OptimizeEngine.vibrateTick(this@WelcomeActivity)
                refreshPermissionStatus()
            }
            visibility = View.GONE
        }
        topBar.addView(refreshBtn)
        feedbackTargets += OptimizeEngine.FeedbackTarget(refreshBtn)

        cardLinearLayout.addView(topBar)

        scrollView = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        scrollView.addView(content)
        cardLinearLayout.addView(scrollView)
        animWrapper.addView(cardLinearLayout)

        handleTouchArea = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(dpToPx(100f), dpToPx(40f)).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dpToPx(4f)
            }
            isClickable = true; isFocusable = true
        }

        val handle = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(2f).toFloat()
                setColor(ColorUtils.setAlphaComponent(onSurfaceVariantColor, 100))
            }
            layoutParams = FrameLayout.LayoutParams(dpToPx(48f), dpToPx(4f)).apply {
                gravity = Gravity.CENTER
            }
        }
        handleTouchArea.addView(handle)

        handleTouchArea.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartY = event.rawY
                    isDragging = true
                    cardContainer.animate().cancel()
                    cardContainer.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging) return@setOnTouchListener true
                    val rawDy = event.rawY - dragStartY
                    val dy = if (rawDy < 0f) {
                        (rawDy * 0.3f).coerceAtLeast(-dpToPx(60f).toFloat())
                    } else rawDy.coerceAtLeast(0f)
                    val screenHeight = resources.displayMetrics.heightPixels
                    if (dy > screenHeight * 0.3f || dy <= -dpToPx(60f).toFloat()) {
                        isDragging = false
                        cardContainer.animate().translationY(0f)
                            .setDuration(500L).setInterpolator(OvershootInterpolator(1.5f)).start()
                    } else {
                        cardContainer.translationY = dy
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.performClick()
                    if (isDragging) {
                        isDragging = false
                        cardContainer.animate().translationY(0f)
                            .setDuration(500L).setInterpolator(OvershootInterpolator(1.5f)).start()
                    }
                    cardContainer.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }
        animWrapper.addView(handleTouchArea)

        scrollDownBtn = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@WelcomeActivity, R.drawable.arrow_downward_24px))
            setColorFilter(onSecondaryContainerColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16f).toFloat()
                setColor(secondaryContainerColor)
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val size = dpToPx(50f); val pad = dpToPx(12f)
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                bottomMargin = dpToPx(16f)
                rightMargin = dpToPx(16f)
            }
            isClickable = true; isFocusable = true
            setOnClickListener {
                OptimizeEngine.vibrateTick(this@WelcomeActivity)
                scrollView.smoothScrollTo(0, scrollView.getChildAt(0).height)
            }
            translationX = dpToPx(100f).toFloat()
            visibility = View.GONE
        }
        scrollDownBtn?.let { btn ->
            animWrapper.addView(btn)
            feedbackTargets += OptimizeEngine.FeedbackTarget(btn)
        }

        scrollView.viewTreeObserver.addOnScrollChangedListener {
            updateScrollDownButtonVisibility()
        }

        setContentView(root)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PENDING_LANG_FADE, false).apply()

        val alreadyLaunched = prefs.getBoolean(KEY_WELCOME_LAUNCHED, false)
        val lastVersion = prefs.getString(KEY_WELCOME_VERSION, null)
        val currentVersion = getAppVersionName()
        isUpgrade = alreadyLaunched && lastVersion != currentVersion
        if (intent.getBooleanExtra(EXTRA_FORCE_UPGRADE, false)) isUpgrade = true

        showPage(1, animate = false)

        cardContainer.post {
            val enterInterpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
            cardContainer.pivotX = cardContainer.width / 2f
            cardContainer.pivotY = animWrapper.height / 2f
            cardContainer.alpha = 0f
            cardContainer.scaleX = 0.85f
            cardContainer.scaleY = 0.85f
            cardContainer.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(1700L).setInterpolator(enterInterpolator).start()
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val hPad = if (isLandscape) dpToPx(32f) else dpToPx(16f)
            val screenHeight = resources.displayMetrics.heightPixels
            val topMargin = if (isLandscape) statusBarTop + dpToPx(20f) else statusBarTop + dpToPx(40f)
            val visibleHeight = screenHeight - topMargin

            val cardParams = cardContainer.layoutParams as FrameLayout.LayoutParams
            cardParams.topMargin = topMargin
            cardParams.height = visibleHeight + dpToPx(CARD_EXTRA_HEIGHT_DP)
            cardContainer.layoutParams = cardParams

            val animParams = animWrapper.layoutParams as FrameLayout.LayoutParams
            animParams.height = visibleHeight
            animWrapper.layoutParams = animParams

            topBar.setPadding(hPad, dpToPx(16f), hPad, dpToPx(8f))
            scrollView.setPadding(0, 0, 0, navBarBottom + dpToPx(16f))
            content.setPadding(hPad, 0, hPad, 0)

            scrollDownBtn?.let { btn ->
                val params = btn.layoutParams as FrameLayout.LayoutParams
                params.gravity = Gravity.BOTTOM or Gravity.END
                params.bottomMargin = navBarBottom + dpToPx(24f)
                params.rightMargin = hPad
                btn.layoutParams = params
            }
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentPage == 3) refreshPermissionStatus()
    }

    private fun showPage(page: Int, animate: Boolean = true) {
        if (page != 4) {
            page4LoadingRunnable?.let { mainHandler.removeCallbacks(it) }
            page4LoadingRunnable = null
        }
        currentPage = page
        content.removeAllViews()
        scrollView.scrollTo(0, 0)

        when (page) {
            1 -> setupPage1()
            2 -> setupPage2()
            3 -> setupPage3()
            5 -> setupPage5()
            else -> setupPage4()
        }

        if (animate) {
            content.alpha = 0f
            content.animate().alpha(1f).setDuration(300)
                .setInterpolator(FastOutSlowInInterpolator()).start()
        }
        scrollView.post { updateScrollDownButtonVisibility() }
    }

    private fun setupPage1() {
        val appIcon = try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            ContextCompat.getDrawable(this, android.R.drawable.sym_def_app_icon)
        }

        content.addView(ImageView(this).apply {
            setImageDrawable(appIcon)
            layoutParams = LinearLayout.LayoutParams(dpToPx(96f), dpToPx(96f)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dpToPx(20f)
                bottomMargin = dpToPx(16f)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        })

        content.addView(TextView(this).apply {
            text = getString(R.string.welcome_to)
            textSize = 18f
            setTextColor(onSurfaceVariantColor)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpToPx(12f) }
        })

        val sineTitle = Sine(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(12f)
            }
            textSizeSp = 32f
            setText(getString(R.string.welcome_line_1), onSurfaceColor)
        }
        content.addView(sineTitle)
        sineTitle.post {
            if (sineTitle.isAttachedToWindow) {
                sineTitle.animateSineWave(
                    durationMs = 750L, staggerMs = 45L, amplitudeDp = 22f,
                    phasePerChar = 0.7f, overshoot = true)
            }
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.welcome_disclaimer)
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            gravity = Gravity.CENTER
            alpha = 0.8f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpToPx(12f) }
        })

        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })

        continueBtn = makeFilledButton(
            label = getString(R.string.welcome_continue),
            heightDp = 56f, topRadiusDp = 100f, bottomRadiusDp = 100f,
            onClick = { switchToPage(2) }
        )
        content.addView(continueBtn)

        content.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(12f))
        })

        content.addView(makeOutlinedButton(
            label = getString(R.string.tangent_exit),
            heightDp = 56f, topRadiusDp = 100f, bottomRadiusDp = 100f,
            onClick = { finishAffinity() }
        ))

        content.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(24f))
        })
    }

    private fun setupPage2() {
        content.addView(TextView(this).apply {
            text = getString(R.string.welcome_for_your_review)
            textSize = 32f
            setTextColor(onPrimaryContainerColor)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8f); bottomMargin = dpToPx(16f)
                marginStart = dpToPx(8f); marginEnd = dpToPx(8f)
            }
        })

        content.addView(makeOptionRow(
            iconRes = R.drawable.license_24px,
            label = getString(R.string.view_license),
            descText = getString(R.string.desc_license),
            topRadiusDp = 16f, bottomRadiusDp = 4f, hasBottomMargin = true
        ) {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://github.com/HeheJuice/TangentUI-Sample/blob/main/LICENSE")))
            }
        })

        content.addView(makeOptionRow(
            iconRes = R.drawable.policy_24px,
            label = getString(R.string.view_privacy),
            descText = getString(R.string.desc_privacy),
            topRadiusDp = 4f, bottomRadiusDp = 16f, hasBottomMargin = false
        ) {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://github.com/HeheJuice/TangentUI-Sample/blob/main/Privacy_Policy.txt")))
            }
        })

        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })

        val pillRadius = dpToPx(100f).toFloat()
        val ackContent = OptimizeEngine.roundedBackground(surfaceBrightColor, pillRadius)
        val ackRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = makeRipple(ackContent, pillRadius)
            setPadding(dpToPx(8f), 0, dpToPx(20f), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(56f)).apply {
                topMargin = dpToPx(12f); bottomMargin = dpToPx(12f)
            }
            isClickable = true; isFocusable = true
        }
        val ackRadio = MaterialRadioButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable = false; isFocusable = false
        }
        val ackText = TextView(this).apply {
            text = getString(R.string.welcome_agree_to_terms)
            textSize = 14f
            setTextColor(onSurfaceColor)
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        ackRow.addView(ackRadio)
        ackRow.addView(ackText)
        content.addView(ackRow)

        val contBtn = makeFilledButton(
            label = getString(R.string.welcome_continue),
            heightDp = 56f, topRadiusDp = 100f, bottomRadiusDp = 100f,
            onClick = { switchToPage(3) }
        )
        applyMd3DisabledColors(contBtn)
        contBtn.isEnabled = false
        content.addView(contBtn)
        continueBtn = contBtn

        ackRow.setOnClickListener {
            OptimizeEngine.vibrateTick(this@WelcomeActivity)
            ackRadio.isChecked = !ackRadio.isChecked
        }
        ackRadio.setOnCheckedChangeListener { _, isChecked -> contBtn.isEnabled = isChecked }

        content.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(24f))
        })
    }

    private fun setupPage3() {
        permRowRefs.clear()
        permContinueBtn = null
        permStatusSummary = null
        rootGrantBtn = null

        content.addView(TextView(this).apply {
            text = getString(R.string.welcome_perm_title)
            textSize = 32f
            setTextColor(onPrimaryContainerColor)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8f); bottomMargin = dpToPx(12f)
                marginStart = dpToPx(8f); marginEnd = dpToPx(8f)
            }
        })

        content.addView(TextView(this).apply {
            text = getString(R.string.welcome_perm_desc)
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            alpha = 0.85f
            includeFontPadding = false
            setLineSpacing(dpToPx(3f).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dpToPx(8f); marginEnd = dpToPx(8f)
                bottomMargin = dpToPx(16f)
            }
        })

        content.addView(TextView(this).apply {
            text = getString(R.string.welcome_perm_required_title)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dpToPx(8f), dpToPx(6f), dpToPx(8f), dpToPx(8f))
        })

        val required = PermissionHelper.requiredPermissions()
        val listGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val outerR = dpToPx(16f).toFloat()
        val innerR = dpToPx(4f).toFloat()

        required.forEachIndexed { index, perm ->
            val topR = if (index == 0) outerR else innerR
            val bottomR = if (index == required.size - 1) outerR else innerR
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = OptimizeEngine.groupItemBackground(surfaceBrightColor, topR, bottomR)
                setPadding(dpToPx(20f), dpToPx(14f), dpToPx(20f), dpToPx(14f))
                setMinimumHeight(dpToPx(56f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (index < required.size - 1) bottomMargin = dpToPx(2f)
                }
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = perm.substringAfterLast('.')
                textSize = 16f
                setTextColor(onSurfaceColor)
                setTypeface(typeface, Typeface.BOLD)
                includeFontPadding = false
            })
            textCol.addView(TextView(this).apply {
                text = perm
                textSize = 12f
                setTextColor(onSurfaceVariantColor)
                alpha = 0.7f
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpToPx(2f) }
            })
            row.addView(textCol)
            val statusIcon = ImageView(this).apply {
                setImageDrawable(ContextCompat.getDrawable(
                    this@WelcomeActivity, R.drawable.close_24px))
                setColorFilter(permErrorColor(), android.graphics.PorterDuff.Mode.SRC_ATOP)
                layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                    .apply { marginStart = dpToPx(12f) }
            }
            row.addView(statusIcon)
            listGroup.addView(row)
            permRowRefs.add(PermRowRef(perm, row, statusIcon))
        }
        content.addView(listGroup)

        permStatusSummary = TextView(this).apply {
            text = ""
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(12f)
            }
        }
        content.addView(permStatusSummary)

        // ★ 把下面所有按鈕推到底部：空白都在按鈕上方
        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })

        // Copy ADB command
        val copyAdbBtn = makeOutlinedButton(
            label = getString(R.string.welcome_perm_copy_adb),
            heightDp = 56f,
            onClick = { copyAdbGrantCommands() }
        )
        (copyAdbBtn.layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = dpToPx(8f)
            bottomMargin = dpToPx(8f)
        }
        content.addView(copyAdbBtn)

        // Grant with Root
        val grantRootBtn = makeOutlinedButton(
            label = getString(R.string.welcome_perm_grant_root),
            heightDp = 56f,
            onClick = { performRootGrant() }
        )
        (grantRootBtn.layoutParams as LinearLayout.LayoutParams).apply {
            bottomMargin = dpToPx(8f)
        }
        content.addView(grantRootBtn)
        rootGrantBtn = grantRootBtn

        // Continue — must pass permission check
        val contBtn = makeFilledButton(
            label = getString(R.string.welcome_continue),
            heightDp = 56f, topRadiusDp = 100f, bottomRadiusDp = 100f,
            onClick = {
                if (!PermissionHelper.hasAllPermissions(this)) {
                    OptimizeEngine.vibrateThud(this)
                    Toast.makeText(this,
                        getString(R.string.welcome_perm_denied_toast),
                        Toast.LENGTH_SHORT).show()
                    refreshPermissionStatus()
                    return@makeFilledButton
                }
                if (isSignatureOfficial()) switchToPage(4)
                else switchToPage(5)
            }
        )
        applyMd3DisabledColors(contBtn)
        contBtn.isEnabled = false
        content.addView(contBtn)
        permContinueBtn = contBtn

        content.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(24f))
        })

        refreshPermissionStatus()
    }

    private fun permPrimaryColor(): Int = MaterialColors.getColor(
        this, androidx.appcompat.R.attr.colorPrimary,
        if (isNightMode()) Color.parseColor("#D0BCFF") else Color.parseColor("#6750A4"))

    private fun permErrorColor(): Int = MaterialColors.getColor(
        this, androidx.appcompat.R.attr.colorError,
        Color.parseColor("#B3261E"))

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private fun refreshPermissionStatus() {
        if (permRowRefs.isEmpty()) return
        val okColor = permPrimaryColor()
        val errColor = permErrorColor()
        var allGranted = true
        permRowRefs.forEach { ref ->
            val ok = PermissionHelper.hasPermission(this, ref.permission)
            if (!ok) allGranted = false
            ref.statusIcon.setImageDrawable(ContextCompat.getDrawable(
                this, if (ok) R.drawable.check_24px else R.drawable.close_24px))
            ref.statusIcon.setColorFilter(
                if (ok) okColor else errColor,
                android.graphics.PorterDuff.Mode.SRC_ATOP)
        }
        permStatusSummary?.apply {
            if (allGranted) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = getString(R.string.welcome_perm_still_missing)
                setTextColor(errColor)
            }
        }
        permContinueBtn?.isEnabled = allGranted
    }

    private fun copyAdbGrantCommands() {
        val required = PermissionHelper.requiredPermissions()
        val text = required.joinToString("\n") { perm ->
            PermissionHelper.buildAdbGrantCommand(this, perm)
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("grant_permission_adb_cmd", text))
        Toast.makeText(this,
            getString(R.string.welcome_perm_copied),
            Toast.LENGTH_SHORT).show()
        OptimizeEngine.vibrateTick(this)
    }

    private fun performRootGrant() {
        if (isRootGranting) return

        if (PermissionHelper.hasAllPermissions(this)) {
            Toast.makeText(this,
                getString(R.string.welcome_perm_grant_success),
                Toast.LENGTH_SHORT).show()
            refreshPermissionStatus()
            return
        }

        isRootGranting = true
        val btn = rootGrantBtn
        btn?.isEnabled = false
        btn?.text = getString(R.string.welcome_perm_granting)
        OptimizeEngine.vibrateTick(this)

        Thread {
            val result = PermissionHelper.grantAllWithRoot(this, 20)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                isRootGranting = false
                btn?.isEnabled = true
                btn?.text = getString(R.string.welcome_perm_grant_root)

                mainHandler.postDelayed({
                    if (isFinishing || isDestroyed) return@postDelayed
                    refreshPermissionStatus()
                    if (PermissionHelper.hasAllPermissions(this)) {
                        OptimizeEngine.vibrateThud(this)
                        Toast.makeText(this,
                            getString(R.string.welcome_perm_grant_success),
                            Toast.LENGTH_SHORT).show()
                    } else {
                        OptimizeEngine.vibrateThud(this)
                        val msg = if (result.output.isBlank())
                            getString(R.string.welcome_perm_grant_failed_no_root)
                        else
                            getString(R.string.welcome_perm_grant_failed, result.output)
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }, 300L)
            }
        }.start()
    }

    private fun setupPage5() {
        content.addView(TextView(this).apply {
            text = getString(R.string.welcome_unknown_signature_title)
            textSize = 32f
            setTextColor(onPrimaryContainerColor)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8f); bottomMargin = dpToPx(16f)
                marginStart = dpToPx(8f); marginEnd = dpToPx(8f)
            }
        })

        content.addView(makeMessageCard(
            message = getString(R.string.welcome_unknown_signature_desc)))

        content.addView(makeRepoRow())

        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })

        val pillRadius = dpToPx(100f).toFloat()
        val ackContent = OptimizeEngine.roundedBackground(surfaceBrightColor, pillRadius)
        val ackRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = makeRipple(ackContent, pillRadius)
            setPadding(dpToPx(8f), 0, dpToPx(20f), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(56f)).apply {
                topMargin = dpToPx(12f); bottomMargin = dpToPx(12f)
            }
            isClickable = true; isFocusable = true
        }
        val ackRadio = MaterialRadioButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable = false; isFocusable = false
        }
        val ackText = TextView(this).apply {
            text = getString(R.string.welcome_unknown_signature_acknowledge)
            textSize = 14f
            setTextColor(onSurfaceColor)
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        ackRow.addView(ackRadio)
        ackRow.addView(ackText)
        content.addView(ackRow)

        val continueAnywayBtn = makeFilledButton(
            label = getString(R.string.welcome_continue_anyway),
            heightDp = 56f, topRadiusDp = 100f, bottomRadiusDp = 100f,
            onClick = { switchToPage(4) }
        )
        applyMd3DisabledColors(continueAnywayBtn)
        continueAnywayBtn.isEnabled = false
        content.addView(continueAnywayBtn)

        ackRow.setOnClickListener {
            OptimizeEngine.vibrateTick(this@WelcomeActivity)
            ackRadio.isChecked = !ackRadio.isChecked
        }
        ackRadio.setOnCheckedChangeListener { _, isChecked -> continueAnywayBtn.isEnabled = isChecked }

        content.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(24f))
        })
    }

    private fun setupPage4() {
        if (isUpgrade) {
            page4Loaded = true
            showPage4Content()
            return
        }
        if (page4Loaded) {
            showPage4Content()
            return
        }
        showPage4Loading()
        page4LoadingRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            page4LoadingRunnable = null
            if (currentPage == 4 && !isFinishing && !isDestroyed) {
                page4Loaded = true
                showPage4Content()
            }
        }
        page4LoadingRunnable = runnable
        mainHandler.postDelayed(runnable, PAGE4_LOADING_MS)
    }

    private fun showPage4Loading() {
        content.removeAllViews()
        continueBtn = null
        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })
        content.addView(LoadingIndicator(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setIndicatorColor(onPrimaryContainerColor)
            setContainerColor(primaryContainerColor)
        })
        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })
    }

    private fun showPage4Content() {
        content.removeAllViews()
        content.alpha = 0f

        val titleText = getString(
            if (isUpgrade) R.string.update_success_title else R.string.all_set_up)
        val descText = getString(
            if (isUpgrade) R.string.update_success_desc else R.string.all_set_up_desc)

        val sineTitle = Sine(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8f)
                marginStart = dpToPx(8f); marginEnd = dpToPx(8f)
            }
            textSizeSp = 32f
            setText(titleText, onPrimaryContainerColor)
        }
        content.addView(sineTitle)
        sineTitle.post {
            if (sineTitle.isAttachedToWindow) {
                sineTitle.animateSineWave(
                    durationMs = 750L, staggerMs = 45L, amplitudeDp = 22f,
                    phasePerChar = 0.7f, overshoot = true)
            }
        }

        content.addView(TextView(this).apply {
            text = descText
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            alpha = 0.8f
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8f)
                marginStart = dpToPx(8f); marginEnd = dpToPx(8f)
            }
        })

        if (isUpgrade) {
            val whatsNewRow = makeOptionRow(
                iconRes = R.drawable.explore_24px,
                label = getString(R.string.whats_new_title),
                descText = getString(R.string.whats_new_desc),
                topRadiusDp = 16f, bottomRadiusDp = 16f, hasBottomMargin = false
            ) {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_RELEASES)))
                }
            }
            (whatsNewRow.layoutParams as LinearLayout.LayoutParams).apply {
                topMargin = dpToPx(16f)
            }
            content.addView(whatsNewRow)
        }

        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })

        if (!isUpgrade) {
            content.addView(ImageView(this).apply {
                setImageDrawable(ContextCompat.getDrawable(
                    this@WelcomeActivity, R.drawable.sentiment_excited_24px))
                setColorFilter(onPrimaryContainerColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = getString(R.string.all_set_up_icon_desc)
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(144f), dpToPx(144f)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dpToPx(24f); bottomMargin = dpToPx(24f)
                }
            })
            content.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            })
        }

        continueBtn = makeFilledButton(
            label = getString(R.string.enter_app),
            heightDp = 56f, topRadiusDp = 100f, bottomRadiusDp = 100f,
            onClick = { finishWelcome() }
        )
        content.addView(continueBtn)

        content.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(24f))
        })

        content.animate().alpha(1f).setDuration(400L)
            .setInterpolator(FastOutSlowInInterpolator()).start()
    }

    private fun animateBackButton(targetPage: Int) {
        val currentIconRes =
            if (currentPage == 1) R.drawable.close_24px else R.drawable.arrow_back_24px
        val newIconRes =
            if (targetPage == 1) R.drawable.close_24px else R.drawable.arrow_back_24px
        if (currentIconRes == newIconRes) return

        backBtnIcon.animate().cancel()
        val goingForward = targetPage > currentPage
        val outTranslation = if (goingForward) -dpToPx(30f).toFloat() else dpToPx(30f).toFloat()
        val inTranslation = if (goingForward) dpToPx(30f).toFloat() else -dpToPx(30f).toFloat()
        val duration = 180L

        backBtnIcon.animate().translationX(outTranslation).alpha(0f)
            .setDuration(duration)
            .withEndAction {
                backBtnIcon.setImageResource(newIconRes)
                backBtnIcon.translationX = inTranslation
                backBtnIcon.alpha = 0f
                backBtnIcon.animate().translationX(0f).alpha(1f)
                    .setDuration(duration)
                    .setInterpolator(OvershootInterpolator(1.5f)).start()
            }.start()
    }

    private fun switchToPage(page: Int) {
        if (currentPage == page) return
        if (isPageSwitchAnimating) return
        isPageSwitchAnimating = true

        val duration = PAGE_SWITCH_ANIM_MS
        val curve = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        animateBackButton(page)

        // ★ 最原始的按鈕顯隱控制：第2頁開始就滑出語言，第3頁才滑入刷新
        if (page > 1) {
            languageBtn.animate().translationX(dpToPx(100f).toFloat()).alpha(0f)
                .setDuration(duration).setInterpolator(curve)
                .withEndAction { languageBtn.visibility = View.GONE }.start()
        } else {
            languageBtn.visibility = View.VISIBLE
            languageBtn.translationX = dpToPx(100f).toFloat()
            languageBtn.alpha = 0f
            languageBtn.animate().translationX(0f).alpha(1f)
                .setDuration(duration).setInterpolator(curve).start()
        }

        if (page == 3) {
            refreshBtn.visibility = View.VISIBLE
            refreshBtn.translationX = dpToPx(100f).toFloat()
            refreshBtn.alpha = 0f
            refreshBtn.animate().translationX(0f).alpha(1f)
                .setDuration(duration).setInterpolator(curve).start()
        } else {
            refreshBtn.animate().translationX(dpToPx(100f).toFloat()).alpha(0f)
                .setDuration(duration).setInterpolator(curve)
                .withEndAction { refreshBtn.visibility = View.GONE }.start()
        }

        content.animate().alpha(0f).scaleX(0.95f).scaleY(0.95f)
            .setDuration(duration).setInterpolator(curve)
            .withEndAction {
                showPage(page, animate = false)
                isPageSwitchAnimating = false
                content.alpha = 0f; content.scaleX = 0.95f; content.scaleY = 0.95f
                content.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(duration).setInterpolator(curve).start()
            }.start()
    }

    private fun makeRepoRow(): LinearLayout {
        val radius = dpToPx(16f).toFloat()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = makeRowRipple(surfaceBrightColor, radius, radius)
            setPadding(dpToPx(20f), dpToPx(16f), dpToPx(20f), dpToPx(16f))
            setMinimumHeight(dpToPx(64f))
            isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpToPx(12f) }
            setOnClickListener {
                OptimizeEngine.vibrateTick(this@WelcomeActivity)
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_HTML))) }
                catch (_: Exception) { }
            }
        }
        row.addView(ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@WelcomeActivity, R.drawable.code_24px))
            setColorFilter(onSurfaceVariantColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
            layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                .apply { marginEnd = dpToPx(16f) }
        })
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = getString(R.string.view_source_code)
            textSize = 16f
            setTextColor(onSurfaceColor)
            setTypeface(typeface, Typeface.BOLD)
        })
        textCol.addView(TextView(this).apply {
            text = getString(R.string.desc_source)
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            alpha = 0.7f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpToPx(2f) }
        })
        row.addView(textCol)
        row.addView(ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@WelcomeActivity, R.drawable.arrow_forward_24px))
            setColorFilter(onSurfaceVariantColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
            layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                .apply { marginStart = dpToPx(8f) }
        })
        return row
    }

    private fun makeMessageCard(message: String): LinearLayout {
        val radius = dpToPx(16f).toFloat()
        val contentBg = OptimizeEngine.roundedBackground(surfaceBrightColor, radius)
        val rippleBg = makeRipple(contentBg, radius)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = rippleBg
            setPadding(dpToPx(20f), dpToPx(18f), dpToPx(20f), dpToPx(18f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            setMinimumHeight(dpToPx(64f))
            isClickable = true; isFocusable = true
            setOnClickListener { OptimizeEngine.vibrateTick(this@WelcomeActivity) }
        }
        row.addView(TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(onSurfaceColor)
            setLineSpacing(dpToPx(6f).toFloat(), 1f)
            includeFontPadding = false
        })
        return row
    }

    private fun makeOptionRow(
        iconRes: Int, label: String, descText: String,
        topRadiusDp: Float, bottomRadiusDp: Float, hasBottomMargin: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        val topR = dpToPx(topRadiusDp).toFloat()
        val bottomR = dpToPx(bottomRadiusDp).toFloat()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = makeRowRipple(surfaceBrightColor, topR, bottomR)
            setPadding(dpToPx(20f), dpToPx(16f), dpToPx(20f), dpToPx(16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (hasBottomMargin) bottomMargin = dpToPx(2f)
            }
            setMinimumHeight(dpToPx(64f))
            isClickable = true; isFocusable = true
            setOnClickListener {
                OptimizeEngine.vibrateTick(this@WelcomeActivity)
                onClick()
            }
        }
        row.addView(ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@WelcomeActivity, iconRes))
            setColorFilter(onSurfaceVariantColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
            layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                .apply { marginEnd = dpToPx(16f) }
        })
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(onSurfaceColor)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        })
        textCol.addView(TextView(this).apply {
            text = descText
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            alpha = 0.7f
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpToPx(2f) }
        })
        row.addView(textCol)
        row.addView(ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@WelcomeActivity, R.drawable.arrow_forward_24px))
            setColorFilter(onSurfaceVariantColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
            layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                .apply { marginStart = dpToPx(8f) }
        })
        return row
    }

    private fun makeFilledButton(
        label: String, heightDp: Float,
        topRadiusDp: Float = 100f, bottomRadiusDp: Float = 100f,
        onClick: () -> Unit
    ): MaterialButton {
        return MaterialButton(ContextThemeWrapper(
            this, com.google.android.material.R.style.Widget_Material3_Button_UnelevatedButton)
        ).apply {
            text = label
            setTextColor(onPrimaryContainerColor)
            backgroundTintList = ColorStateList.valueOf(primaryContainerColor)
            textSize = 16f
            gravity = Gravity.CENTER
            insetTop = 0; insetBottom = 0
            minWidth = 0; minimumWidth = 0
            val hPad = dpToPx(24f)
            setPadding(hPad, 0, hPad, 0)
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(dpToPx(topRadiusDp).toFloat())
                .setTopRightCornerSize(dpToPx(topRadiusDp).toFloat())
                .setBottomLeftCornerSize(dpToPx(bottomRadiusDp).toFloat())
                .setBottomRightCornerSize(dpToPx(bottomRadiusDp).toFloat())
                .build()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
            setOnClickListener {
                OptimizeEngine.vibrateTick(this@WelcomeActivity)
                onClick()
            }
        }
    }

    private fun makeOutlinedButton(
        label: String, heightDp: Float,
        topRadiusDp: Float = 100f, bottomRadiusDp: Float = 100f,
        onClick: () -> Unit
    ): MaterialButton {
        val monetBorderColor = MaterialColors.getColor(
            this, androidx.appcompat.R.attr.colorPrimary, Color.parseColor("#6750A4"))
        return MaterialButton(ContextThemeWrapper(
            this, com.google.android.material.R.style.Widget_Material3_Button_OutlinedButton)
        ).apply {
            text = label
            setTextColor(monetBorderColor)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            strokeColor = ColorStateList.valueOf(monetBorderColor)
            strokeWidth = dpToPx(1f)
            textSize = 16f
            gravity = Gravity.CENTER
            insetTop = 0; insetBottom = 0
            minWidth = 0; minimumWidth = 0
            val hPad = dpToPx(24f)
            setPadding(hPad, 0, hPad, 0)
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(dpToPx(topRadiusDp).toFloat())
                .setTopRightCornerSize(dpToPx(topRadiusDp).toFloat())
                .setBottomLeftCornerSize(dpToPx(bottomRadiusDp).toFloat())
                .setBottomRightCornerSize(dpToPx(bottomRadiusDp).toFloat())
                .build()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
            setOnClickListener {
                OptimizeEngine.vibrateTick(this@WelcomeActivity)
                onClick()
            }
        }
    }

    private fun updateScrollDownButtonVisibility() {
        val btn = scrollDownBtn ?: return
        val canScroll = scrollView.canScrollVertically(1)
        if (canScroll) {
            if (btn.visibility == View.GONE) {
                btn.visibility = View.VISIBLE
                btn.animate().translationX(0f).alpha(1f).setDuration(450).start()
            }
        } else {
            if (btn.visibility == View.VISIBLE) {
                btn.animate().translationX(dpToPx(100f).toFloat()).alpha(0f)
                    .setDuration(450)
                    .withEndAction { btn.visibility = View.GONE }.start()
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        for (t in feedbackTargets) t.handleEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        page4LoadingRunnable?.let { mainHandler.removeCallbacks(it) }
        page4LoadingRunnable = null
        mainHandler.removeCallbacksAndMessages(null)
        isRootGranting = false
        feedbackTargets.forEach { it.destroy() }
        feedbackTargets.clear()
        super.onDestroy()
    }

    private fun finishWelcome() {
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WELCOME_LAUNCHED, true)
            .putString(KEY_WELCOME_VERSION, getAppVersionName())
            .apply()
        try {
            startActivity(Intent(this, TangentHome::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
        } catch (_: Exception) { }
    }

    private data class LangOption(val tag: String, val labelRes: Int)

    private fun getLangOptions(): List<LangOption> = listOf(
        LangOption("",       R.string.lang_follow_system),
        LangOption("en",     R.string.lang_english),
        LangOption("ja",     R.string.lang_japanese),
        LangOption("ko",     R.string.lang_korean),
        LangOption("zh-HK",  R.string.lang_zh_hk),
        LangOption("zh-TW",  R.string.lang_zh_tw),
        LangOption("zh-CN",  R.string.lang_zh_cn),
        LangOption("zh-ZZ",  R.string.lang_zh_fake),
        LangOption("yue",    R.string.lang_yue),
        LangOption("yue-CN", R.string.lang_yue_cn)
    )

    private fun animateFadeToBlack(onEnd: () -> Unit) {
        val overlayView = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            isClickable = true
        }
        root.addView(overlayView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))
        overlayView.bringToFront()

        val totalDuration = 500L
        val fadeCurve = FastOutSlowInInterpolator()
        cardContainer.pivotX = cardContainer.width / 2f
        cardContainer.pivotY = animWrapper.height / 2f
        cardContainer.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f)
            .setDuration(totalDuration).setInterpolator(fadeCurve).start()
        overlayView.animate().alpha(1f).setStartDelay(200L)
            .setDuration(totalDuration - 200L).setInterpolator(fadeCurve)
            .withEndAction { onEnd() }.start()
    }

    private fun showLanguagePickerDialog() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val surfaceHigh = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            if (isDark) Color.parseColor("#2B2930") else Color.parseColor("#ECE6F0"))
        val rowBg = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurfaceBright,
            if (isDark) Color.parseColor("#3B3B3E") else Color.parseColor("#FFFFFF"))
        val onSurface = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurface,
            if (isDark) Color.parseColor("#E6E1E5") else Color.parseColor("#1C1B1F"))
        val onSurfaceVariant = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            if (isDark) Color.parseColor("#CAC4D0") else Color.parseColor("#49454F"))
        val primary = MaterialColors.getColor(this,
            androidx.appcompat.R.attr.colorPrimary,
            if (isDark) Color.parseColor("#D0BCFF") else Color.parseColor("#6750A4"))
        val primaryContainer = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorPrimaryContainer,
            Color.parseColor("#E8DEF8"))
        val onPrimaryContainer = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            if (isDark) Color.parseColor("#EADDFF") else Color.parseColor("#4F378B"))

        val options = getLangOptions()
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentTag = if (currentLocales.isEmpty) ""
                         else currentLocales.toLanguageTags().substringBefore(",").replace('_', '-')

        var selectedIndex = options.indexOfFirst { it.tag.equals(currentTag, ignoreCase = true) }
        if (selectedIndex < 0) selectedIndex = options.indexOfFirst {
            it.tag.isNotEmpty() && currentTag.startsWith(it.tag, ignoreCase = true)
        }
        if (selectedIndex < 0) selectedIndex = 0

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(28f).toFloat()
                setColor(surfaceHigh)
            }
            clipToOutline = true
        }

        card.addView(TextView(this).apply {
            text = getString(R.string.language_picker_title)
            textSize = 22f
            setTextColor(onSurface)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(4f))
        })

        card.addView(TextView(this).apply {
            text = getString(R.string.language_picker_subtitle)
            textSize = 14f
            setTextColor(onSurfaceVariant)
            setPadding(dpToPx(24f), 0, dpToPx(24f), dpToPx(12f))
        })

        val outerRadiusPx = dpToPx(16f).toFloat()
        val innerRadiusPx = dpToPx(4f).toFloat()
        val rowHeightPx = dpToPx(56f)
        val rowGapPx = dpToPx(2f)
        val listHorizontalPadPx = dpToPx(16f)
        val total = options.size

        val screenHeightPx = resources.displayMetrics.heightPixels
        val maxDialogHeightPx = (screenHeightPx * 0.85f).toInt()
        val reservedPx = dpToPx(210f)
        val maxListHeightPx = (maxDialogHeightPx - reservedPx).coerceAtLeast(dpToPx(120f))
        val listHeightPx = minOf(
            total * (rowHeightPx + rowGapPx) + dpToPx(4f) * 2, maxListHeightPx)

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, listHeightPx)
        }
        val listColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(listHorizontalPadPx, dpToPx(4f), listHorizontalPadPx, dpToPx(4f))
        }
        scroll.addView(listColumn)

        data class RowRef(val container: LinearLayout, val label: TextView,
                          val check: ImageView, val topR: Float, val botR: Float)
        val rowRefs = mutableListOf<RowRef>()

        fun makeRowBg(color: Int, topR: Float, botR: Float) = GradientDrawable().apply {
            cornerRadii = floatArrayOf(topR, topR, topR, topR, botR, botR, botR, botR)
            setColor(color)
        }

        fun updateSelection() {
            rowRefs.forEachIndexed { idx, ref ->
                val isSelected = idx == selectedIndex
                val contentBg = makeRowBg(
                    if (isSelected) primaryContainer else rowBg, ref.topR, ref.botR)
                val mask = makeRowBg(Color.WHITE, ref.topR, ref.botR)
                val rippleColor = ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(onSurfaceVariant, 40))
                ref.container.background = RippleDrawable(rippleColor, contentBg, mask)
                ref.label.setTextColor(if (isSelected) onPrimaryContainer else onSurface)
                ref.check.visibility = if (isSelected) View.VISIBLE else View.GONE
            }
        }

        options.forEachIndexed { index, option ->
            val (topR, bottomR) = when {
                total == 1 -> outerRadiusPx to outerRadiusPx
                index == 0 -> outerRadiusPx to innerRadiusPx
                index == total - 1 -> innerRadiusPx to outerRadiusPx
                else -> innerRadiusPx to innerRadiusPx
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true; isFocusable = true
                setPadding(dpToPx(20f), 0, dpToPx(20f), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, rowHeightPx
                ).apply { if (index < total - 1) bottomMargin = rowGapPx }
                setOnClickListener {
                    selectedIndex = index
                    OptimizeEngine.vibrateTick(this@WelcomeActivity)
                    updateSelection()
                }
            }
            val label = TextView(this).apply {
                text = getString(option.labelRes)
                textSize = 16f
                setTextColor(onSurface)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(label)
            val check = ImageView(this).apply {
                setImageDrawable(ContextCompat.getDrawable(this@WelcomeActivity, R.drawable.check_24px))
                setColorFilter(onPrimaryContainer, android.graphics.PorterDuff.Mode.SRC_ATOP)
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                    .apply { marginStart = dpToPx(12f) }
            }
            row.addView(check)
            listColumn.addView(row)
            rowRefs.add(RowRef(row, label, check, topR, bottomR))
        }

        updateSelection()
        card.addView(scroll)

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
        }
        val rippleColor = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(onSurfaceVariant, 40))

        val cancelBtn = TextView(this).apply {
            text = getString(R.string.tangent_cancel)
            textSize = 14f
            setTextColor(primary)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dpToPx(20f), dpToPx(12f), dpToPx(20f), dpToPx(12f))
            isClickable = true; isFocusable = true
            background = RippleDrawable(rippleColor, GradientDrawable().apply {
                cornerRadius = dpToPx(100f).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(1f), primary)
            }, GradientDrawable().apply {
                cornerRadius = dpToPx(100f).toFloat()
                setColor(Color.WHITE)
            })
            setOnClickListener {
                OptimizeEngine.vibrateTick(this@WelcomeActivity)
                dialog.dismiss()
            }
        }
        val selectBtn = TextView(this).apply {
            text = getString(R.string.action_select)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dpToPx(20f), dpToPx(12f), dpToPx(20f), dpToPx(12f))
            isClickable = true; isFocusable = true
            background = RippleDrawable(rippleColor, GradientDrawable().apply {
                cornerRadius = dpToPx(100f).toFloat()
                setColor(primaryContainer)
            }, GradientDrawable().apply {
                cornerRadius = dpToPx(100f).toFloat()
                setColor(Color.WHITE)
            })
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dpToPx(8f) }
            setOnClickListener {
                OptimizeEngine.vibrateThud(this@WelcomeActivity)
                val selectedTag = options[selectedIndex].tag
                val curr = AppCompatDelegate.getApplicationLocales()
                val currTag = if (curr.isEmpty) ""
                              else curr.toLanguageTags().substringBefore(",").replace('_', '-')
                if (selectedTag.equals(currTag, ignoreCase = true)) {
                    dialog.dismiss(); return@setOnClickListener
                }
                dialog.dismiss()
                getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_USER_LANGUAGE_TAG, selectedTag)
                    .putBoolean(KEY_PENDING_LANG_FADE, true)
                    .apply()
                animateFadeToBlack {
                    if (selectedTag.isEmpty()) {
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.getEmptyLocaleList())
                    } else {
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(selectedTag))
                    }
                }
            }
        }

        bottomBar.addView(cancelBtn)
        bottomBar.addView(selectBtn)
        card.addView(bottomBar)

        dialog.setContentView(card)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        val dialogWidth = (resources.displayMetrics.widthPixels - dpToPx(48f))
            .coerceAtMost(dpToPx(420f))
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.5f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }
}