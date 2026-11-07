package com.HeheJuice.CrashLogs.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import com.HeheJuice.CrashLogs.R
import com.HeheJuice.CrashLogs.ui.HomePageContent
import com.HeheJuice.CrashLogs.ui.SettingsPageContent
import com.HeheJuice.CrashLogs.ui.UpdatePageContent
import com.HeheJuice.CrashLogs.util.MonetColorManager
import com.HeheJuice.CrashLogs.util.OptimizeEngine
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class TangentHome : AppCompatActivity() {

    enum class AppTab { HOME, SETTINGS, UPDATER }
    private var activeTab = AppTab.HOME

    private lateinit var slidingPillView: View
    private lateinit var homeTabBtn: TextView
    private lateinit var settingsTabBtn: TextView
    private lateinit var updaterTabBtn: TextView
    private lateinit var homeContainer: FrameLayout
    private lateinit var settingsContainer: FrameLayout
    private lateinit var updaterContainer: FrameLayout
    private lateinit var contentHolder: FrameLayout
    private lateinit var bottomBarLayout: LinearLayout
    private lateinit var tabPillContainer: FrameLayout
    private lateinit var rootFrame: FrameLayout

    private lateinit var refreshBtnRef: FrameLayout

    private lateinit var homePage: HomePageContent
    private lateinit var settingsPage: SettingsPageContent
    private lateinit var updaterPageContent: UpdatePageContent

    private var primaryContainerColor: Int = 0
    private var onPrimaryContainerColor: Int = 0
    private var surfaceContainerColor: Int = 0
    private var surfaceContainerLowColor: Int = 0
    private var surfaceBrightColor: Int = 0
    private var onSurfaceColor: Int = 0
    private var onSurfaceVariantColor: Int = 0
    private var primaryColor: Int = 0

    private var currentTopInset: Int = 0

    private val feedbackTargets = mutableListOf<OptimizeEngine.FeedbackTarget>()

    private fun dpToPx(dp: Float): Int = OptimizeEngine.dpToPx(dp)
    private fun dpToPx(dp: Int): Int = OptimizeEngine.dpToPx(dp)

    private fun getAppVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
    } catch (_: Exception) { "1.0.0" }

    private fun isWelcomeLaunched(): Boolean {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_WELCOME_LAUNCHED, false)) return false
        return prefs.getString(KEY_WELCOME_VERSION, null) == getAppVersionName()
    }

    private var welcomeLaunchGuard = false

    // ★ 完美还原参考代码的同步启动逻辑，不搞任何延迟
    private fun ensureWelcomeShown() {
        if (welcomeLaunchGuard) return
        if (isWelcomeLaunched()) return

        welcomeLaunchGuard = true

        try {
            startActivity(Intent(this, WelcomeActivity::class.java))
        } catch (_: Exception) {
        } finally {
            Handler(Looper.getMainLooper()).postDelayed({
                welcomeLaunchGuard = false
            }, 500L)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        for (t in feedbackTargets) t.handleEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            activeTab = savedInstanceState.getString("active_tab")
                ?.let { AppTab.valueOf(it) } ?: AppTab.HOME
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        // ★ 核心修复：在最顶层同步拉起欢迎页，绝不闪主页
        ensureWelcomeShown()

        MonetColorManager.applyToActivity(this)

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }

        primaryContainerColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorPrimaryContainer, Color.parseColor("#E8DEF8"))
        onPrimaryContainerColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnPrimaryContainer, Color.parseColor("#4F378B"))
        surfaceContainerColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurfaceContainer,
            if (isDark) Color.parseColor("#1C1B1F") else Color.parseColor("#FEF7FF"))
        surfaceContainerLowColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurfaceContainerLow,
            if (isDark) Color.parseColor("#1D1B20") else Color.parseColor("#F7F2FA"))
        surfaceBrightColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurfaceBright,
            if (isDark) Color.parseColor("#3B3B3E") else Color.parseColor("#FFFFFF"))
        onSurfaceColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurface,
            if (isDark) Color.parseColor("#E6E1E5") else Color.parseColor("#1C1B1F"))
        onSurfaceVariantColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.parseColor("#49454F"))
        primaryColor = MaterialColors.getColor(this,
            androidx.appcompat.R.attr.colorPrimary,
            if (isDark) Color.parseColor("#D0BCFF") else Color.parseColor("#6750A4"))

        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(surfaceContainerColor)
            clipChildren = false; clipToPadding = false
        }

        homeContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            visibility = if (activeTab == AppTab.HOME) View.VISIBLE else View.GONE
        }
        homePage = HomePageContent(this)

        val homeScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; clipToPadding = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(dpToPx(16f), dpToPx(64f), dpToPx(16f), dpToPx(100f))
        }
        homeScroll.addView(homePage.build())
        homeContainer.addView(homeScroll)

        settingsContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            visibility = if (activeTab == AppTab.SETTINGS) View.VISIBLE else View.GONE
        }
        settingsPage = SettingsPageContent(this)
        val settingsScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; clipToPadding = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(dpToPx(16f), dpToPx(64f), dpToPx(16f), dpToPx(100f))
        }
        settingsScroll.addView(settingsPage.build())
        settingsContainer.addView(settingsScroll)

        updaterContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            visibility = if (activeTab == AppTab.UPDATER) View.VISIBLE else View.GONE
        }
        val updaterScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; clipToPadding = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(dpToPx(16f), dpToPx(64f), dpToPx(16f), dpToPx(100f))
        }
        updaterPageContent = UpdatePageContent(this@TangentHome)
        updaterScroll.addView(updaterPageContent.build())
        updaterContainer.addView(updaterScroll)

        contentHolder = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            clipChildren = false; clipToPadding = false
            addView(homeContainer); addView(settingsContainer); addView(updaterContainer)
        }
        rootFrame.addView(contentHolder)

        bottomBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            clipChildren = false; clipToPadding = false
        }

        tabPillContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(100f).toFloat()
                setColor(surfaceContainerLowColor)
            }
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(4f), dpToPx(4f))
            clipChildren = false; clipToPadding = false
        }

        slidingPillView = View(this).apply {
            background = GradientDrawable().apply {
                setColor(primaryContainerColor)
                cornerRadius = dpToPx(100f).toFloat()
            }
            layoutParams = FrameLayout.LayoutParams(0, dpToPx(44f), Gravity.CENTER_VERTICAL)
        }

        val tabButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val homeDrawable = try { ContextCompat.getDrawable(this, R.drawable.interests_24px) } catch (_: Exception) { null }
        val settingsDrawable = try { ContextCompat.getDrawable(this, R.drawable.settings_24px) } catch (_: Exception) { null }
        val updaterDrawable = try { ContextCompat.getDrawable(this, R.drawable.info_24px) } catch (_: Exception) { null }

        fun makeTabBtn(drawable: Drawable?, labelRes: Int, tab: AppTab): TextView =
            TextView(this).apply {
                text = if (activeTab == tab) getString(labelRes) else ""
                textSize = 14f
                setTextColor(if (activeTab == tab) onPrimaryContainerColor else onSurfaceVariantColor)
                gravity = Gravity.CENTER
                setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
                compoundDrawablePadding = if (activeTab == tab) dpToPx(8f) else 0
                setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(44f))
                isClickable = true; isFocusable = true
                setOnTouchListener(OptimizeEngine.pressScaleTouchListener)
                includeFontPadding = false
            }

        homeTabBtn = makeTabBtn(homeDrawable, R.string.nav_home, AppTab.HOME)
        settingsTabBtn = makeTabBtn(settingsDrawable, R.string.nav_settings, AppTab.SETTINGS)
        updaterTabBtn = makeTabBtn(updaterDrawable, R.string.nav_updater, AppTab.UPDATER)
        
        tabButtonsLayout.addView(homeTabBtn)
        tabButtonsLayout.addView(settingsTabBtn)
        tabButtonsLayout.addView(updaterTabBtn)
        tabPillContainer.addView(slidingPillView)
        tabPillContainer.addView(tabButtonsLayout)

        refreshBtnRef = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(surfaceContainerLowColor)
            }
            clipChildren = false
            clipToPadding = false
            isClickable = true
            isFocusable = true
            contentDescription = "Refresh Logs"
            visibility = if (activeTab == AppTab.HOME) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(dpToPx(52f), dpToPx(52f)).apply {
                marginStart = dpToPx(12f)
            }
            setOnClickListener {
                OptimizeEngine.vibrateTick(this@TangentHome)
                if (::homePage.isInitialized) {
                    homePage.forceRefresh()
                }
            }
        }

        val refreshIcon = ImageView(this).apply {
            val d = ContextCompat.getDrawable(this@TangentHome, R.drawable.refresh_24px)?.mutate()
            d?.setTint(onSurfaceVariantColor)
            setImageDrawable(d)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dpToPx(14f)
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }
        refreshBtnRef.addView(refreshIcon)

        bottomBarLayout.addView(tabPillContainer)
        bottomBarLayout.addView(refreshBtnRef)
        rootFrame.addView(bottomBarLayout)
        feedbackTargets += OptimizeEngine.FeedbackTarget(bottomBarLayout)

        setContentView(rootFrame)

        val fadePrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (fadePrefs.getBoolean(KEY_PENDING_LANG_FADE, false)) {
            fadePrefs.edit().putBoolean(KEY_PENDING_LANG_FADE, false).apply()
            playRestartFadeInAnimation()
        }

        val tintDrawableColor: (TextView, Int) -> Unit = { tv, color ->
            val d = tv.compoundDrawables[0]?.let {
                val w = DrawableCompat.wrap(it).mutate(); DrawableCompat.setTint(w, color); w
            }
            tv.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null)
        }
        tintDrawableColor(homeTabBtn, if (activeTab == AppTab.HOME) onPrimaryContainerColor else onSurfaceVariantColor)
        tintDrawableColor(settingsTabBtn, if (activeTab == AppTab.SETTINGS) onPrimaryContainerColor else onSurfaceVariantColor)
        tintDrawableColor(updaterTabBtn, if (activeTab == AppTab.UPDATER) onPrimaryContainerColor else onSurfaceVariantColor)

        val tabTransitionInterpolator = android.view.animation.AnimationUtils.loadInterpolator(
            this, android.R.interpolator.fast_out_extra_slow_in)
        var isTabSwitchAnimating = false

        fun switchTab(targetTab: AppTab) {
            if (activeTab == targetTab || isTabSwitchAnimating) return
            isTabSwitchAnimating = true
            val oldTab = activeTab
            activeTab = targetTab

            TransitionManager.beginDelayedTransition(bottomBarLayout, TransitionSet().apply {
                addTransition(ChangeBounds()); addTransition(Fade())
                duration = 250L; interpolator = OvershootInterpolator(1.0f)
            })

            fun updateTabStyle(btn: TextView, tab: AppTab, isTarget: Boolean) {
                btn.text = if (isTarget) when (tab) {
                    AppTab.HOME -> getString(R.string.nav_home)
                    AppTab.SETTINGS -> getString(R.string.nav_settings)
                    AppTab.UPDATER -> getString(R.string.nav_updater)
                } else ""
                btn.compoundDrawablePadding = if (isTarget) dpToPx(8f) else 0
                btn.setTextColor(if (isTarget) onPrimaryContainerColor else onSurfaceVariantColor)
                tintDrawableColor(btn, if (isTarget) onPrimaryContainerColor else onSurfaceVariantColor)
            }
            updateTabStyle(homeTabBtn, AppTab.HOME, targetTab == AppTab.HOME)
            updateTabStyle(settingsTabBtn, AppTab.SETTINGS, targetTab == AppTab.SETTINGS)
            updateTabStyle(updaterTabBtn, AppTab.UPDATER, targetTab == AppTab.UPDATER)

            val refreshLp = refreshBtnRef.layoutParams as LinearLayout.LayoutParams
            if (targetTab == AppTab.HOME) {
                refreshLp.width = dpToPx(52f)
                refreshLp.marginStart = dpToPx(12f)
                refreshBtnRef.alpha = 1f
                refreshBtnRef.isEnabled = true
                refreshBtnRef.visibility = View.VISIBLE
            } else {
                refreshLp.width = 0
                refreshLp.marginStart = 0
                refreshBtnRef.alpha = 0f
                refreshBtnRef.isEnabled = false
                refreshBtnRef.visibility = View.GONE
            }
            refreshBtnRef.layoutParams = refreshLp

            updatePillPosition()

            val incoming = when (targetTab) {
                AppTab.HOME -> homeContainer
                AppTab.SETTINGS -> settingsContainer
                AppTab.UPDATER -> updaterContainer
            }
            val outgoing = when (oldTab) {
                AppTab.HOME -> homeContainer
                AppTab.SETTINGS -> settingsContainer
                AppTab.UPDATER -> updaterContainer
            }
            
            val dist = contentHolder.width.takeIf { it > 0 }?.toFloat()
                ?: resources.displayMetrics.widthPixels.toFloat()
            val movingRight = targetTab.ordinal > oldTab.ordinal
            val startX = if (movingRight) dist else -dist

            incoming.animate().cancel(); outgoing.animate().cancel()
            incoming.visibility = View.VISIBLE; incoming.alpha = 1f; incoming.translationX = startX
            outgoing.alpha = 1f; outgoing.translationX = 0f
            contentHolder.post {
                val dur = 450L
                incoming.animate().translationX(0f).setDuration(dur)
                    .setInterpolator(tabTransitionInterpolator).withLayer().start()
                outgoing.animate().translationX(-startX).setDuration(dur)
                    .setInterpolator(tabTransitionInterpolator).withLayer()
                    .withEndAction {
                        outgoing.visibility = View.GONE
                        outgoing.translationX = 0f
                        incoming.translationX = 0f
                        isTabSwitchAnimating = false
                    }.start()
            }
        }

        homeTabBtn.setOnClickListener { OptimizeEngine.vibrateThud(this); switchTab(AppTab.HOME) }
        settingsTabBtn.setOnClickListener { OptimizeEngine.vibrateThud(this); switchTab(AppTab.SETTINGS) }
        updaterTabBtn.setOnClickListener { OptimizeEngine.vibrateThud(this); switchTab(AppTab.UPDATER) }

        rootFrame.setOnApplyWindowInsetsListener { _, insets ->
            val statusTop = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top
            // ★ 修复点：拼写错误 Window.Type -> WindowInsets.Type
            val navBottom = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            currentTopInset = statusTop
            contentHolder.setPadding(0, currentTopInset, 0, 0)
            (bottomBarLayout.layoutParams as FrameLayout.LayoutParams).bottomMargin =
                dpToPx(16f) + navBottom
            insets
        }
        rootFrame.post { updatePillPosition() }

        lifecycleScope.launch {
            (application as EngineStart).monetVersion.drop(1).collect {
                if (!isFinishing && !isDestroyed) recreate()
            }
        }
    }

    // ★ 核心修复：切后台再回来时，强制检查向导
    override fun onPostResume() {
        super.onPostResume()
        ensureWelcomeShown()

        if (::homePage.isInitialized) {
            homePage.refreshStatusIcons()
        }
        if (::settingsPage.isInitialized) settingsPage.refreshStatusIcons()
        if (::updaterPageContent.isInitialized) updaterPageContent.refreshIfNeeded()
    }

    // ★ 核心修复：singleTask 返回时，强制检查向导
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        ensureWelcomeShown()
    }

    override fun onDestroy() {
        feedbackTargets.forEach { it.destroy() }
        feedbackTargets.clear()
        if (::updaterPageContent.isInitialized) updaterPageContent.destroy()
        if (::homePage.isInitialized) homePage.destroy()
        if (::settingsPage.isInitialized) settingsPage.destroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("active_tab", activeTab.name)
    }

    fun animateFadeToBlack(onEnd: () -> Unit) {
        val overlayView = View(this).apply {
            setBackgroundColor(Color.BLACK); alpha = 0f; isClickable = true
        }
        rootFrame.addView(overlayView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlayView.bringToFront()

        val totalDuration = 500L
        val fadeCurve = FastOutSlowInInterpolator()
        contentHolder.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f)
            .setDuration(totalDuration).setInterpolator(fadeCurve).start()
        bottomBarLayout.animate().translationY(dpToPx(150f).toFloat())
            .scaleX(0.6f).scaleY(0.6f)
            .setDuration(totalDuration)
            .setInterpolator(android.view.animation.AnticipateInterpolator(4f)).start()
        overlayView.animate().alpha(1f).setStartDelay(200L)
            .setDuration(totalDuration - 200L).setInterpolator(fadeCurve)
            .withEndAction { onEnd() }.start()
    }

    private fun playRestartFadeInAnimation() {
        val enterInterpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        contentHolder.alpha = 0f; contentHolder.scaleX = 0.85f; contentHolder.scaleY = 0.85f
        contentHolder.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(1700L).setInterpolator(enterInterpolator).start()
        bottomBarLayout.translationY = dpToPx(250f).toFloat()
        bottomBarLayout.animate().translationY(0f)
            .setDuration(1200L).setInterpolator(OvershootInterpolator(1.4f)).start()
    }

    private fun updatePillPosition() {
        if (!::slidingPillView.isInitialized) return
        val specW = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val specH = View.MeasureSpec.makeMeasureSpec(dpToPx(44f), View.MeasureSpec.EXACTLY)
        val tabs = arrayOf(homeTabBtn, settingsTabBtn, updaterTabBtn)
        var targetW = 0; var targetX = 0; var accX = 0
        tabs.forEachIndexed { idx, btn ->
            btn.measure(specW, specH)
            val w = if (btn.visibility == View.GONE) 0 else btn.measuredWidth
            if (idx == activeTab.ordinal) { targetW = w; targetX = accX }
            accX += w
        }
        val lp = slidingPillView.layoutParams as FrameLayout.LayoutParams
        if (lp.width != targetW || lp.leftMargin != targetX) {
            lp.width = targetW; lp.leftMargin = targetX; slidingPillView.layoutParams = lp
        }
    }

    companion object {
        private const val KEY_PENDING_LANG_FADE = "pending_language_fade_in"
        private const val KEY_WELCOME_LAUNCHED = "welcome_launched"
        private const val KEY_WELCOME_VERSION = "welcome_version"
    }
}