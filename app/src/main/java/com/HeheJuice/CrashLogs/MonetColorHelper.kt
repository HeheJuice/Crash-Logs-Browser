package com.HeheJuice.CrashLogs

import android.app.Activity
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR

object MonetColorHelper {
    @JvmStatic
    fun getColor(activity: Activity, attr: Int): Int {
        return MaterialColors.getColor(activity, attr, 0)
    }
}