package com.HeheJuice.CrashLogs.ui

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.os.LocaleListCompat
import com.HeheJuice.CrashLogs.R
import com.HeheJuice.CrashLogs.app.TangentHome
import com.HeheJuice.CrashLogs.util.OptimizeEngine
import com.google.android.material.color.MaterialColors
import kotlin.math.min

class SettingsPageContent(private val activity: AppCompatActivity) {

    companion object {
        private const val KEY_PENDING_LANG_FADE = "pending_language_fade_in"
        private const val KEY_USER_LANGUAGE_TAG = "user_language_tag"
    }

    private var languageDescView: TextView? = null

    private var surfaceBrightColor: Int = 0
    private var onSurfaceColor: Int = 0
    private var onSurfaceVariantColor: Int = 0
    private var onPrimaryContainerColor: Int = 0
    private var primaryContainerColor: Int = 0
    private var primaryColor: Int = 0

    private fun dpToPx(dp: Float): Int = OptimizeEngine.dpToPx(dp)
    private fun dpToPx(dp: Int): Int = OptimizeEngine.dpToPx(dp)

    private val MP = LinearLayout.LayoutParams.MATCH_PARENT
    private val WC = LinearLayout.LayoutParams.WRAP_CONTENT

    fun destroy() { /* no listeners */ }

    fun refreshStatusIcons() {
        languageDescView?.text = getCurrentLanguageLabel()
    }

    private fun makeRowRipple(
        contentColor: Int, topRadius: Float, bottomRadius: Float
    ): RippleDrawable {
        val content = OptimizeEngine.groupItemBackground(contentColor, topRadius, bottomRadius)
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(
                topRadius, topRadius, topRadius, topRadius,
                bottomRadius, bottomRadius, bottomRadius, bottomRadius
            )
            setColor(Color.WHITE)
        }
        val rc = ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurfaceVariantColor, 40))
        return RippleDrawable(rc, content, mask)
    }

    fun build(): LinearLayout {
        val isDark = (activity.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        onPrimaryContainerColor = MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorOnPrimaryContainer,
            if (isDark) Color.parseColor("#EADDFF") else Color.parseColor("#4F378B"))
        surfaceBrightColor = MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorSurfaceBright,
            if (isDark) Color.parseColor("#3B3B3E") else Color.parseColor("#FFFFFF"))
        onSurfaceColor = MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorOnSurface,
            if (isDark) Color.parseColor("#E6E1E5") else Color.parseColor("#1C1B1F"))
        onSurfaceVariantColor = MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorOnSurfaceVariant,
            Color.parseColor("#49454F"))
        primaryContainerColor = MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorPrimaryContainer,
            Color.parseColor("#E8DEF8"))
        primaryColor = MaterialColors.getColor(
            activity, androidx.appcompat.R.attr.colorPrimary,
            if (isDark) Color.parseColor("#D0BCFF") else Color.parseColor("#6750A4"))

        val outerR = dpToPx(16f).toFloat()
        val innerR = dpToPx(4f).toFloat()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MP, WC)
        }

        // ── 大標題 ──
        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.nav_settings)
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

        // ── Language group ──
        val langGroup = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { topMargin = dpToPx(24f) }
        }
        langGroup.addView(TextView(activity).apply {
            text = activity.getString(R.string.settings_group_general)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dpToPx(8f), dpToPx(6f), dpToPx(8f), dpToPx(8f))
        })

        val langRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = makeRowRipple(surfaceBrightColor, outerR, outerR)
            setPadding(dpToPx(20f), dpToPx(14f), dpToPx(20f), dpToPx(14f))
            setMinimumHeight(dpToPx(64f))
            isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(MP, WC)
        }

        langRow.addView(ImageView(activity).apply {
            setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.language_24px))
            setColorFilter(onSurfaceVariantColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
            layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                .apply { marginEnd = dpToPx(16f) }
        })

        val langTextCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
        }
        langTextCol.addView(TextView(activity).apply {
            text = activity.getString(R.string.settings_language)
            textSize = 16f
            setTextColor(onSurfaceColor)
        })
        val langDesc = TextView(activity).apply {
            text = getCurrentLanguageLabel()
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            alpha = 0.7f
            layoutParams = LinearLayout.LayoutParams(WC, WC).apply { topMargin = dpToPx(2f) }
        }
        languageDescView = langDesc
        langTextCol.addView(langDesc)
        langRow.addView(langTextCol)

        langRow.addView(ImageView(activity).apply {
            setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.arrow_forward_24px))
            setColorFilter(onSurfaceVariantColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
            layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                .apply { marginStart = dpToPx(8f) }
        })

        langRow.setOnClickListener {
            OptimizeEngine.vibrateTick(activity)
            showLanguagePickerDialog()
        }
        langGroup.addView(langRow)
        root.addView(langGroup)

        return root
    }

    // ─────────────────────────────────────────────────────────────────
    // Language picker
    // ─────────────────────────────────────────────────────────────────

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

    private fun getCurrentLanguageLabel(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return activity.getString(R.string.lang_follow_system)
        val tag = locales.toLanguageTags().substringBefore(",").replace('_', '-')
        val match = getLangOptions().firstOrNull {
            it.tag.isNotEmpty() && tag.equals(it.tag, ignoreCase = true)
        } ?: getLangOptions().firstOrNull {
            it.tag.isNotEmpty() && tag.startsWith(it.tag, ignoreCase = true)
        }
        return match?.let { activity.getString(it.labelRes) } ?: tag
    }

    private fun applyLanguageChange(selectedTag: String) {
        activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_LANGUAGE_TAG, selectedTag)
            .putBoolean(KEY_PENDING_LANG_FADE, true)
            .apply()

        if (selectedTag.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(selectedTag))
        }
    }

    private fun showLanguagePickerDialog() {
        val isDark = (activity.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val surfaceHigh = MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorSurfaceContainerHigh,
            if (isDark) Color.parseColor("#2B2930") else Color.parseColor("#ECE6F0"))
        val rowBg = MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorSurfaceBright,
            if (isDark) Color.parseColor("#3B3B3E") else Color.parseColor("#FFFFFF"))
        val onSurface = MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorOnSurface,
            if (isDark) Color.parseColor("#E6E1E5") else Color.parseColor("#1C1B1F"))
        val onSurfaceVariant = MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorOnSurfaceVariant,
            if (isDark) Color.parseColor("#CAC4D0") else Color.parseColor("#49454F"))
        val primary = MaterialColors.getColor(
            activity, androidx.appcompat.R.attr.colorPrimary,
            if (isDark) Color.parseColor("#D0BCFF") else Color.parseColor("#6750A4"))
        val primaryContainer = MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorPrimaryContainer,
            Color.parseColor("#E8DEF8"))
        val onPrimaryContainer = MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorOnPrimaryContainer,
            if (isDark) Color.parseColor("#EADDFF") else Color.parseColor("#4F378B"))

        val options = getLangOptions()
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentTag = if (currentLocales.isEmpty) ""
                         else currentLocales.toLanguageTags()
                             .substringBefore(",").replace('_', '-')

        var selectedIndex = options.indexOfFirst {
            it.tag.equals(currentTag, ignoreCase = true)
        }
        if (selectedIndex < 0) selectedIndex = options.indexOfFirst {
            it.tag.isNotEmpty() && currentTag.startsWith(it.tag, ignoreCase = true)
        }
        if (selectedIndex < 0) selectedIndex = 0

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(28f).toFloat()
                setColor(surfaceHigh)
            }
            clipToOutline = true
        }
        card.addView(TextView(activity).apply {
            text = activity.getString(R.string.language_picker_title)
            textSize = 22f
            setTextColor(onSurface)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(4f))
        })
        card.addView(TextView(activity).apply {
            text = activity.getString(R.string.language_picker_subtitle)
            textSize = 14f
            setTextColor(onSurfaceVariant)
            setPadding(dpToPx(24f), 0, dpToPx(24f), dpToPx(12f))
        })

        val outerR = dpToPx(16f).toFloat()
        val innerR = dpToPx(4f).toFloat()
        val rowH = dpToPx(56f)
        val gap = dpToPx(2f)
        val hPad = dpToPx(16f)
        val total = options.size

        val screenH = activity.resources.displayMetrics.heightPixels
        val maxDialogH = (screenH * 0.85f).toInt()
        val reserved = dpToPx(210f)
        val maxListH = (maxDialogH - reserved).coerceAtLeast(dpToPx(120f))
        val listH = minOf(total * (rowH + gap) + dpToPx(4f) * 2, maxListH)

        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, listH)
        }
        val listColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(hPad, dpToPx(4f), hPad, dpToPx(4f))
        }
        scroll.addView(listColumn)

        data class RowRef(
            val container: LinearLayout,
            val label: TextView,
            val check: ImageView,
            val topR: Float,
            val botR: Float
        )
        val rowRefs = mutableListOf<RowRef>()

        fun makeBg(color: Int, topR: Float, botR: Float) = GradientDrawable().apply {
            cornerRadii = floatArrayOf(topR, topR, topR, topR, botR, botR, botR, botR)
            setColor(color)
        }
        fun updateSelection() {
            rowRefs.forEachIndexed { idx, ref ->
                val sel = idx == selectedIndex
                val content = makeBg(
                    if (sel) primaryContainer else rowBg, ref.topR, ref.botR)
                val mask = makeBg(Color.WHITE, ref.topR, ref.botR)
                val rc = ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(onSurfaceVariant, 40))
                ref.container.background = RippleDrawable(rc, content, mask)
                ref.label.setTextColor(if (sel) onPrimaryContainer else onSurface)
                ref.check.visibility = if (sel) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

        options.forEachIndexed { index, opt ->
            val (topR, botR) = when {
                total == 1 -> outerR to outerR
                index == 0 -> outerR to innerR
                index == total - 1 -> innerR to outerR
                else -> innerR to innerR
            }
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true; isFocusable = true
                setPadding(dpToPx(20f), 0, dpToPx(20f), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, rowH
                ).apply { if (index < total - 1) bottomMargin = gap }
                setOnClickListener {
                    selectedIndex = index
                    OptimizeEngine.vibrateTick(activity)
                    updateSelection()
                }
            }
            val label = TextView(activity).apply {
                text = activity.getString(opt.labelRes)
                textSize = 16f
                setTextColor(onSurface)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
            }
            row.addView(label)
            val check = ImageView(activity).apply {
                setImageDrawable(
                    ContextCompat.getDrawable(activity, R.drawable.check_24px))
                setColorFilter(
                    onPrimaryContainer,
                    android.graphics.PorterDuff.Mode.SRC_ATOP)
                visibility = android.view.View.GONE
                layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                    .apply { marginStart = dpToPx(12f) }
            }
            row.addView(check)
            listColumn.addView(row)
            rowRefs.add(RowRef(row, label, check, topR, botR))
        }
        updateSelection()
        card.addView(scroll)

        val bottomBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
        }
        val rc = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(onSurfaceVariant, 40))

        val cancelBtn = TextView(activity).apply {
            text = activity.getString(R.string.tangent_cancel)
            textSize = 14f
            setTextColor(primary)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dpToPx(20f), dpToPx(12f), dpToPx(20f), dpToPx(12f))
            isClickable = true; isFocusable = true
            background = RippleDrawable(rc, GradientDrawable().apply {
                cornerRadius = dpToPx(100f).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(1f), primary)
            }, GradientDrawable().apply {
                cornerRadius = dpToPx(100f).toFloat()
                setColor(Color.WHITE)
            })
            setOnClickListener {
                OptimizeEngine.vibrateTick(activity)
                dialog.dismiss()
            }
        }
        val selectBtn = TextView(activity).apply {
            text = activity.getString(R.string.action_select)
            textSize = 14f
            setTextColor(onPrimaryContainer)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dpToPx(20f), dpToPx(12f), dpToPx(20f), dpToPx(12f))
            isClickable = true; isFocusable = true
            background = RippleDrawable(rc, GradientDrawable().apply {
                cornerRadius = dpToPx(100f).toFloat()
                setColor(primaryContainer)
            }, GradientDrawable().apply {
                cornerRadius = dpToPx(100f).toFloat()
                setColor(Color.WHITE)
            })
            layoutParams = LinearLayout.LayoutParams(WC, WC).apply {
                marginStart = dpToPx(8f)
            }
            setOnClickListener {
                OptimizeEngine.vibrateThud(activity)
                val selectedTag = options[selectedIndex].tag
                val curr = AppCompatDelegate.getApplicationLocales()
                val currTag = if (curr.isEmpty) ""
                              else curr.toLanguageTags()
                                  .substringBefore(",").replace('_', '-')

                if (selectedTag.equals(currTag, ignoreCase = true)) {
                    dialog.dismiss()
                    return@setOnClickListener
                }
                dialog.dismiss()

                val homeActivity = activity as? TangentHome
                if (homeActivity != null) {
                    homeActivity.animateFadeToBlack { applyLanguageChange(selectedTag) }
                } else {
                    applyLanguageChange(selectedTag)
                }
            }
        }
        bottomBar.addView(cancelBtn)
        bottomBar.addView(selectBtn)
        card.addView(bottomBar)

        dialog.setContentView(card)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        val dialogW = (activity.resources.displayMetrics.widthPixels - dpToPx(48f))
            .coerceAtMost(dpToPx(420f))
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.5f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(dialogW, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }
}