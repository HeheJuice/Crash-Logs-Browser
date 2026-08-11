package com.HeheJuice.CrashLogs

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.*
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class DetailsActivity : Activity() {

    private lateinit var updateStatusView: TextView
    private lateinit var updateActionView: TextView
    private var googleSansFlexTypeface: Typeface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        // Load Google Sans Flex from assets folder
        googleSansFlexTypeface = try {
            Typeface.createFromAsset(assets, "GoogleSansFlex.ttf")
        } catch (e: Exception) {
            null
        }

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val bgColor = if (isDark) Color.parseColor("#000000") else Color.parseColor("#F2F2F7")
        val cardBgColor = if (isDark) Color.parseColor("#1C1C1E") else Color.parseColor("#FFFFFF")
        val cardBorderColor = if (isDark) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA")
        val primaryTextColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#000000")
        val secondaryTextColor = if (isDark) Color.parseColor("#8E8E93") else Color.parseColor("#6C6C70")
        val accentColor = if (isDark) Color.parseColor("#3E82F7") else Color.parseColor("#0066FF")
        val backBtnBgColor = if (isDark) Color.parseColor("#3A3A3C") else Color.parseColor("#E5E5EA")

        val statusBarHeight = getStatusBarHeight()
        val dpToPx = { dp: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt() }

        val rootFrameLayout = FrameLayout(this).apply { setBackgroundColor(bgColor) }

        val scrollView = ScrollView(this).apply {
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

        // ----- Banner Card -----
        val bannerCard = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dpToPx(28f).toFloat()
                setStroke(0, Color.TRANSPARENT)
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val backgroundImage = ImageView(this).apply {
            val imageResId = resources.getIdentifier("hehejuicebanner", "drawable", packageName)
            if (imageResId != 0) {
                setImageResource(imageResId)
            } else {
                setBackgroundColor(accentColor)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(200f)
            )
        }
        bannerCard.addView(backgroundImage)

        val dimOverlay = View(this).apply {
            setBackgroundColor(Color.parseColor("#66000000"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bannerCard.addView(dimOverlay)

        val titleText = TextView(this).apply {
            text = getString(R.string.details_title) // "Crash Logs Browser"
            textSize = 32f
            setTextColor(Color.WHITE)

            // Bold rounded configuration: Higher weight for boldness, high ROND axis for rounded style
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && googleSansFlexTypeface != null) {
                typeface = Typeface.create(googleSansFlexTypeface, 700, false)
                fontVariationSettings = "'wght' 700, 'ROND' 100, 'opsz' 14"
            } else {
                typeface = googleSansFlexTypeface ?: Typeface.DEFAULT_BOLD
            }

            gravity = Gravity.CENTER
            translationY = -dpToPx(3f).toFloat()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bannerCard.addView(titleText)
        scrollContent.addView(bannerCard)

        // ----- UPDATE CHECKER CARD -----
        val updateCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBgColor)
                cornerRadius = dpToPx(28f).toFloat()
                setStroke(dpToPx(1f), cardBorderColor)
            }
            setPadding(dpToPx(20f), dpToPx(24f), dpToPx(20f), dpToPx(24f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(16f)
            }
        }

        updateStatusView = TextView(this).apply {
            text = getString(R.string.update_checking)
            textSize = 15f
            setTextColor(secondaryTextColor)
            if (googleSansFlexTypeface != null) typeface = googleSansFlexTypeface
            gravity = Gravity.CENTER
        }
        updateCard.addView(updateStatusView)

        updateActionView = TextView(this).apply {
            text = ""
            textSize = 15f
            setTextColor(accentColor)
            if (googleSansFlexTypeface != null) typeface = googleSansFlexTypeface
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/HeheJuice/Crash-Logs-Browser/releases")))
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        updateCard.addView(updateActionView)
        scrollContent.addView(updateCard)

        // ----- NEW INFO CARD: Source Code & License Buttons -----
        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBgColor)
                cornerRadius = dpToPx(28f).toFloat()
                setStroke(dpToPx(1f), cardBorderColor)
            }
            setPadding(dpToPx(20f), dpToPx(20f), dpToPx(20f), dpToPx(20f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(16f)
            }
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val sourceBtn = TextView(this).apply {
            text = "View Source Code"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = dpToPx(100f).toFloat()
            }
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dpToPx(8f)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/HeheJuice/Crash-Logs-Browser")))
            }
            setOnTouchListener(pressScaleTouchListener)
        }

        val licenseBtn = TextView(this).apply {
            text = "View GPL-3.0 License"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = dpToPx(100f).toFloat()
            }
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(8f)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/HeheJuice/Crash-Logs-Browser/blob/main/LICENSE")))
            }
            setOnTouchListener(pressScaleTouchListener)
        }

        buttonRow.addView(sourceBtn)
        buttonRow.addView(licenseBtn)
        infoCard.addView(buttonRow)
        scrollContent.addView(infoCard)

        // ----- CREDITS CARD -----
        val creditsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBgColor)
                cornerRadius = dpToPx(28f).toFloat()
                setStroke(dpToPx(1f), cardBorderColor)
            }
            setPadding(dpToPx(20f), dpToPx(24f), dpToPx(20f), dpToPx(24f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(16f)
            }
        }

        val creditsTitle = TextView(this).apply {
            text = getString(R.string.credits_title)
            textSize = 20f
            setTextColor(primaryTextColor)
            if (googleSansFlexTypeface != null) typeface = googleSansFlexTypeface
            setPadding(0, 0, 0, dpToPx(16f))
        }
        creditsCard.addView(creditsTitle)

        // ----- CREDIT 1: HeheJuice -----
        val hehejuiceName = "HeheJuice"
        val hehejuiceDesc = getString(R.string.credit_hehejuice_desc)

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12f)
            }
            gravity = Gravity.CENTER_VERTICAL
        }

        val avatarResId = resources.getIdentifier(hehejuiceName.lowercase(), "drawable", packageName)
        val avatar1 = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(48f), dpToPx(48f)).apply {
                marginEnd = dpToPx(16f)
            }
            if (avatarResId != 0) {
                setImageResource(avatarResId)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                }
                clipToOutline = true
            } else {
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accentColor)
                }
                background = drawable
                setImageDrawable(null)
            }
        }
        row1.addView(avatar1)

        if (avatarResId == 0) {
            val initialTv = TextView(this).apply {
                text = "H"
                textSize = 24f
                setTextColor(Color.WHITE)
                if (googleSansFlexTypeface != null) typeface = googleSansFlexTypeface
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(dpToPx(48f), dpToPx(48f))
            }
            val avatarContainer = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(48f), dpToPx(48f)).apply {
                    marginEnd = dpToPx(16f)
                }
                addView(avatar1)
                addView(initialTv)
            }
            row1.removeView(avatar1)
            row1.addView(avatarContainer, 0)
        }

        val textContainer1 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView1 = TextView(this).apply {
            text = hehejuiceName
            textSize = 17f
            setTextColor(primaryTextColor)
            if (googleSansFlexTypeface != null) typeface = googleSansFlexTypeface
            isClickable = true
            isFocusable = true
            setOnClickListener { openTelegram("HeheJuice") }
            setOnTouchListener(pressScaleTouchListener)
        }
        textContainer1.addView(nameView1)

        val descView1 = TextView(this).apply {
            text = hehejuiceDesc
            textSize = 14f
            setTextColor(secondaryTextColor)
            if (googleSansFlexTypeface != null) typeface = googleSansFlexTypeface
        }
        textContainer1.addView(descView1)
        row1.addView(textContainer1)
        creditsCard.addView(row1)

        // ----- CREDIT 2: Material Design -----
        val materialName = "Material Design"
        val materialDesc = "XML Vector Android Icon"

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12f)
            }
            gravity = Gravity.CENTER_VERTICAL
        }

        val materialAvatarResId = resources.getIdentifier("androidcredit", "drawable", packageName)
        val avatar2 = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(48f), dpToPx(48f)).apply {
                marginEnd = dpToPx(16f)
            }
            if (materialAvatarResId != 0) {
                setImageResource(materialAvatarResId)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                }
                clipToOutline = true
            } else {
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accentColor)
                }
                background = drawable
                setImageDrawable(null)
            }
        }
        row2.addView(avatar2)

        if (materialAvatarResId == 0) {
            val initialTv = TextView(this).apply {
                text = "M"
                textSize = 24f
                setTextColor(Color.WHITE)
                if (googleSansFlexTypeface != null) typeface = googleSansFlexTypeface
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(dpToPx(48f), dpToPx(48f))
            }
            val avatarContainer = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(48f), dpToPx(48f)).apply {
                    marginEnd = dpToPx(16f)
                }
                addView(avatar2)
                addView(initialTv)
            }
            row2.removeView(avatar2)
            row2.addView(avatarContainer, 0)
        }

        val textContainer2 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView2 = TextView(this).apply {
            text = materialName
            textSize = 17f
            setTextColor(primaryTextColor)
            if (googleSansFlexTypeface != null) typeface = googleSansFlexTypeface
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://m3.material.io/get-started")))
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        textContainer2.addView(nameView2)

        val descView2 = TextView(this).apply {
            text = materialDesc
            textSize = 14f
            setTextColor(secondaryTextColor)
            if (googleSansFlexTypeface != null) typeface = googleSansFlexTypeface
        }
        textContainer2.addView(descView2)
        row2.addView(textContainer2)
        creditsCard.addView(row2)

        // ----- CREDIT 3: Google Sans Flex -----
        val fontCreditName = "Google Sans Flex"
        val fontCreditDesc = "Fonts Used in Some UI"

        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12f)
            }
            gravity = Gravity.CENTER_VERTICAL
        }

        val fontAvatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(48f), dpToPx(48f)).apply {
                marginEnd = dpToPx(16f)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accentColor)
            }
        }

        val fontInitialTv = TextView(this).apply {
            text = "G"
            textSize = 24f
            setTextColor(Color.WHITE)
            if (googleSansFlexTypeface != null) typeface = googleSansFlexTypeface
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(48f),
                dpToPx(48f)
            )
        }

        val fontAvatarContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(48f), dpToPx(48f)).apply {
                marginEnd = dpToPx(16f)
            }
            addView(fontAvatar)
            addView(fontInitialTv)
        }
        row3.addView(fontAvatarContainer)

        val textContainer3 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView3 = TextView(this).apply {
            text = fontCreditName
            textSize = 17f
            setTextColor(primaryTextColor)
            if (googleSansFlexTypeface != null) typeface = googleSansFlexTypeface
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://fonts.google.com")))
            }
            setOnTouchListener(pressScaleTouchListener)
        }
        textContainer3.addView(nameView3)

        val descView3 = TextView(this).apply {
            text = fontCreditDesc
            textSize = 14f
            setTextColor(secondaryTextColor)
            if (googleSansFlexTypeface != null) typeface = googleSansFlexTypeface
        }
        textContainer3.addView(descView3)
        row3.addView(textContainer3)
        creditsCard.addView(row3)

        scrollContent.addView(creditsCard)
        scrollView.addView(scrollContent)
        rootFrameLayout.addView(scrollView)

        // ---------- TOP BAR ----------
        val topBarLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(16f), statusBarHeight + dpToPx(12f), dpToPx(16f), dpToPx(12f))
        }

        val topBarTitle = TextView(this).apply {
            text = getString(R.string.details_topbar_title)
            textSize = 16f
            setTextColor(primaryTextColor)
            if (googleSansFlexTypeface != null) typeface = googleSansFlexTypeface
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

        val backArrowDrawable = createArrowBackDrawable(primaryTextColor, dpToPx)
        val backBtn = ImageView(this).apply {
            setImageDrawable(backArrowDrawable)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(backBtnBgColor)
            }
            contentDescription = getString(R.string.back)
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(48f),
                dpToPx(48f),
                Gravity.START or Gravity.CENTER_VERTICAL
            )
            setOnClickListener { finish() }
            setOnTouchListener(pressScaleTouchListener)
        }

        topBarLayout.addView(topBarTitle)
        topBarLayout.addView(backBtn)
        rootFrameLayout.addView(topBarLayout)

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
            insets
        }

        setContentView(rootFrameLayout)

        val versionName = getVersionName()
        if (versionName.contains("Debug", ignoreCase = true)) {
            updateStatusView.text = getString(R.string.update_disabled_debug)
            updateActionView.visibility = View.GONE
        } else {
            checkForUpdates()
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dpToPx(36f)
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

    private fun openTelegram(username: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$username"))
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$username"))
                startActivity(intent)
            } catch (e2: Exception) {
                // ignore
            }
        }
    }

    private fun getVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val clean1 = v1.replace(Regex("[^0-9.]"), "")
        val clean2 = v2.replace(Regex("[^0-9.]"), "")
        val parts1 = clean1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = clean2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = if (i < parts1.size) parts1[i] else 0
            val p2 = if (i < parts2.size) parts2[i] else 0
            if (p1 != p2) return p1 - p2
        }
        return 0
    }

    private fun checkForUpdates() {
        updateStatusView.text = getString(R.string.update_checking)
        updateActionView.visibility = View.GONE

        Thread {
            try {
                val url = URL("https://api.github.com/repos/HeheJuice/Crash-Logs-Browser/releases/latest")
                val connection = url.openConnection() as HttpsURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val latestTag = json.getString("tag_name")
                    val currentVersion = getVersionName()

                    val latestVersion = latestTag.replace(Regex("^[^0-9]*"), "")
                    val currentVer = currentVersion.replace(Regex("^[^0-9]*"), "")

                    val comparison = compareVersions(latestVersion, currentVer)
                    runOnUiThread {
                        if (comparison > 0) {
                            updateStatusView.text = getString(R.string.update_new_version, latestVersion)
                            updateActionView.text = getString(R.string.update_download)
                            updateActionView.visibility = View.VISIBLE
                        } else {
                            updateStatusView.text = getString(R.string.update_latest, currentVer)
                        }
                    }
                } else {
                    runOnUiThread {
                        updateStatusView.text = getString(R.string.update_server_error)
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    updateStatusView.text = getString(R.string.update_connection_error)
                }
            }
        }.start()
    }

    private fun createArrowBackDrawable(color: Int, dpToPx: (Float) -> Int): android.graphics.drawable.Drawable {
        return object : android.graphics.drawable.Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
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

    private val pressScaleTouchListener = View.OnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().scaleX(0.94f).scaleY(0.94f).alpha(0.85f).setDuration(120).start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350).start()
            }
        }
        false
    }
}