package com.github.legiontube.util

import com.github.legiontube.helpers.PreferenceHelper

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExceptionHandler(
    private val context: android.content.Context,
    private val defaultExceptionHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, exc: Throwable) {
        // OkHttp spawns threads to parse the response headers
        // if an exception is on a thread spawned by OkHttp, there's no apparent other way to catch them
        // work around for Cronet spawning different threads with uncaught Exception when used with Coil
        // for example this catches crashes when there are invalid values in the header
        if (thread.name == OKHTTP_THREAD_NAME) return

        // save the error log in SharedPreferences (for UI dialog)
        PreferenceHelper.saveErrorLog(exc.stackTraceToString())
        
        // save to physical file system
        try {
            val crashDir = File(context.getExternalFilesDir(null), "CrashLogs")
            if (!crashDir.exists()) crashDir.mkdirs()
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val crashFile = File(crashDir, "crash_log_$timestamp.txt")
            crashFile.writeText(exc.stackTraceToString())
        } catch (e: Exception) {
            // Ignored, we are already crashing natively
        }
        
        // throw the exception with the default exception handler to make the app crash
        defaultExceptionHandler?.uncaughtException(thread, exc)
    }

    companion object {
        private const val OKHTTP_THREAD_NAME = "OkHttp Dispatcher"
    }
}
