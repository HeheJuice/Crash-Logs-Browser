package com.HeheJuice.CrashLogs

import android.app.Application
import com.google.android.material.color.DynamicColors

class CrashLogsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 全局应用 Monet 动态颜色（适用于所有 Activity）
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}