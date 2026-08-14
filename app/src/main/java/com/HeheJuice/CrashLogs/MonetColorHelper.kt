package com.HeheJuice.CrashLogs

import android.app.Activity
import com.google.android.material.color.MaterialColors

/**
 * 工具类，用于获取 Android 12+ 系统的动态主题颜色（Monet）。
 * 由于 minSdk = 35，无需兼容旧版本，直接使用 MaterialColors 即可。
 */
object MonetColorHelper {

    /**
     * 从当前 Activity 主题中获取指定的颜色属性（如 colorPrimary）。
     * @param activity 当前的 Activity 上下文
     * @param attr 颜色属性，例如 com.google.android.material.R.attr.colorPrimary
     * @return 解析后的颜色值（Int）
     */
    @JvmStatic
    fun getColor(activity: Activity, attr: Int): Int {
        return MaterialColors.getColor(activity, attr, 0)
    }

    /**
     * 获取颜色，并提供一个 fallback（以防万一）。
     * @param activity 当前的 Activity 上下文
     * @param attr 颜色属性
     * @param fallback 当获取失败时返回的默认颜色
     * @return 颜色值
     */
    @JvmStatic
    fun getColor(activity: Activity, attr: Int, fallback: Int): Int {
        return MaterialColors.getColor(activity, attr, fallback)
    }
}