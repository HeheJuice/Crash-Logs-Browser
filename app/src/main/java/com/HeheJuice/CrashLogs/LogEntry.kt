package com.HeheJuice.CrashLogs

data class LogEntry(
    val timestamp: String,
    val appName: String,
    val type: String,      // "Crash" 或 "ANR"
    val details: String
)