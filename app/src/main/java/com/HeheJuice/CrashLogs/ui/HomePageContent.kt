package com.HeheJuice.CrashLogs.ui

import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.DropBoxManager
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.HeheJuice.CrashLogs.R
import com.HeheJuice.CrashLogs.app.TangentLogsDetail
import com.HeheJuice.CrashLogs.util.OptimizeEngine
import com.HeheJuice.CrashLogs.util.PermissionHelper
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class HomePageContent(private val activity: AppCompatActivity) {

    private var surfaceContainerColor: Int = 0
    private var surfaceBrightColor: Int = 0
    private var surfaceContainerLowColor: Int = 0
    private var onSurfaceColor: Int = 0
    private var onSurfaceVariantColor: Int = 0
    private var primaryContainerColor: Int = 0
    private var onPrimaryContainerColor: Int = 0
    private var primaryColor: Int = 0

    private fun dpToPx(dp: Float): Int = OptimizeEngine.dpToPx(dp)
    private fun dpToPx(dp: Int): Int = OptimizeEngine.dpToPx(dp)

    private val MP = LinearLayout.LayoutParams.MATCH_PARENT
    private val WC = LinearLayout.LayoutParams.WRAP_CONTENT

    private val ioExecutor = Executors.newSingleThreadExecutor()
    
    private lateinit var logsWrapper: FrameLayout
    private lateinit var currentLogsContainer: LinearLayout
    
    private lateinit var chipsScroll: HorizontalScrollView
    private lateinit var chipsFrame: FrameLayout
    private lateinit var slidingPill: View
    private lateinit var chipsContainer: LinearLayout
    
    private var isLoading = false
    private var hasLoaded = false       
    private var isFilterAnimating = false
    private var allLogs: List<CrashLog> = emptyList()
    
    private var activeFilter = "ALL" 

    private val chipViews = mutableListOf<TextView>()
    private val chipTags = mutableListOf<String>()

    private val typeMap = listOf(
        "data_app_crash" to R.string.log_type_app_crash,
        "data_app_native_crash" to R.string.log_type_app_native_crash,
        "data_app_anr" to R.string.log_type_app_anr,
        "system_app_crash" to R.string.log_type_sys_crash,
        "system_app_native_crash" to R.string.log_type_sys_native_crash,
        "system_app_anr" to R.string.log_type_sys_anr,
        "system_server_crash" to R.string.log_type_server_crash,
        "system_server_anr" to R.string.log_type_server_anr,
        "SYSTEM_TOMBSTONE" to R.string.log_type_tombstone,
        "system_server_watchdog" to R.string.log_type_watchdog
    )

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    fun destroy() {
        ioExecutor.shutdownNow()
    }

    fun refreshStatusIcons() {
        if (!hasLoaded && !isLoading) {
            fetchLogs(animateEntrance = true)
        }
    }

    fun forceRefresh() {
        if (!isLoading) {
            fetchLogs(animateEntrance = true)
        }
    }

    private data class CrashLog(
        val appName: String,
        val packageName: String,
        val crashType: String,
        val icon: Drawable?,
        val timestamp: Long,
        val rawText: String
    )

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

    fun build(): LinearLayout {
        val isDark = (activity.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        surfaceContainerColor = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurfaceContainer, if (isDark) Color.parseColor("#1C1B1F") else Color.parseColor("#FEF7FF"))
        surfaceBrightColor = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurfaceBright, if (isDark) Color.parseColor("#3B3B3E") else Color.parseColor("#FFFFFF"))
        surfaceContainerLowColor = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurfaceContainerLow, if (isDark) Color.parseColor("#1D1B20") else Color.parseColor("#F7F2FA"))
        onSurfaceColor = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOnSurface, if (isDark) Color.parseColor("#E6E1E5") else Color.parseColor("#1C1B1F"))
        onSurfaceVariantColor = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOnSurfaceVariant, if (isDark) Color.parseColor("#CAC4D0") else Color.parseColor("#49454F"))
        primaryContainerColor = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorPrimaryContainer, Color.parseColor("#E8DEF8"))
        onPrimaryContainerColor = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOnPrimaryContainer, Color.parseColor("#4F378B"))
        primaryColor = MaterialColors.getColor(activity, androidx.appcompat.R.attr.colorPrimary, if (isDark) Color.parseColor("#D0BCFF") else Color.parseColor("#6750A4"))

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MP, WC)
            clipChildren = false
            clipToPadding = false
        }

        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.app_name)
            textSize = 32f
            setTextColor(onPrimaryContainerColor)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                topMargin = dpToPx(8f)
                marginStart = dpToPx(8f)
                marginEnd = dpToPx(8f)
            }
        })

        chipsScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            clipChildren = false
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                topMargin = dpToPx(16f)
                marginStart = dpToPx(8f)
            }
        }

        chipsFrame = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(WC, dpToPx(36f))
            background = OptimizeEngine.pillBackground(surfaceContainerLowColor)
            clipChildren = false
            clipToPadding = false
        }

        slidingPill = View(activity).apply {
            background = OptimizeEngine.pillBackground(primaryContainerColor)
            layoutParams = FrameLayout.LayoutParams(0, dpToPx(28f)).apply {
                gravity = Gravity.CENTER_VERTICAL
                leftMargin = dpToPx(4f)
            }
        }
        chipsFrame.addView(slidingPill)

        chipsContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(4f), 0, dpToPx(4f), 0)
            clipChildren = false
            clipToPadding = false
            layoutParams = FrameLayout.LayoutParams(WC, dpToPx(36f))
        }
        chipsFrame.addView(chipsContainer)
        chipsScroll.addView(chipsFrame)
        root.addView(chipsScroll)

        logsWrapper = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                topMargin = dpToPx(16f)
            }
            clipChildren = false
            clipToPadding = false
        }

        currentLogsContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MP, WC)
        }
        logsWrapper.addView(currentLogsContainer)
        root.addView(logsWrapper)

        fetchLogs(animateEntrance = true)

        return root
    }

    private fun rebuildChips() {
        chipsContainer.removeAllViews()
        chipViews.clear()
        chipTags.clear()

        val filters = mutableListOf<Pair<String, String>>()
        filters.add("ALL" to activity.getString(R.string.log_type_all))
        typeMap.forEach { (tag, resId) ->
            if (allLogs.any { it.crashType == tag }) {
                filters.add(tag to activity.getString(resId))
            }
        }

        if (activeFilter != "ALL" && filters.none { it.first == activeFilter }) {
            activeFilter = "ALL"
        }

        filters.forEachIndexed { index, (tag, label) ->
            val textView = TextView(activity).apply {
                text = label
                textSize = 13f
                setPadding(dpToPx(14f), 0, dpToPx(14f), 0)
                setTextColor(if (activeFilter == tag) onPrimaryContainerColor else onSurfaceVariantColor)
                setTypeface(typeface, if (activeFilter == tag) Typeface.BOLD else Typeface.NORMAL)
                includeFontPadding = false
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(WC, MP)
            }

            textView.setOnClickListener {
                if (activeFilter != tag && !isFilterAnimating) {
                    OptimizeEngine.vibrateTick(activity)
                    val oldIndex = chipTags.indexOf(activeFilter)
                    activeFilter = tag
                    updateChipsSelection(index)
                    triggerLogFilterAnimation(tag, index > oldIndex)
                }
            }

            chipsContainer.addView(textView)
            chipViews.add(textView)
            chipTags.add(tag)
        }

        chipsContainer.post {
            val index = chipTags.indexOf(activeFilter).coerceAtLeast(0)
            updateChipsSelection(index, animate = false)
        }
    }

    private fun updateChipsSelection(selectedIndex: Int, animate: Boolean = true) {
        val targetView = chipViews.getOrNull(selectedIndex) ?: return

        chipViews.forEachIndexed { i, tv ->
            val isSel = i == selectedIndex
            tv.setTextColor(if (isSel) onPrimaryContainerColor else onSurfaceVariantColor)
            tv.setTypeface(null, if (isSel) Typeface.BOLD else Typeface.NORMAL)
        }

        val targetX = targetView.left.toFloat()
        val targetW = targetView.width

        if (animate) {
            slidingPill.animate()
                .x(targetX)
                .setDuration(250)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
            
            val lp = slidingPill.layoutParams
            val anim = ValueAnimator.ofInt(lp.width, targetW)
            anim.addUpdateListener {
                lp.width = it.animatedValue as Int
                slidingPill.layoutParams = lp
            }
            anim.duration = 250
            anim.interpolator = FastOutSlowInInterpolator()
            anim.start()
            
            chipsScroll.smoothScrollTo(targetView.left - dpToPx(16f), 0)
        } else {
            slidingPill.x = targetX
            val lp = slidingPill.layoutParams
            lp.width = targetW
            slidingPill.layoutParams = lp
        }
    }

    private fun triggerLogFilterAnimation(tag: String, moveRight: Boolean) {
        isFilterAnimating = true

        val duration = 300L
        val tabTransitionInterpolator = FastOutSlowInInterpolator()
        
        val dist = activity.resources.displayMetrics.widthPixels.toFloat()
        val startX = if (moveRight) dist else -dist

        val oldContainer = currentLogsContainer
        val newContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MP, WC)
        }

        val filtered = if (tag == "ALL") allLogs else allLogs.filter { it.crashType == tag }
        renderLogsToContainer(newContainer, filtered)
        
        logsWrapper.addView(newContainer)

        newContainer.translationX = startX
        newContainer.alpha = 1f
        oldContainer.alpha = 1f
        oldContainer.translationX = 0f

        newContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        oldContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        logsWrapper.post {
            newContainer.animate()
                .translationX(0f)
                .setDuration(duration)
                .setInterpolator(tabTransitionInterpolator)
                .withEndAction {
                    logsWrapper.removeView(oldContainer)
                    currentLogsContainer = newContainer
                    newContainer.translationX = 0f
                    oldContainer.translationX = 0f
                    isFilterAnimating = false
                    newContainer.setLayerType(View.LAYER_TYPE_NONE, null)
                    oldContainer.setLayerType(View.LAYER_TYPE_NONE, null)
                }.start()
            
            oldContainer.animate()
                .translationX(-startX)
                .setDuration(duration)
                .setInterpolator(tabTransitionInterpolator)
                .start()
        }
    }

    private fun fetchLogs(animateEntrance: Boolean = false) {
        if (isLoading) return
        isLoading = true
        
        currentLogsContainer.removeAllViews()
        
        val loadingWrapper = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                topMargin = dpToPx(48f)
                bottomMargin = dpToPx(48f)
            }
        }

        val wavyThemeContext = ContextThemeWrapper(
            activity, 
            com.google.android.material.R.style.Widget_Material3Expressive_CircularProgressIndicator_Wavy
        )
        val progressIndicator = CircularProgressIndicator(wavyThemeContext).apply {
            isIndeterminate = true
            trackThickness = dpToPx(4f)
            setTrackColor(ColorUtils.setAlphaComponent(onSurfaceVariantColor, 80))
            setIndicatorColor(primaryColor)
            layoutParams = FrameLayout.LayoutParams(dpToPx(48f), dpToPx(48f)).apply {
                gravity = Gravity.CENTER
            }
        }
        loadingWrapper.addView(progressIndicator)
        currentLogsContainer.addView(loadingWrapper)

        ioExecutor.execute {
            val logs = mutableListOf<CrashLog>()
            
            val nameCache = mutableMapOf<String, String>()
            val iconCache = mutableMapOf<String, Drawable?>()

            try {
                if (!PermissionHelper.hasAllPermissions(activity)) {
                    throw SecurityException("Missing permissions")
                }

                val dbm = activity.getSystemService(Context.DROPBOX_SERVICE) as DropBoxManager
                val since = System.currentTimeMillis() - 604800000L
                val now = System.currentTimeMillis()
                
                var currentTime = since
                var entry = dbm.getNextEntry(null, currentTime)

                while (entry != null) {
                    val tag = entry.tag ?: ""
                    if (tag.contains("crash") || tag.contains("anr") || 
                        tag.contains("SYSTEM_TOMBSTONE") || tag.contains("watchdog")) {
                        
                        var text: String? = null
                        try {
                            text = entry.inputStream?.bufferedReader()?.use { it.readText() }
                        } catch (e: Exception) {
                            text = entry.getText(1024 * 1024)
                        }

                        if (text != null) {
                            val pkgName = extractPackageName(text)
                            if (pkgName != null) {
                                var appName = pkgName
                                var icon: Drawable? = null
                                
                                if (nameCache.containsKey(pkgName) && iconCache.containsKey(pkgName)) {
                                    appName = nameCache[pkgName]!!
                                    icon = iconCache[pkgName]
                                } else {
                                    val pm = activity.packageManager
                                    try {
                                        val appInfo = pm.getApplicationInfo(pkgName, 0)
                                        appName = pm.getApplicationLabel(appInfo).toString()
                                        icon = pm.getApplicationIcon(appInfo)
                                    } catch (e: PackageManager.NameNotFoundException) {
                                        try {
                                            val component = ComponentName(activity, TangentLogsDetail::class.java)
                                            icon = pm.getActivityIcon(component)
                                        } catch (e2: Exception) {
                                            icon = ContextCompat.getDrawable(activity, android.R.drawable.sym_def_app_icon)
                                        }
                                    }
                                    nameCache[pkgName] = appName
                                    iconCache[pkgName] = icon
                                }

                                if (!logs.any { it.timestamp == entry.timeMillis && it.packageName == pkgName }) {
                                    logs.add(CrashLog(appName, pkgName, tag, icon, entry.timeMillis, text))
                                }
                            }
                        }
                    }
                    
                    currentTime = entry.timeMillis + 1
                    entry.close()
                    
                    if (currentTime > now) break
                    
                    entry = dbm.getNextEntry(null, currentTime)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val finalLogs = logs.sortedByDescending { it.timestamp }.take(50)

            activity.runOnUiThread {
                isLoading = false
                hasLoaded = true
                allLogs = finalLogs
                
                rebuildChips()
                
                val filtered = if (activeFilter == "ALL") allLogs else allLogs.filter { it.crashType == activeFilter }
                renderLogsToContainer(currentLogsContainer, filtered)

                if (animateEntrance) {
                    currentLogsContainer.alpha = 0f
                    currentLogsContainer.animate()
                        .alpha(1f)
                        .setDuration(600L)
                        .setInterpolator(FastOutSlowInInterpolator())
                        .start()
                } else {
                    currentLogsContainer.alpha = 1f
                }
            }
        }
    }

    private fun extractPackageName(text: String): String? {
        val pRegex = Regex("Process:\\s*([a-zA-Z0-9_.]+)")
        pRegex.find(text)?.let { return it.groupValues[1] }

        val anrRegex = Regex("Subject:\\s*ANR in\\s*([a-zA-Z0-9_.]+)")
        anrRegex.find(text)?.let { return it.groupValues[1] }

        val pkgRegex = Regex("Package:\\s*([a-zA-Z0-9_.]+)")
        pkgRegex.find(text)?.let { return it.groupValues[1] }
        
        val tombstoneRegex = Regex(">>>\\s*([a-zA-Z0-9_.]+)\\s*<<<")
        tombstoneRegex.find(text)?.let { return it.groupValues[1] }
        
        if (text.contains("system_server")) return "android"
        
        return null
    }

    private fun getCrashTypeLabel(tag: String): String {
        return when {
            tag == "data_app_crash" -> activity.getString(R.string.log_type_app_crash)
            tag == "data_app_native_crash" -> activity.getString(R.string.log_type_app_native_crash)
            tag == "data_app_anr" -> activity.getString(R.string.log_type_app_anr)
            tag == "system_app_crash" -> activity.getString(R.string.log_type_sys_crash)
            tag == "system_app_native_crash" -> activity.getString(R.string.log_type_sys_native_crash)
            tag == "system_app_anr" -> activity.getString(R.string.log_type_sys_anr)
            tag == "system_server_crash" -> activity.getString(R.string.log_type_server_crash)
            tag == "system_server_anr" -> activity.getString(R.string.log_type_server_anr)
            tag == "SYSTEM_TOMBSTONE" -> activity.getString(R.string.log_type_tombstone)
            tag == "system_server_watchdog" -> activity.getString(R.string.log_type_watchdog)
            else -> tag 
        }
    }

    private fun renderLogsToContainer(targetContainer: LinearLayout, logs: List<CrashLog>) {
        targetContainer.removeAllViews()

        val outerR = dpToPx(16f).toFloat()
        val innerR = dpToPx(4f).toFloat()
        val totalLogs = logs.size

        if (totalLogs == 0) {
            targetContainer.addView(TextView(activity).apply {
                text = activity.getString(R.string.home_no_crash_logs)
                textSize = 16f
                setTextColor(onSurfaceVariantColor)
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(32f), 0, dpToPx(32f))
            })
            return
        }

        logs.forEachIndexed { index, log ->
            val (topR, botR) = when {
                totalLogs == 1 -> outerR to outerR
                index == 0 -> outerR to innerR
                index == totalLogs - 1 -> innerR to outerR
                else -> innerR to innerR
            }

            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = makeRowRipple(surfaceBrightColor, topR, botR)
                setPadding(dpToPx(20f), dpToPx(14f), dpToPx(20f), dpToPx(14f))
                setMinimumHeight(dpToPx(72f))
                isClickable = true; isFocusable = true
                layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                    if (index < totalLogs - 1) bottomMargin = dpToPx(2f)
                }
                setOnClickListener {
                    OptimizeEngine.vibrateTick(activity)
                    TangentLogsDetail.cachedLogContent = log.rawText
                    val intent = Intent(activity, TangentLogsDetail::class.java).apply {
                        putExtra("log_title", log.appName)
                    }
                    activity.startActivity(intent)
                }
            }

            row.addView(ImageView(activity).apply {
                setImageDrawable(log.icon)
                layoutParams = LinearLayout.LayoutParams(dpToPx(32f), dpToPx(32f))
                    .apply { marginEnd = dpToPx(16f) }
            })

            val textCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
            }
            
            textCol.addView(TextView(activity).apply {
                text = log.appName
                textSize = 16f
                setTextColor(onSurfaceColor)
                setTypeface(typeface, Typeface.BOLD)
                includeFontPadding = false
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            val timeString = timeFormat.format(Date(log.timestamp))
            textCol.addView(TextView(activity).apply {
                text = timeString
                textSize = 14f
                setTextColor(onSurfaceVariantColor)
                alpha = 0.7f
                includeFontPadding = false
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                    topMargin = dpToPx(2f)
                }
            })
            row.addView(textCol)

            val pillText = TextView(activity).apply {
                text = getCrashTypeLabel(log.crashType)
                textSize = 11f
                setTextColor(onPrimaryContainerColor)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = OptimizeEngine.pillBackground(primaryContainerColor)
                setPadding(dpToPx(12f), dpToPx(6f), dpToPx(12f), dpToPx(6f))
                layoutParams = LinearLayout.LayoutParams(WC, WC).apply {
                    marginStart = dpToPx(12f)
                }
            }
            row.addView(pillText)

            targetContainer.addView(row)
        }
    }
}