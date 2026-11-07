package com.HeheJuice.CrashLogs.app

import android.content.Intent
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
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.HeheJuice.CrashLogs.R
import com.HeheJuice.CrashLogs.util.MonetColorManager
import com.HeheJuice.CrashLogs.util.OptimizeEngine
import com.google.android.material.color.MaterialColors
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

class Cosine : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESC = "extra_desc"
        const val EXTRA_AVATAR_RES = "extra_avatar_res"
        const val EXTRA_AVATAR_LETTER = "extra_avatar_letter"
        const val EXTRA_GITHUB_USERNAME = "extra_github_username"
        const val EXTRA_GITHUB_URL = "extra_github_url"
        const val EXTRA_AVATAR_CACHE_NAME = "extra_avatar_cache_name"
        const val EXTRA_TELEGRAM_USERNAME = "extra_telegram_username"
        const val EXTRA_INSTAGRAM_USERNAME = "extra_instagram_username"

        const val EXTRA_OTHER_COUNT = "extra_other_count"

        const val EXTRA_OTHER_1_REPO = "extra_other_1_repo"
        const val EXTRA_OTHER_1_TITLE = "extra_other_1_title"
        const val EXTRA_OTHER_1_ICON_RES = "extra_other_1_icon_res"

        const val EXTRA_OTHER_2_REPO = "extra_other_2_repo"
        const val EXTRA_OTHER_2_TITLE = "extra_other_2_title"
        const val EXTRA_OTHER_2_ICON_RES = "extra_other_2_icon_res"

        const val EXTRA_OTHER_3_REPO = "extra_other_3_repo"
        const val EXTRA_OTHER_3_TITLE = "extra_other_3_title"
        const val EXTRA_OTHER_3_ICON_RES = "extra_other_3_icon_res"

        private const val DESC_CACHE_FILE = "repo_descriptions.json"
    }

    private var primaryContainerColor: Int = 0
    private var onPrimaryContainerColor: Int = 0
    private var surfaceContainerColor: Int = 0
    private var surfaceBrightColor: Int = 0
    private var onSurfaceColor: Int = 0
    private var onSurfaceVariantColor: Int = 0

    private val feedbackTargets = mutableListOf<OptimizeEngine.FeedbackTarget>()

    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Cosine-IO").apply { isDaemon = true }
    }

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun dpToPx(dp: Float): Int = OptimizeEngine.dpToPx(dp)
    private fun dpToPx(dp: Int): Int = OptimizeEngine.dpToPx(dp)

    private fun makeRowRipple(
        contentColor: Int,
        topRadius: Float,
        bottomRadius: Float
    ): RippleDrawable {
        val content = OptimizeEngine.groupItemBackground(
            contentColor, topRadius, bottomRadius
        )

        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(
                topRadius, topRadius,
                topRadius, topRadius,
                bottomRadius, bottomRadius,
                bottomRadius, bottomRadius
            )
            setColor(Color.WHITE)
        }

        val rippleColor = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(onSurfaceVariantColor, 40)
        )

        return RippleDrawable(rippleColor, content, mask)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }

        MonetColorManager.applyToActivity(this)

        primaryContainerColor = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorPrimaryContainer,
            Color.parseColor("#E8DEF8")
        )
        onPrimaryContainerColor = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnPrimaryContainer,
            if (isDark) Color.parseColor("#EADDFF") else Color.parseColor("#4F378B")
        )
        surfaceContainerColor = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurfaceContainer,
            if (isDark) Color.parseColor("#1C1B1F") else Color.parseColor("#FEF7FF")
        )
        surfaceBrightColor = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurfaceBright,
            if (isDark) Color.parseColor("#3B3B3E") else Color.parseColor("#FFFFFF")
        )
        onSurfaceColor = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurface,
            if (isDark) Color.parseColor("#E6E1E5") else Color.parseColor("#1C1B1F")
        )
        onSurfaceVariantColor = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurfaceVariant,
            Color.parseColor("#49454F")
        )

        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val desc = intent?.getStringExtra(EXTRA_DESC).orEmpty()
        val avatarRes = intent?.getIntExtra(EXTRA_AVATAR_RES, 0) ?: 0
        val avatarLetter = intent?.getStringExtra(EXTRA_AVATAR_LETTER)
            .orEmpty().firstOrNull() ?: '?'
        val githubUsername = intent?.getStringExtra(EXTRA_GITHUB_USERNAME).orEmpty()
        val githubUrl = intent?.getStringExtra(EXTRA_GITHUB_URL).orEmpty()
        val explicitCacheName = intent?.getStringExtra(EXTRA_AVATAR_CACHE_NAME).orEmpty()
        val telegramUsername = intent?.getStringExtra(EXTRA_TELEGRAM_USERNAME).orEmpty()
        val instagramUsername = intent?.getStringExtra(EXTRA_INSTAGRAM_USERNAME).orEmpty()

        val otherCount = intent?.getIntExtra(EXTRA_OTHER_COUNT, 0) ?: 0
        data class OtherProject(
            val repo: String,
            val title: String,
            val iconRes: Int
        )
        val otherProjects = mutableListOf<OtherProject>()
        for (i in 1..otherCount.coerceAtMost(3)) {
            val repo = intent?.getStringExtra("extra_other_${i}_repo").orEmpty()
            val t = intent?.getStringExtra("extra_other_${i}_title").orEmpty()
            val icon = intent?.getIntExtra("extra_other_${i}_icon_res", 0) ?: 0
            if (repo.isNotEmpty() && t.isNotEmpty()) {
                otherProjects.add(OtherProject(repo, t, icon))
            }
        }

        val mainContainer = FrameLayout(this).apply {
            setBackgroundColor(surfaceContainerColor)
            clipChildren = false
            clipToPadding = false
        }

        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            layoutParams = FrameLayout.LayoutParams(MP, MP)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(MP, WC)
            clipChildren = false
            clipToPadding = false
        }

        val backBtn = ImageView(this).apply {
            setImageDrawable(
                ContextCompat.getDrawable(this@Cosine, R.drawable.arrow_back_24px)
            )
            background = OptimizeEngine.circleBackground(surfaceBrightColor)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val size = dpToPx(50f)
            val pad = dpToPx(12f)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(size, size)
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.back_content_desc)
            setOnClickListener {
                OptimizeEngine.vibrateTick(this@Cosine)
                finish()
            }
        }
        topBar.addView(backBtn)
        feedbackTargets += OptimizeEngine.FeedbackTarget(backBtn)

        topBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        val bigTitle = TextView(this).apply {
            text = getString(R.string.cosine_details_title)
            textSize = 32f
            setTextColor(onPrimaryContainerColor)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                topMargin = dpToPx(8f)
                bottomMargin = dpToPx(16f)
                marginStart = dpToPx(8f)
                marginEnd = dpToPx(8f)
            }
        }

        val outerR = dpToPx(16f).toFloat()
        val innerR = dpToPx(4f).toFloat()

        // ── Group 1: Data ──
        val dataGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MP, WC)
        }
        dataGroup.addView(sectionLabel(getString(R.string.cosine_group_data)))

        val dataRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = makeRowRipple(surfaceBrightColor, outerR, outerR)
            setPadding(dpToPx(16f), 0, dpToPx(16f), 0)
            layoutParams = LinearLayout.LayoutParams(MP, dpToPx(72f))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                OptimizeEngine.vibrateTick(this@Cosine)
            }
        }

        val avatarSizePx = dpToPx(40f)
        val avatarContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(avatarSizePx, avatarSizePx)
                .apply { marginEnd = dpToPx(16f) }
        }
        val avatarView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MP, MP)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = OptimizeEngine.circleBackground(Color.TRANSPARENT)
            clipToOutline = true
        }

        val cacheFileName: String? = when {
            explicitCacheName.isNotEmpty() -> explicitCacheName
            githubUsername.isNotEmpty() ->
                "${githubUsername.lowercase(Locale.ROOT)}_avatar.png"
            else -> null
        }

        val cachedFile = cacheFileName?.let { File(cacheDir, it) }

        val cachedBitmap: Bitmap? = cachedFile
            ?.takeIf { it.exists() }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }

        when {
            cachedBitmap != null -> {
                avatarView.setImageBitmap(cachedBitmap)
            }
            avatarRes != 0 -> {
                avatarView.setImageResource(avatarRes)
            }
            else -> {
                avatarView.setImageBitmap(
                    createMonetLetterCircle(
                        letter = avatarLetter,
                        sizePx = avatarSizePx,
                        backgroundColor = primaryContainerColor,
                        textColor = onPrimaryContainerColor
                    )
                )
            }
        }

        if (githubUsername.isNotEmpty() && cachedFile != null && !cachedFile.exists()) {
            ioExecutor.execute {
                downloadAvatarIfNeeded(githubUsername, cachedFile) { bmp ->
                    runOnUiThread { avatarView.setImageBitmap(bmp) }
                }
            }
        }

        avatarContainer.addView(avatarView)
        dataRow.addView(avatarContainer)

        val dataTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
        }
        dataTextCol.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(onSurfaceColor)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        })
        dataTextCol.addView(TextView(this).apply {
            text = desc
            textSize = 14f
            setTextColor(onSurfaceVariantColor)
            alpha = 0.7f
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                topMargin = dpToPx(4f)
            }
        })
        dataRow.addView(dataTextCol)

        dataGroup.addView(dataRow)
        root.addView(bigTitle)
        root.addView(dataGroup)

        // ── Group 2: Related Links ──
        data class LinkRow(
            val iconRes: Int,
            val title: String,
            val subtitle: String,
            val url: String
        )

        val linkRows = mutableListOf<LinkRow>()
        linkRows.add(
            LinkRow(
                R.drawable.ic_github,
                getString(R.string.cosine_github_title),
                getString(R.string.cosine_github_desc),
                githubUrl
            )
        )
        if (telegramUsername.isNotEmpty()) {
            linkRows.add(
                LinkRow(
                    R.drawable.ic_tg,
                    "Telegram",
                    "@$telegramUsername",
                    "https://t.me/$telegramUsername"
                )
            )
        }
        if (instagramUsername.isNotEmpty()) {
            linkRows.add(
                LinkRow(
                    R.drawable.ic_ig,
                    "Instagram",
                    "@$instagramUsername",
                    "https://instagram.com/$instagramUsername"
                )
            )
        }

        val linksGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                topMargin = dpToPx(12f)
            }
        }
        linksGroup.addView(sectionLabel(getString(R.string.cosine_group_links)))

        linkRows.forEachIndexed { index, link ->
            val topR: Float
            val bottomR: Float
            val isLast = index == linkRows.size - 1
            when {
                linkRows.size == 1 -> {
                    topR = outerR; bottomR = outerR
                }
                index == 0 -> {
                    topR = outerR; bottomR = innerR
                }
                isLast -> {
                    topR = innerR; bottomR = outerR
                }
                else -> {
                    topR = innerR; bottomR = innerR
                }
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = makeRowRipple(surfaceBrightColor, topR, bottomR)
                setPadding(dpToPx(20f), dpToPx(14f), dpToPx(20f), dpToPx(14f))
                setMinimumHeight(dpToPx(64f))
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                    if (index > 0) topMargin = dpToPx(2f)
                }
                setOnClickListener {
                    OptimizeEngine.vibrateTick(this@Cosine)
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                    } catch (_: Exception) {
                        Toast.makeText(
                            this@Cosine,
                            getString(R.string.cosine_open_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            row.addView(ImageView(this).apply {
                setImageDrawable(
                    ContextCompat.getDrawable(this@Cosine, link.iconRes)
                )
                setColorFilter(
                    onSurfaceVariantColor,
                    android.graphics.PorterDuff.Mode.SRC_ATOP
                )
                layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                    .apply { marginEnd = dpToPx(16f) }
            })

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = link.title
                textSize = 16f
                setTextColor(onSurfaceColor)
                includeFontPadding = false
            })
            textCol.addView(TextView(this).apply {
                text = link.subtitle
                textSize = 14f
                setTextColor(onSurfaceVariantColor)
                alpha = 0.7f
                includeFontPadding = false
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                    topMargin = dpToPx(2f)
                }
            })
            row.addView(textCol)

            row.addView(ImageView(this).apply {
                setImageDrawable(
                    ContextCompat.getDrawable(this@Cosine, R.drawable.arrow_forward_24px)
                )
                setColorFilter(
                    onSurfaceVariantColor,
                    android.graphics.PorterDuff.Mode.SRC_ATOP
                )
                layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                    .apply { marginStart = dpToPx(8f) }
            })

            linksGroup.addView(row)
        }

        root.addView(linksGroup)

        // ── Group 3: Other Projects ──
        if (githubUsername.isNotEmpty() && otherProjects.isNotEmpty()) {
            val otherGroup = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                    topMargin = dpToPx(12f)
                }
            }

            otherGroup.addView(sectionLabel(
                getString(R.string.cosine_other_projects_title, githubUsername)
            ))

            val descViews = mutableMapOf<String, TextView>()
            val cachedDescs = loadDescCache()

            otherProjects.forEachIndexed { index, proj ->
                val topR: Float
                val bottomR: Float
                when {
                    otherProjects.size == 1 -> {
                        topR = outerR; bottomR = outerR
                    }
                    index == 0 -> {
                        topR = outerR; bottomR = innerR
                    }
                    index == otherProjects.size - 1 -> {
                        topR = innerR; bottomR = outerR
                    }
                    else -> {
                        topR = innerR; bottomR = innerR
                    }
                }

                val projectUrl = "https://github.com/${proj.repo}"

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = makeRowRipple(surfaceBrightColor, topR, bottomR)
                    setPadding(dpToPx(20f), dpToPx(14f), dpToPx(20f), dpToPx(14f))
                    setMinimumHeight(dpToPx(64f))
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                        if (index < otherProjects.size - 1) bottomMargin = dpToPx(2f)
                    }
                    setOnClickListener {
                        OptimizeEngine.vibrateTick(this@Cosine)
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(projectUrl)))
                        } catch (_: Exception) {
                            Toast.makeText(
                                this@Cosine,
                                getString(R.string.cosine_open_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                val iconRes = if (proj.iconRes != 0) proj.iconRes else R.drawable.ic_github
                row.addView(ImageView(this).apply {
                    setImageDrawable(ContextCompat.getDrawable(this@Cosine, iconRes))
                    setColorFilter(
                        onSurfaceVariantColor,
                        android.graphics.PorterDuff.Mode.SRC_ATOP
                    )
                    layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                        .apply { marginEnd = dpToPx(16f) }
                })

                val textCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
                }
                textCol.addView(TextView(this).apply {
                    text = proj.title
                    textSize = 16f
                    setTextColor(onSurfaceColor)
                    includeFontPadding = false
                })
                val descView = TextView(this).apply {
                    text = cachedDescs[proj.repo].orEmpty()
                    textSize = 14f
                    setTextColor(onSurfaceVariantColor)
                    alpha = 0.7f
                    includeFontPadding = false
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                        topMargin = dpToPx(2f)
                    }
                }
                textCol.addView(descView)
                row.addView(textCol)
                descViews[proj.repo] = descView

                row.addView(ImageView(this).apply {
                    setImageDrawable(
                        ContextCompat.getDrawable(this@Cosine, R.drawable.arrow_forward_24px)
                    )
                    setColorFilter(
                        onSurfaceVariantColor,
                        android.graphics.PorterDuff.Mode.SRC_ATOP
                    )
                    layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f))
                        .apply { marginStart = dpToPx(8f) }
                })

                otherGroup.addView(row)
            }

            root.addView(otherGroup)

            val missingRepos = otherProjects
                .map { it.repo }
                .filter { cachedDescs[it].isNullOrEmpty() }

            if (missingRepos.isNotEmpty()) {
                ioExecutor.execute {
                    val newEntries = mutableMapOf<String, String>()
                    for (repo in missingRepos) {
                        val d = fetchRepoDescription(repo)
                        if (!d.isNullOrEmpty()) newEntries[repo] = d
                    }
                    if (newEntries.isNotEmpty()) {
                        val merged = cachedDescs.toMutableMap().apply { putAll(newEntries) }
                        saveDescCache(merged)
                        runOnUiThread {
                            newEntries.forEach { (repo, d) ->
                                descViews[repo]?.text = d
                            }
                        }
                    }
                }
            }
        }

        scrollView.addView(root)
        mainContainer.addView(scrollView)
        mainContainer.addView(topBar)
        setContentView(mainContainer)

        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            topBar.setPadding(
                dpToPx(16f),
                statusBarTop + dpToPx(8f),
                dpToPx(16f),
                dpToPx(8f)
            )

            val totalTopBarHeight = statusBarTop + dpToPx(8f + 50f + 8f)
            root.setPadding(
                dpToPx(16f),
                totalTopBarHeight,
                dpToPx(16f),
                navBarBottom + dpToPx(16f)
            )
            insets
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        for (t in feedbackTargets) t.handleEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        feedbackTargets.forEach { it.destroy() }
        feedbackTargets.clear()
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────
    // 頭像背景下載
    // ─────────────────────────────────────────────────────────────────

    private fun downloadAvatarIfNeeded(
        username: String,
        targetFile: File,
        onDone: (Bitmap) -> Unit
    ) {
        try {
            val conn = URL("https://github.com/$username.png")
                .openConnection() as HttpsURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()
            if (conn.responseCode == HttpsURLConnection.HTTP_OK) {
                val bmp = BitmapFactory.decodeStream(conn.inputStream)
                conn.disconnect()
                if (bmp != null) {
                    FileOutputStream(targetFile).use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    onDone(bmp)
                }
            } else {
                conn.disconnect()
            }
        } catch (_: Exception) { }
    }

    // ─────────────────────────────────────────────────────────────────
    // GitHub 描述拉取 + 快取
    // ─────────────────────────────────────────────────────────────────

    private fun descCacheFile(): File = File(cacheDir, DESC_CACHE_FILE)

    private fun loadDescCache(): MutableMap<String, String> {
        return try {
            val f = descCacheFile()
            if (!f.exists()) return mutableMapOf()
            val json = JSONObject(f.readText())
            mutableMapOf<String, String>().also { m ->
                json.keys().forEach { k ->
                    val v = json.optString(k, "")
                    if (v.isNotEmpty()) m[k] = v
                }
            }
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveDescCache(map: Map<String, String>) {
        try {
            val json = JSONObject()
            map.forEach { (k, v) ->
                if (v.isNotEmpty()) json.put(k, v)
            }
            descCacheFile().writeText(json.toString())
        } catch (_: Exception) { }
    }

    private fun fetchRepoDescription(fullRepo: String): String? {
        return try {
            val url = URL("https://api.github.com/repos/$fullRepo")
            val conn = url.openConnection() as HttpsURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "TangentUI-Sample-Cosine")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val code = conn.responseCode
            val remaining = conn.getHeaderField("X-RateLimit-Remaining")
                ?.toIntOrNull()

            if (code != HttpsURLConnection.HTTP_OK ||
                (remaining != null && remaining <= 0)
            ) {
                conn.disconnect()
                return null
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            if (json.isNull("description")) return null
            val d = json.optString("description", "").trim()
            if (d.isEmpty()) null else d
        } catch (_: Exception) {
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(onPrimaryContainerColor)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dpToPx(8f), dpToPx(6f), dpToPx(8f), dpToPx(8f))
    }

    private fun createMonetLetterCircle(
        letter: Char,
        sizePx: Int,
        backgroundColor: Int,
        textColor: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
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
}