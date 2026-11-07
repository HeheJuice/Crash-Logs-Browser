package com.HeheJuice.CrashLogs.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.HeheJuice.CrashLogs.util.MonetColorManager
import com.HeheJuice.CrashLogs.util.OptimizeEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EngineStart : Application() {

    @Volatile
    private var lastKnownSeed: Int = 0

    private val paletteHandler = Handler(Looper.getMainLooper())

    // ────────────────────────────────────────────────────────────────
    // 供 UI 订阅的版本号（每次调色板变化 +1）
    // ────────────────────────────────────────────────────────────────

    private val _monetVersion = MutableStateFlow(0L)

    /**
     * 每当调色板 / 壁纸 / 深浅色变化时递增。
     * 订阅方只需关心「值变了」这件事，不需要读取 seed color 本身。
     */
    val monetVersion: StateFlow<Long> = _monetVersion.asStateFlow()

    // ────────────────────────────────────────────────────────────────
    // 变化侦测
    // ────────────────────────────────────────────────────────────────

    private fun checkPaletteChanged(forceRefresh: Boolean = false) {
        val newSeed = try {
            MonetColorManager.getCurrentSeedColor(this)
        } catch (_: Exception) {
            0
        }

        val changed = newSeed != 0 && newSeed != lastKnownSeed

        if (changed || forceRefresh) {
            if (changed) lastKnownSeed = newSeed

            runCatching { MonetColorManager.clearCache() }
            runCatching { OptimizeEngine.clearCaches() }

            // 主线程调用（onConfigurationChanged / Handler.post / BroadcastReceiver 都在主线程），
            // 单写者，直接 ++ 即可。
            _monetVersion.value = _monetVersion.value + 1L
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        paletteHandler.post { checkPaletteChanged() }
    }

    // ────────────────────────────────────────────────────────────────
    // 壁纸变化
    // ────────────────────────────────────────────────────────────────

    private val wallpaperReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_WALLPAPER_CHANGED) return

            runCatching { MonetColorManager.clearCache() }
            checkPaletteChanged(forceRefresh = true)

            // 有些 ROM 读 seed color 会滞后，延后多查两次
            paletteHandler.postDelayed({ checkPaletteChanged() }, 500L)
            paletteHandler.postDelayed({ checkPaletteChanged() }, 2000L)
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Lifecycle
    // ────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        lastKnownSeed = try {
            MonetColorManager.getCurrentSeedColor(this)
        } catch (_: Exception) {
            0
        }

        try {
            ContextCompat.registerReceiver(
                this,
                wallpaperReceiver,
                IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) { }
    }
}