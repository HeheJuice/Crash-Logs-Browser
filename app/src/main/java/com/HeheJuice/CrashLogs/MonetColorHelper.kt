package com.HeheJuice.CrashLogs

import android.app.Activity
import com.google.android.material.color.MaterialColors

object MonetColorHelper {
    @JvmStatic
    fun getColor(activity: Activity, attr: Int): Int {
        return MaterialColors.getColor(activity, attr, 0)
    }

    @JvmStatic
    fun getColor(activity: Activity, attr: Int, fallback: Int): Int {
        return MaterialColors.getColor(activity, attr, fallback)
    }
}