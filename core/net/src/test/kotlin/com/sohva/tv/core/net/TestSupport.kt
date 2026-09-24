package com.sohva.tv.core.net

import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

class RecordingLog : DiagnosticsLog {
    val lines: MutableList<String> = CopyOnWriteArrayList()

    override fun info(event: String, message: String) {
        lines += "$event $message"
    }

    override fun error(event: String, message: String?, error: Throwable?) {
        lines += "$event $message"
    }

    override fun snapshot(): List<String> = lines.toList()
}

const val TEST_AGENT: String = "Sohva TV/0.2.0-test (Android TV 11)"

inline fun expectError(expected: AppError, block: () -> Unit) {
    try {
        block()
        fail("expected $expected")
    } catch (e: AppException) {
        assertEquals(expected, e.error)
    }
}
