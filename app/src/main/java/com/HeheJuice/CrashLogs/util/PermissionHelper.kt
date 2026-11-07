package com.HeheJuice.CrashLogs.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

object PermissionHelper {

    const val READ_LOGS = "android.permission.READ_LOGS"
    const val READ_DROPBOX_DATA = "android.permission.READ_DROPBOX_DATA"
    const val PACKAGE_USAGE_STATS = "android.permission.PACKAGE_USAGE_STATS"

    fun requiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= 35) {
            listOf(PACKAGE_USAGE_STATS, READ_DROPBOX_DATA, READ_LOGS)
        } else {
            listOf(PACKAGE_USAGE_STATS, READ_LOGS)
        }
    }

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED

    fun hasAllPermissions(context: Context): Boolean =
        requiredPermissions().all { hasPermission(context, it) }

    fun buildAdbGrantCommand(context: Context, permission: String): String =
        "adb shell pm grant ${context.packageName} $permission"

    // ─────────────────────────────────────────────────────────────
    // Root 自己授權
    // ─────────────────────────────────────────────────────────────

    data class RootGrantResult(val success: Boolean, val output: String)

    fun grantAllWithRoot(
        context: Context,
        timeoutSeconds: Long = 20
    ): RootGrantResult {
        val perms = requiredPermissions()
        val pkg = context.packageName
        val script = buildString {
            perms.forEach { append("pm grant $pkg $it\n") }
            append("exit\n")
        }
        return try {
            val proc = Runtime.getRuntime().exec("su")
            proc.outputStream.use { it.write(script.toByteArray()) }
            val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return RootGrantResult(false, "timeout after ${timeoutSeconds}s")
            }
            val out = proc.inputStream.bufferedReader().readText().trim()
            val err = proc.errorStream.bufferedReader().readText().trim()
            val combined = listOf(out, err).filter { it.isNotEmpty() }.joinToString("\n")
            RootGrantResult(proc.exitValue() == 0, combined)
        } catch (e: Exception) {
            RootGrantResult(false, e.message ?: e.javaClass.simpleName)
        }
    }
}