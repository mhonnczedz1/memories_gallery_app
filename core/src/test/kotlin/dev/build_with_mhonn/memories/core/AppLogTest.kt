package dev.build_with_mhonn.memories.core

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLogTest {
    private class RecordingLog : AppLog {
        val lines = mutableListOf<String>()

        override fun d(tag: String, msg: String) {
            lines += "D/$tag: $msg"
        }

        override fun i(tag: String, msg: String) {
            lines += "I/$tag: $msg"
        }

        override fun w(tag: String, msg: String) {
            lines += "W/$tag: $msg"
        }

        override fun e(tag: String, msg: String) {
            lines += "E/$tag: $msg"
        }
    }

    private fun logAllLevels(log: AppLog) {
        log.d("Tag", "debug")
        log.i("Tag", "info")
        log.w("Tag", "warn")
        log.e("Tag", "error")
    }

    @Test
    fun `release log drops debug and info and forwards warn and error`() {
        val delegate = RecordingLog()

        logAllLevels(ReleaseLog(delegate))

        assertEquals(listOf("W/Tag: warn", "E/Tag: error"), delegate.lines)
    }

    @Test
    fun `debug log prints every level`() {
        val out = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(out, true))
        try {
            logAllLevels(DebugLog)
        } finally {
            System.setOut(original)
        }

        assertEquals(
            listOf("D/Tag: debug", "I/Tag: info", "W/Tag: warn", "E/Tag: error"),
            out.toString().lines().filter { it.isNotEmpty() }
        )
    }
}
