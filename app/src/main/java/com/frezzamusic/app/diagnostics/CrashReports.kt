package com.frezzamusic.app.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.frezzamusic.app.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/** Opt-in local crash diagnostics; does not upload data or collect audio. */
object CrashReports {
    private const val PREFS = "diagnostic_reports"
    private const val KEY_PENDING = "pending"
    private const val KEY_REPORT = "report"
    private const val MAX_CHARS = 12000
    private val lastAction = AtomicReference("startup")
    private var installed = false

    fun action(value: String) { lastAction.set(value.take(64)) }

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val report = format("UNCAUGHT_EXCEPTION", stack, lastAction.get())
                appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_REPORT, report).putBoolean(KEY_PENDING, true).commit()
            } catch (_: Throwable) { }
            if (previous != null) previous.uncaughtException(thread, error)
            else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    fun hasPending(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_PENDING, false)

    fun report(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_REPORT, null)
        if (!saved.isNullOrBlank()) return saved
        // Android 11+ can report a process death even when the exception handler did not run.
        if (Build.VERSION.SDK_INT >= 30) {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val exit = manager.getHistoricalProcessExitReasons(context.packageName, 0, 8)
                .firstOrNull { it.reason == android.app.ApplicationExitInfo.REASON_CRASH ||
                    it.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE ||
                    it.reason == android.app.ApplicationExitInfo.REASON_ANR }
            if (exit != null) return format("ANDROID_EXIT_${exit.reason}", "Process exit captured by Android; stack trace unavailable.", lastAction.get())
        }
        return format("MANUAL_DIAGNOSTIC", "No captured exception available.", lastAction.get())
    }

    fun acknowledge(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PENDING, false).apply()
    }

    private fun scrub(input: String): String = input
        .replace(Regex("(?i)(https?://)[^\\s)]+"), "[URL redacted]")
        .replace(Regex("(?i)(bearer\\s+)[^\\s]+"), "[credential redacted]")
        .replace(Regex("(?i)(token|api[_-]?key|password|authorization)[=:]\\s*[^\\s]+"), "[credential redacted]")
        .take(MAX_CHARS)

    private fun format(type: String, detail: String, action: String) = buildString {
        appendLine("FREZZAMUSIC diagnostic report")
        appendLine("Application: ${BuildConfig.PROJECT_MODE}")
        appendLine("Version: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
        appendLine("When: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Last action: $action")
        appendLine("Failure: $type")
        appendLine("Stack trace (sanitized):")
        appendLine(scrub(detail))
    }

    fun share(context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${BuildConfig.PROJECT_MODE} — relatório de erro")
            putExtra(Intent.EXTRA_TEXT, report(context))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Enviar relatório por e-mail, WhatsApp ou outro aplicativo")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
