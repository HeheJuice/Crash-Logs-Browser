package com.HeheJuice.CrashLogs.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnLayout
import com.HeheJuice.CrashLogs.R
import com.HeheJuice.CrashLogs.app.Cosine
import com.HeheJuice.CrashLogs.util.OptimizeEngine
import com.HeheJuice.CrashLogs.util.Sine
import com.google.android.material.color.MaterialColors
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

class UpdatePageContent(private val activity: AppCompatActivity) {

    companion object {
        // ★ 請替換為你自己的簽名 SHA-256（去掉冒號、小寫）
        private const val EXPECTED_SIGNATURE_SHA256 =
            "bbd053c0a67dbfc7938a3086d13325bfa212d550d6e6cbf39e48edf6b52bfc0d"

        private const val REPO_OWNER = "HeheJuice"
        private const val REPO_NAME = "Crash-Logs-Browser"

        private const val REPO_HTML    = "https://github.com/$REPO_OWNER/$REPO_NAME"
        private const val REPO_LICENSE = "$REPO_HTML/blob/main/LICENSE"
        private const val REPO_PRIVACY = "$REPO_HTML/blob/main/Privacy_Policy.txt"
        private const val REPO_LATEST_API =
            "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

        private const val CREDIT_HEHE_USERNAME = "HeheJuice"
        private const val CREDIT_HEHE_CACHE = "hehejuice_avatar.png"
        private const val CREDIT_HEHE_TELEGRAM = "HeheJuice"
        private const val CREDIT_HEHE_INSTAGRAM = "hehejuice_"
        private const val CREDIT_MITOVER_USERNAME = "MiToverG422"
        private const val CREDIT_MITOVER_CACHE = "mitover_avatar.png"
        private const val CREDIT_MATERIAL_USERNAME = "material-components"
        private const val CREDIT_MATERIAL_CACHE = "material_components_avatar.png"
        private const val CREDIT_GOOGLE_USERNAME = "google"
        private const val CREDIT_GOOGLE_CACHE = "google_avatar.png"

        private val ANCHOR_TAG_RE =
            Regex("""<a\s+[^>]*id="[^"]*"[^>]*>.*?</a>""", RegexOption.IGNORE_CASE)
        private val ANCHOR_TAG_SELFCLOSE_RE =
            Regex("""<a\s+[^>]*id="[^"]*"[^>]*/?>""", RegexOption.IGNORE_CASE)
        private val LINK_ONLY_RE =
            Regex("""^\s*\[[^\]]+\]\((?:#|https?://)[^)]+\)\s*$""")
        private val ORDERED_RE =
            Regex("""^(\d+)\.\s+(.*)$""")
    }

    private lateinit var updateStatusView: TextView
    private lateinit var updateActionView: TextView
    private lateinit var releaseNotesView: TextView
    private lateinit var downloadProgressText: TextView
    private var downloadTask: DownloadApkTask? = null
    private var downloadedApkFile: File? = null

    private var appNameSine: Sine? = null
    private var lastAppNameAnimMs = 0L
    private var appNameAnimCooldownUntilMs = 0L

    private var primaryContainerColor: Int = 0
    private var tertiaryContainerColor: Int = 0
    private var onPrimaryContainerColor: Int = 0
    private var onTertiaryContainerColor: Int = 0
    private var surfaceBrightColor: Int = 0
    private var onSurfaceColor: Int = 0
    private var onSurfaceVariantColor: Int = 0
    private var primaryColor: Int = 0

    private val feedbackTargets = mutableListOf<OptimizeEngine.FeedbackTarget>()

    private fun dpToPx(dp: Float): Int = OptimizeEngine.dpToPx(dp)
    private fun dpToPx(dp: Int): Int = OptimizeEngine.dpToPx(dp)

    fun destroy() {
        feedbackTargets.forEach { it.destroy() }
        feedbackTargets.clear()
        downloadTask?.cancel(true)
        downloadTask = null
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

    private fun makeRipple(
        contentDrawable: android.graphics.drawable.Drawable,
        radius: Float,
        rippleBaseColor: Int
    ): RippleDrawable {
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.WHITE)
        }
        val rippleColor = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(rippleBaseColor, 40))
        return RippleDrawable(rippleColor, contentDrawable, mask)
    }

    private fun versionPillLabel(rawName: String): String = when {
        rawName.contains("測試版") -> "測試版"
        rawName.contains(Regex("(?i)beta")) -> "Beta"
        rawName.contains(Regex("(?i)debug")) -> "Debug"
        else -> activity.getString(R.string.version_tag_release)
    }

    private fun cleanVersionName(versionName: String): String {
        var result = versionName
            .replace("測試版", "")
            .replace(Regex("(?i)beta"), "")
            .replace(Regex("(?i)debug"), "")
        result = result
            .replace(Regex("^[\\s\\-_]+"), "")
            .replace(Regex("[\\s\\-_]+$"), "")
            .replace(Regex("[\\s\\-_]{2,}"), "-")
            .replace(Regex("\\(\\s*\\)"), "")
            .replace(Regex("\\[\\s*\\]"), "")
            .trim()
        return result.ifEmpty { versionName }
    }

    fun build(): LinearLayout {
        val isDark = (activity.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        surfaceBrightColor = MaterialColors.getColor(activity,
            com.google.android.material.R.attr.colorSurfaceBright,
            if (isDark) Color.parseColor("#3B3B3E") else Color.parseColor("#FFFFFF"))
        onPrimaryContainerColor = MaterialColors.getColor(activity,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            if (isDark) Color.parseColor("#EADDFF") else Color.parseColor("#4F378B"))
        onTertiaryContainerColor = MaterialColors.getColor(activity,
            com.google.android.material.R.attr.colorOnTertiaryContainer,
            if (isDark) Color.parseColor("#FFD8E4") else Color.parseColor("#31111D"))
        onSurfaceColor = MaterialColors.getColor(activity,
            com.google.android.material.R.attr.colorOnSurface,
            if (isDark) Color.parseColor("#E6E1E5") else Color.parseColor("#1C1B1F"))
        onSurfaceVariantColor = MaterialColors.getColor(activity,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            if (isDark) Color.parseColor("#CAC4D0") else Color.parseColor("#49454F"))
        primaryColor = MaterialColors.getColor(activity,
            androidx.appcompat.R.attr.colorPrimary,
            if (isDark) Color.parseColor("#D0BCFF") else Color.parseColor("#6750A4"))
        primaryContainerColor = MaterialColors.getColor(activity,
            com.google.android.material.R.attr.colorPrimaryContainer, Color.parseColor("#E8DEF8"))
        tertiaryContainerColor = MaterialColors.getColor(activity,
            com.google.android.material.R.attr.colorTertiaryContainer, Color.parseColor("#FFD8E4"))

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.setting_updates_and_about)
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

        val cardCornerRadiusPx = dpToPx(16f).toFloat()
        val gradientBg = OptimizeEngine.monetDiagonalGradient(
            primaryContainerColor, tertiaryContainerColor, cardCornerRadiusPx)

        val appInfoCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false; clipToPadding = false
            background = gradientBg
            setPadding(dpToPx(24f), dpToPx(28f), dpToPx(24f), dpToPx(28f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8f); bottomMargin = dpToPx(16f)
            }
            isClickable = true; isFocusable = true
            setOnClickListener {
                if (playAppNameAnimation(force = true)) OptimizeEngine.vibrateTick(activity)
            }
        }

        val appIconDrawable = try {
            activity.packageManager.getApplicationIcon(activity.packageName)
        } catch (_: Exception) { null }
        if (appIconDrawable != null) {
            appInfoCard.addView(ImageView(activity).apply {
                setImageDrawable(appIconDrawable)
                layoutParams = LinearLayout.LayoutParams(dpToPx(64f), dpToPx(64f)).apply {
                    bottomMargin = dpToPx(12f)
                }
            })
        }

        val rawVersionName = getVersionName()
        val displayVersionName = cleanVersionName(rawVersionName)
        val pillLabel = versionPillLabel(rawVersionName)
        val pillBg = primaryContainerColor
        val pillFg = onPrimaryContainerColor

        val sineAppName = Sine(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            textSizeSp = 20f
            setText(activity.getString(R.string.app_name), onPrimaryContainerColor)
        }
        appNameSine = sineAppName
        appInfoCard.addView(sineAppName)
        sineAppName.doOnLayout { playAppNameAnimation() }

        // ★ 版本標籤 & FOSS 標籤容器
        val pillContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(12f)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            clipChildren = false; clipToPadding = false
        }

        // 1. 版本標籤
        val pillView = TextView(activity).apply {
            text = "$pillLabel $displayVersionName"
            textSize = 13f
            setTextColor(pillFg)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            background = OptimizeEngine.pillBackground(pillBg)
            setPadding(dpToPx(18f), dpToPx(6f), dpToPx(18f), dpToPx(6f))
            minHeight = dpToPx(32f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable = true; isFocusable = true
        }
        val pillFeedback = OptimizeEngine.FeedbackTarget(pillView)
        feedbackTargets.add(pillFeedback)
        pillView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            pillFeedback.handleEvent(event)
            false
        }
        pillContainer.addView(pillView)

        // 2. 新增的 #FOSS 標籤
        val fossPillView = TextView(activity).apply {
            text = "#FOSS"
            textSize = 13f
            setTextColor(pillFg)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            background = OptimizeEngine.pillBackground(pillBg)
            setPadding(dpToPx(18f), dpToPx(6f), dpToPx(18f), dpToPx(6f))
            minHeight = dpToPx(32f)
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dpToPx(8f) // 標籤間距
            }
            isClickable = true; isFocusable = true
        }
        val fossPillFeedback = OptimizeEngine.FeedbackTarget(fossPillView)
        feedbackTargets.add(fossPillFeedback)
        fossPillView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            fossPillFeedback.handleEvent(event)
            false
        }
        pillContainer.addView(fossPillView)

        appInfoCard.addView(pillContainer)
        root.addView(appInfoCard)

        val updateCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = OptimizeEngine.roundedBackground(
                surfaceBrightColor, dpToPx(16f).toFloat())
            setPadding(dpToPx(20f), dpToPx(24f), dpToPx(20f), dpToPx(24f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpToPx(16f) }
        }

        updateStatusView = TextView(activity).apply {
            text = activity.getString(R.string.update_checking)
            textSize = 15f
            setTextColor(onSurfaceColor)
            gravity = Gravity.CENTER
        }
        updateCard.addView(updateStatusView)

        releaseNotesView = TextView(activity).apply {
            visibility = View.GONE
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            setLineSpacing(dpToPx(3f).toFloat(), 1f)
        }
        updateCard.addView(releaseNotesView)

        downloadProgressText = TextView(activity).apply {
            text = activity.getString(R.string.zero_percent)
            visibility = View.GONE
            textSize = 14f
            setTextColor(primaryColor)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8f); bottomMargin = dpToPx(8f)
            }
        }
        updateCard.addView(downloadProgressText)

        updateActionView = TextView(activity).apply {
            text = activity.getString(R.string.action_download)
            textSize = 15f
            setTextColor(onPrimaryContainerColor)
            gravity = Gravity.CENTER
            val pillRadius = dpToPx(100f).toFloat()
            val content = OptimizeEngine.pillBackground(primaryContainerColor, pillRadius)
            background = makeRipple(content, pillRadius, onPrimaryContainerColor)
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable = true; isFocusable = true
            visibility = View.GONE
        }
        updateCard.addView(updateActionView)
        root.addView(updateCard)

        val outerRadiusPx = dpToPx(16f).toFloat()
        val innerRadiusPx = dpToPx(4f).toFloat()
        val infoGroup = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpToPx(16f) }
        }

        fun addRowIcon(row: LinearLayout, iconResId: Int) {
            val iconView = ImageView(activity).apply {
                setImageDrawable(ContextCompat.getDrawable(activity, iconResId))
                setColorFilter(onSurfaceVariantColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
                layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                    .apply { marginEnd = dpToPx(16f) }
            }
            row.addView(iconView, 0)
        }

        fun buildRow(
            topRes: Float, bottomRes: Float, iconRes: Int,
            labelText: String, subText: String, url: String?,
            onClickOverride: (() -> Unit)?, bottomMargin: Int
        ): LinearLayout {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = makeRowRipple(surfaceBrightColor, topRes, bottomRes)
                setPadding(dpToPx(20f), dpToPx(16f), dpToPx(20f), dpToPx(16f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (bottomMargin > 0) this.bottomMargin = dpToPx(bottomMargin.toFloat())
                }
                setMinimumHeight(dpToPx(56f))
                isClickable = true; isFocusable = true
                setOnClickListener {
                    if (onClickOverride != null) onClickOverride()
                    else if (!url.isNullOrEmpty()) {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            }
            addRowIcon(row, iconRes)
            val textCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(activity).apply {
                text = labelText
                textSize = 16f
                setTextColor(onSurfaceColor)
                setTypeface(typeface, Typeface.BOLD)
            })
            textCol.addView(TextView(activity).apply {
                text = subText
                textSize = 14f
                setTextColor(onSurfaceVariantColor)
                alpha = 0.7f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpToPx(2f) }
            })
            row.addView(textCol)
            row.addView(ImageView(activity).apply {
                setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.arrow_forward_24px))
                setColorFilter(onSurfaceVariantColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
                layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                    .apply { marginStart = dpToPx(8f) }
            })
            return row
        }

        infoGroup.addView(buildRow(
            outerRadiusPx, innerRadiusPx, R.drawable.code_24px,
            activity.getString(R.string.view_source_code),
            activity.getString(R.string.desc_source),
            REPO_HTML, null, 2))
        infoGroup.addView(buildRow(
            innerRadiusPx, innerRadiusPx, R.drawable.license_24px,
            activity.getString(R.string.view_license),
            activity.getString(R.string.desc_license),
            REPO_LICENSE, null, 2))
        infoGroup.addView(buildRow(
            innerRadiusPx, outerRadiusPx, R.drawable.policy_24px,
            activity.getString(R.string.view_privacy),
            activity.getString(R.string.desc_privacy),
            REPO_PRIVACY, null, 0))

        root.addView(infoGroup)

        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.acknowledgments)
            textSize = 14f
            setTextColor(onPrimaryContainerColor)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dpToPx(8f), dpToPx(6f), dpToPx(8f), dpToPx(8f))
        })

        val creditsGroup = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpToPx(16f) }
        }

        val creditRowHeightPx = dpToPx(72f)
        val creditAvatarSizePx = dpToPx(40f)
        val creditPaddingPx = dpToPx(16f)

        fun buildCreditRow(
            topRes: Float, bottomRes: Float, avatarRes: Int?, avatarLetter: Char = 'N',
            name: String, descText: String, hasBottomMargin: Boolean,
            onClick: () -> Unit
        ): Pair<LinearLayout, ImageView> {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = makeRowRipple(surfaceBrightColor, topRes, bottomRes)
                setPadding(creditPaddingPx, 0, creditPaddingPx, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, creditRowHeightPx).apply {
                    if (hasBottomMargin) bottomMargin = dpToPx(2f)
                }
                isClickable = true; isFocusable = true
                setOnClickListener { onClick() }
            }
            val avatarContainer = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    creditAvatarSizePx, creditAvatarSizePx).apply { marginEnd = dpToPx(16f) }
            }
            val avatar = ImageView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT)
                if (avatarRes != null) setImageResource(avatarRes)
                else setImageBitmap(createMonetLetterCircle(
                    letter = avatarLetter, sizePx = creditAvatarSizePx,
                    backgroundColor = primaryContainerColor, textColor = onPrimaryContainerColor))
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = OptimizeEngine.circleBackground(Color.TRANSPARENT)
                clipToOutline = true
            }
            avatarContainer.addView(avatar)
            row.addView(avatarContainer)

            val textCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(activity).apply {
                text = name
                textSize = 16f
                setTextColor(onSurfaceColor)
                setTypeface(typeface, Typeface.BOLD)
                includeFontPadding = false
            })
            textCol.addView(TextView(activity).apply {
                text = descText
                textSize = 14f
                setTextColor(onSurfaceVariantColor)
                alpha = 0.7f
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dpToPx(4f) }
            })
            row.addView(textCol)
            row.addView(ImageView(activity).apply {
                setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.arrow_forward_24px))
                setColorFilter(onSurfaceVariantColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
                layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                    .apply { marginStart = dpToPx(8f) }
            })
            return row to avatar
        }

        val hehePair = buildCreditRow(
            outerRadiusPx, innerRadiusPx,
            R.drawable.hehejuice, 'H', "HeheJuice",
            activity.getString(R.string.credit_hehejuice_desc),
            hasBottomMargin = true
        ) {
            activity.startActivity(Intent(activity, Cosine::class.java).apply {
                putExtra(Cosine.EXTRA_TITLE, "HeheJuice")
                putExtra(Cosine.EXTRA_DESC, activity.getString(R.string.credit_hehejuice_desc))
                putExtra(Cosine.EXTRA_AVATAR_RES, R.drawable.hehejuice)
                putExtra(Cosine.EXTRA_AVATAR_LETTER, "H")
                putExtra(Cosine.EXTRA_GITHUB_USERNAME, CREDIT_HEHE_USERNAME)
                putExtra(Cosine.EXTRA_AVATAR_CACHE_NAME, CREDIT_HEHE_CACHE)
                putExtra(Cosine.EXTRA_GITHUB_URL, "https://github.com/HeheJuice")
                putExtra(Cosine.EXTRA_TELEGRAM_USERNAME, CREDIT_HEHE_TELEGRAM)
                putExtra(Cosine.EXTRA_INSTAGRAM_USERNAME, CREDIT_HEHE_INSTAGRAM)
                
                // ★ 將數量改為 3，並加入 Tangent-Lyrics
                putExtra(Cosine.EXTRA_OTHER_COUNT, 3)
                putExtra(Cosine.EXTRA_OTHER_1_REPO, "HeheJuice/OneUI-Lockscreen-Font-EXT")
                putExtra(Cosine.EXTRA_OTHER_1_TITLE, "OneUI-Lockscreen-Font-EXT")
                putExtra(Cosine.EXTRA_OTHER_1_ICON_RES, R.drawable.brand_family_24px)
                putExtra(Cosine.EXTRA_OTHER_2_REPO, "HeheJuice/OneUI-Settings-Hook")
                putExtra(Cosine.EXTRA_OTHER_2_TITLE, "OneUI-Settings-Hook")
                putExtra(Cosine.EXTRA_OTHER_2_ICON_RES, R.drawable.healing_24px)
                putExtra(Cosine.EXTRA_OTHER_3_REPO, "HeheJuice/Tangent-Lyrics")
                putExtra(Cosine.EXTRA_OTHER_3_TITLE, "Tangent-Lyrics")
                putExtra(Cosine.EXTRA_OTHER_3_ICON_RES, R.drawable.music_note_2_24px)
            })
        }
        creditsGroup.addView(hehePair.first)
        val heheAvatar = hehePair.second

        val mitoverPair = buildCreditRow(
            innerRadiusPx, innerRadiusPx,
            R.drawable.mortismi, 'M', "MiToverG422",
            activity.getString(R.string.credit_mortis_desc),
            hasBottomMargin = true
        ) {
            activity.startActivity(Intent(activity, Cosine::class.java).apply {
                putExtra(Cosine.EXTRA_TITLE, "MiToverG422")
                putExtra(Cosine.EXTRA_DESC, activity.getString(R.string.credit_mortis_desc))
                putExtra(Cosine.EXTRA_AVATAR_RES, R.drawable.mortismi)
                putExtra(Cosine.EXTRA_AVATAR_LETTER, "M")
                putExtra(Cosine.EXTRA_GITHUB_USERNAME, CREDIT_MITOVER_USERNAME)
                putExtra(Cosine.EXTRA_AVATAR_CACHE_NAME, CREDIT_MITOVER_CACHE)
                putExtra(Cosine.EXTRA_GITHUB_URL, "https://github.com/MiToverG422")
                putExtra(Cosine.EXTRA_OTHER_COUNT, 1)
                putExtra(Cosine.EXTRA_OTHER_1_REPO, "MiToverG422/ONextBox")
                putExtra(Cosine.EXTRA_OTHER_1_TITLE, "ONextBox")
                putExtra(Cosine.EXTRA_OTHER_1_ICON_RES, R.drawable.handyman_24px)
            })
        }
        creditsGroup.addView(mitoverPair.first)
        val mitoverAvatar = mitoverPair.second

        val materialPair = buildCreditRow(
            innerRadiusPx, innerRadiusPx,
            null, 'M', "Material Components",
            activity.getString(R.string.credit_material_desc),
            hasBottomMargin = true
        ) {
            activity.startActivity(Intent(activity, Cosine::class.java).apply {
                putExtra(Cosine.EXTRA_TITLE, "Material Components")
                putExtra(Cosine.EXTRA_DESC, activity.getString(R.string.credit_material_desc))
                putExtra(Cosine.EXTRA_AVATAR_RES, 0)
                putExtra(Cosine.EXTRA_AVATAR_LETTER, "M")
                putExtra(Cosine.EXTRA_GITHUB_USERNAME, CREDIT_MATERIAL_USERNAME)
                putExtra(Cosine.EXTRA_AVATAR_CACHE_NAME, CREDIT_MATERIAL_CACHE)
                putExtra(Cosine.EXTRA_GITHUB_URL, "https://github.com/material-components")
            })
        }
        creditsGroup.addView(materialPair.first)
        val materialAvatar = materialPair.second

        val googlePair = buildCreditRow(
            innerRadiusPx, outerRadiusPx,
            null, 'G', "Google",
            activity.getString(R.string.credit_google_desc),
            hasBottomMargin = false
        ) {
            activity.startActivity(Intent(activity, Cosine::class.java).apply {
                putExtra(Cosine.EXTRA_TITLE, "Google")
                putExtra(Cosine.EXTRA_DESC, activity.getString(R.string.credit_google_desc))
                putExtra(Cosine.EXTRA_AVATAR_RES, 0)
                putExtra(Cosine.EXTRA_AVATAR_LETTER, "G")
                putExtra(Cosine.EXTRA_GITHUB_USERNAME, CREDIT_GOOGLE_USERNAME)
                putExtra(Cosine.EXTRA_AVATAR_CACHE_NAME, CREDIT_GOOGLE_CACHE)
                putExtra(Cosine.EXTRA_GITHUB_URL, "https://github.com/google")
            })
        }
        creditsGroup.addView(googlePair.first)
        val googleAvatar = googlePair.second

        root.addView(creditsGroup)

        val cachedFile = File(activity.cacheDir, "app-release.apk")
        if (cachedFile.exists()) {
            downloadedApkFile = cachedFile
            updateActionView.text = activity.getString(R.string.action_install)
            updateActionView.isEnabled = true
            updateActionView.visibility = View.VISIBLE
            downloadProgressText.visibility = View.GONE
        }

        updateActionView.setOnClickListener {
            if (updateActionView.text == activity.getString(R.string.action_install) &&
                downloadedApkFile?.exists() == true) {
                installApk(downloadedApkFile!!)
            } else {
                downloadApk()
            }
        }

        loadGithubAvatar(heheAvatar, CREDIT_HEHE_USERNAME, CREDIT_HEHE_CACHE)
        loadGithubAvatar(mitoverAvatar, CREDIT_MITOVER_USERNAME, CREDIT_MITOVER_CACHE)
        loadGithubAvatar(materialAvatar, CREDIT_MATERIAL_USERNAME, CREDIT_MATERIAL_CACHE)
        loadGithubAvatar(googleAvatar, CREDIT_GOOGLE_USERNAME, CREDIT_GOOGLE_CACHE)

        if (!isOfficialSignature()) {
            updateStatusView.text = activity.getString(R.string.sign_unmatches)
            updateActionView.visibility = View.GONE
            releaseNotesView.visibility = View.GONE
            downloadProgressText.visibility = View.GONE
            deleteCachedApk()
        } else {
            checkForUpdates()
        }

        return root
    }

    fun refreshIfNeeded() {
        playAppNameAnimation()
        if (!isOfficialSignature()) {
            deleteCachedApk()
            return
        }
        checkForUpdates()
    }

    private fun playAppNameAnimation(force: Boolean = false): Boolean {
        val sine = appNameSine ?: return false
        if (!sine.isAttachedToWindow) return false
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastAppNameAnimMs < 250L) return false
        if (now < appNameAnimCooldownUntilMs) return false
        val durationMs = 900L; val staggerMs = 55L
        lastAppNameAnimMs = now
        sine.animateSineWave(durationMs = durationMs, staggerMs = staggerMs,
            amplitudeDp = 24f, phasePerChar = 0.75f, overshoot = true)
        appNameAnimCooldownUntilMs =
            now + sine.estimateTotalAnimationDuration(durationMs, staggerMs)
        return true
    }

    private fun createMonetLetterCircle(
        letter: Char, sizePx: Int, backgroundColor: Int, textColor: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor; style = Paint.Style.FILL
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, circlePaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = sizePx * 0.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fm = textPaint.fontMetrics
        val yOffset = -(fm.ascent + fm.descent) / 2f
        canvas.drawText(letter.toString(), sizePx / 2f, sizePx / 2f + yOffset, textPaint)
        return bmp
    }

    private fun isOfficialSignature(): Boolean {
        return try {
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activity.packageManager.getPackageInfo(
                    activity.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                activity.packageManager.getPackageInfo(
                    activity.packageName, PackageManager.GET_SIGNATURES)
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
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun loadGithubAvatar(avatarView: ImageView, username: String, cacheFileName: String) {
        val localAvatarFile = File(activity.cacheDir, cacheFileName)
        if (localAvatarFile.exists()) {
            BitmapFactory.decodeFile(localAvatarFile.absolutePath)?.let {
                avatarView.setImageBitmap(it)
            }
        }
        Thread {
            try {
                val connection = URL("https://github.com/$username.png")
                    .openConnection() as HttpsURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()
                if (connection.responseCode == HttpsURLConnection.HTTP_OK) {
                    val downloadedBitmap = BitmapFactory.decodeStream(connection.inputStream)
                    connection.disconnect()
                    if (downloadedBitmap != null) {
                        val localBitmap = if (localAvatarFile.exists())
                            BitmapFactory.decodeFile(localAvatarFile.absolutePath) else null
                        val isIdentical = localBitmap != null && downloadedBitmap.sameAs(localBitmap)
                        if (!isIdentical) {
                            FileOutputStream(localAvatarFile).use { out ->
                                downloadedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                            activity.runOnUiThread { avatarView.setImageBitmap(downloadedBitmap) }
                        }
                    }
                } else connection.disconnect()
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun checkForUpdates() {
        val cachedFile = File(activity.cacheDir, "app-release.apk")
        if (cachedFile.exists()) {
            downloadedApkFile = cachedFile
            updateActionView.text = activity.getString(R.string.action_install)
            updateActionView.isEnabled = true
            updateActionView.visibility = View.VISIBLE
            downloadProgressText.visibility = View.GONE
            return
        }

        updateStatusView.text = activity.getString(R.string.update_checking)
        updateActionView.visibility = View.GONE
        releaseNotesView.visibility = View.GONE
        downloadProgressText.visibility = View.GONE
        Thread {
            try {
                val connection = URL(REPO_LATEST_API).openConnection() as HttpsURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val code = connection.responseCode
                if (code == HttpsURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val latestTag = json.getString("tag_name")
                    val currentVersion = getVersionNameForUpdateCheck()
                    val latestVersion = latestTag.replace(Regex("^[^0-9]*"), "")
                    val currentVer = currentVersion.replace(Regex("^[^0-9]*"), "")
                    val cmp = compareVersions(latestVersion, currentVer)
                    activity.runOnUiThread {
                        if (cmp > 0) {
                            updateStatusView.text =
                                activity.getString(R.string.update_new_version, latestVersion)
                            val releaseBody = json.optString("body", "")
                            if (releaseBody.isNotEmpty()) {
                                releaseNotesView.text = formatReleaseNotes(releaseBody)
                                releaseNotesView.visibility = View.VISIBLE
                                val lp = releaseNotesView.layoutParams as LinearLayout.LayoutParams
                                lp.topMargin = dpToPx(8f); lp.bottomMargin = dpToPx(8f)
                                releaseNotesView.layoutParams = lp
                            }
                            updateActionView.text = activity.getString(R.string.action_download)
                            updateActionView.visibility = View.VISIBLE
                            updateActionView.isEnabled = true
                            downloadedApkFile = null
                        } else {
                            updateStatusView.text =
                                activity.getString(R.string.update_latest, currentVer)
                            releaseNotesView.visibility = View.GONE
                            updateActionView.visibility = View.GONE
                            deleteCachedApk()
                        }
                    }
                } else {
                    activity.runOnUiThread {
                        updateStatusView.text = activity.getString(R.string.update_server_error)
                        releaseNotesView.visibility = View.GONE
                        updateActionView.visibility = View.GONE
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                activity.runOnUiThread {
                    updateStatusView.text = activity.getString(R.string.update_connection_error)
                    releaseNotesView.visibility = View.GONE
                    updateActionView.visibility = View.GONE
                }
            }
        }.start()
    }

    private fun downloadApk() {
        downloadTask?.cancel(true)
        downloadTask = DownloadApkTask()
        downloadTask?.execute()
    }

    inner class DownloadApkTask : AsyncTask<Void, Int, File?>() {
        override fun onPreExecute() {
            updateActionView.text = activity.getString(R.string.zero_percent)
            updateActionView.isEnabled = false
            updateActionView.visibility = View.VISIBLE
            downloadProgressText.visibility = View.GONE
        }
        override fun doInBackground(vararg params: Void?): File? {
            try {
                val connection = URL(REPO_LATEST_API).openConnection() as HttpsURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                if (connection.responseCode != HttpsURLConnection.HTTP_OK) return null
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val assets = json.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) {
                        apkUrl = a.getString("browser_download_url")
                        break
                    }
                }
                connection.disconnect()
                if (apkUrl == null) return null

                val apkConn = URL(apkUrl).openConnection() as HttpsURLConnection
                apkConn.connectTimeout = 10000
                apkConn.readTimeout = 10000
                val contentLength = apkConn.contentLength
                val inputStream = apkConn.inputStream
                val outputFile = File(activity.cacheDir, "app-release.apk")
                if (outputFile.exists()) outputFile.delete()
                val outputStream = FileOutputStream(outputFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var total = 0
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    total += bytesRead
                    if (contentLength > 0) publishProgress(total * 100 / contentLength)
                }
                outputStream.close()
                inputStream.close()
                apkConn.disconnect()
                return outputFile
            } catch (e: Exception) {
                Log.e("UpdatePageContent", "Download failed", e)
                return null
            }
        }
        override fun onProgressUpdate(vararg values: Int?) {
            val progress = values[0] ?: 0
            updateActionView.text = activity.getString(R.string.progress_format, progress)
        }
        override fun onPostExecute(result: File?) {
            if (result != null && result.exists()) {
                downloadedApkFile = result
                updateActionView.text = activity.getString(R.string.action_install)
                updateActionView.isEnabled = true
            } else {
                Toast.makeText(activity,
                    activity.getString(R.string.download_failed),
                    Toast.LENGTH_SHORT).show()
                updateActionView.text = activity.getString(R.string.action_download)
                updateActionView.isEnabled = true
                downloadedApkFile = null
            }
            downloadTask = null
            downloadProgressText.visibility = View.GONE
        }
    }

    private fun installApk(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(activity,
                    activity.getString(R.string.allow_unknown_sources),
                    Toast.LENGTH_LONG).show()
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
                return
            }
        }
        try {
            val uri = FileProvider.getUriForFile(
                activity, "${activity.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(intent)
            } else {
                Toast.makeText(activity,
                    activity.getString(R.string.no_app_found_install),
                    Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("UpdatePageContent", "Install failed", e)
            Toast.makeText(activity,
                activity.getString(R.string.install_error, e.message ?: ""),
                Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteCachedApk() {
        try {
            val file = File(activity.cacheDir, "app-release.apk")
            if (file.exists()) {
                file.delete()
                if (downloadedApkFile == file) downloadedApkFile = null
                if (updateActionView.text == activity.getString(R.string.action_install)) {
                    updateActionView.text = activity.getString(R.string.action_download)
                    updateActionView.isEnabled = true
                }
            }
        } catch (_: Exception) { }
    }

    private fun formatReleaseNotes(body: String): SpannableStringBuilder {
        val withoutAnchors = body
            .replace(ANCHOR_TAG_RE, "")
            .replace(ANCHOR_TAG_SELFCLOSE_RE, "")
        val sb = SpannableStringBuilder()
        for (raw in withoutAnchors.split("\n")) {
            val t = raw.trim()
            if (LINK_ONLY_RE.matches(t)) continue
            val contentForCheck: String? = when {
                t.isEmpty() -> null
                t == "---" || t == "***" || t == "___" -> null
                t.startsWith("#") -> t.removePrefix("#".repeat(t.takeWhile { it == '#' }.length)).trim()
                t.startsWith("- ") || t.startsWith("* ") -> t.substring(2)
                t.startsWith("> ") -> t.substring(2)
                ORDERED_RE.containsMatchIn(t) -> ORDERED_RE.find(t)!!.groupValues[2]
                else -> t
            }
            if (contentForCheck != null && contentForCheck.isNotBlank() &&
                stripInlineMarkdown(contentForCheck).isBlank()) continue

            when {
                t == "---" || t == "***" || t == "___" -> sb.append("────────────────────\n")
                t.startsWith("#") -> {
                    val level = t.takeWhile { it == '#' }.length
                    val text = t.removePrefix("#".repeat(level)).trim()
                    if (text.isEmpty()) continue
                    val start = sb.length
                    appendInline(sb, text)
                    val end = sb.length
                    val sizeSp = when (level) { 1 -> 20f; 2 -> 18f; 3 -> 16f; else -> 15f }
                    sb.setSpan(AbsoluteSizeSpan(dpToPx(sizeSp)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append("\n")
                }
                t.startsWith("- ") || t.startsWith("* ") -> {
                    sb.append("• "); appendInline(sb, t.substring(2)); sb.append("\n")
                }
                ORDERED_RE.containsMatchIn(t) -> {
                    val m = ORDERED_RE.find(t)!!
                    sb.append("${m.groupValues[1]}. "); appendInline(sb, m.groupValues[2]); sb.append("\n")
                }
                t.startsWith("> ") -> {
                    sb.append("▎ "); appendInline(sb, t.substring(2)); sb.append("\n")
                }
                t.isEmpty() -> sb.append("\n")
                else -> { appendInline(sb, t); sb.append("\n") }
            }
        }
        return sb
    }

    private fun stripInlineMarkdown(raw: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '[') {
                val close = raw.indexOf(']', i)
                if (close > i && close + 1 < raw.length && raw[close + 1] == '(') {
                    val end = raw.indexOf(')', close + 2)
                    if (end > close) { sb.append(raw.substring(i + 1, close)); i = end + 1; continue }
                }
            }
            if (i + 1 < raw.length && raw[i] == '*' && raw[i + 1] == '*') {
                val end = raw.indexOf("**", i + 2)
                if (end > i) { sb.append(raw.substring(i + 2, end)); i = end + 2; continue }
            }
            if (raw[i] == '*') {
                val end = raw.indexOf('*', i + 1)
                if (end > i) { sb.append(raw.substring(i + 1, end)); i = end + 1; continue }
            }
            if (raw[i] == '`') {
                val end = raw.indexOf('`', i + 1)
                if (end > i) { sb.append(raw.substring(i + 1, end)); i = end + 1; continue }
            }
            sb.append(raw[i]); i++
        }
        return sb.toString()
    }

    private fun appendInline(sb: SpannableStringBuilder, raw: String) {
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '[') {
                val close = raw.indexOf(']', i)
                if (close > i && close + 1 < raw.length && raw[close + 1] == '(') {
                    val end = raw.indexOf(')', close + 2)
                    if (end > close) { sb.append(raw.substring(i + 1, close)); i = end + 1; continue }
                }
            }
            if (i + 1 < raw.length && raw[i] == '*' && raw[i + 1] == '*') {
                val end = raw.indexOf("**", i + 2)
                if (end > i) {
                    val start = sb.length
                    sb.append(raw.substring(i + 2, end))
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = end + 2; continue
                }
            }
            if (raw[i] == '*') {
                val end = raw.indexOf('*', i + 1)
                if (end > i) {
                    val start = sb.length
                    sb.append(raw.substring(i + 1, end))
                    sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = end + 1; continue
                }
            }
            if (raw[i] == '`') {
                val end = raw.indexOf('`', i + 1)
                if (end > i) {
                    val start = sb.length
                    sb.append(raw.substring(i + 1, end))
                    sb.setSpan(TypefaceSpan("monospace"), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = end + 1; continue
                }
            }
            sb.append(raw[i]); i++
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val p1 = v1.replace(Regex("[^0-9.]"), "").split(".").map { it.toIntOrNull() ?: 0 }
        val p2 = v2.replace(Regex("[^0-9.]"), "").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val a = p1.getOrElse(i) { 0 }
            val b = p2.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }

    private fun getVersionName(): String = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "1.0.0"
    } catch (_: Exception) { "1.0.0" }

    private fun getVersionNameForUpdateCheck(): String = getVersionName()
}