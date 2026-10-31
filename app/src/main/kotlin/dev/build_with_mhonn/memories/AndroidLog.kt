package dev.build_with_mhonn.memories

import android.util.Log
import dev.build_with_mhonn.memories.core.AppLog
import dev.build_with_mhonn.memories.core.ReleaseLog

/** Forwards every level to [android.util.Log]. */
object AndroidLog : AppLog {
    override fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    override fun i(tag: String, msg: String) {
        Log.i(tag, msg)
    }

    override fun w(tag: String, msg: String) {
        Log.w(tag, msg)
    }

    override fun e(tag: String, msg: String) {
        Log.e(tag, msg)
    }
}

/** The log for this build: everything in debug builds, warn and error only in release (E1.12). */
val appLog: AppLog = if (BuildConfig.DEBUG) AndroidLog else ReleaseLog(AndroidLog)
