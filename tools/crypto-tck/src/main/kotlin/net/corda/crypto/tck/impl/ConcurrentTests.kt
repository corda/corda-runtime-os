package net.corda.crypto.tck.impl

import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Copy from :testing:testing-utils as that module is not published.
 */
class ConcurrentTests(
    private val joinTimeoutMilliseconds: Long = 60_000,
    private val startTimeoutMilliseconds: Long = 5_000
) {
    companion object {

        fun <S> Iterable<S>.createTestCase(block: (state: S) -> Unit): ConcurrentTests {
            val tests = ConcurrentTests()
            tests.create(this, block)
            return tests
        }
    }

    private val latch = CountDownLatch(1)
    private val exceptions = ConcurrentHashMap<Throwable, Unit>()
    private val threads = mutableListOf<Thread>()

    fun <S> create(sequence: Iterable<S>, block: (state: S) -> Unit) {
        sequence.forEach { state ->
            val thread = thread(start = true) {
                latch.await(startTimeoutMilliseconds, TimeUnit.MILLISECONDS)
                block(state)
            }.also { it.setUncaughtExceptionHandler { _, e -> exceptions[e] = Unit } }
            threads.add(thread)
        }
    }

    fun runAndValidate(reportFirstNExceptions: Int = 1) {
        run()
        validateResult(reportFirstNExceptions)
    }

    private fun run(): List<Throwable> {
        latch.countDown()
        threads.forEach {
            it.join(joinTimeoutMilliseconds)
        }
        return exceptions.keys().toList()
    }

    private fun validateResult(reportFirstNExceptions: Int = 1) {
        if (exceptions.isNotEmpty()) {
            throw AssertionError(
                exceptions.keys
                    .take(Integer.min(reportFirstNExceptions, 10))
                    .joinToString("${System.lineSeparator()}>>>>${System.lineSeparator()}") {
                        printException(it)
                    }
            )
        }
    }

    private fun printException(e: Throwable): String {
        return StringWriter().use { sw ->
            PrintWriter(sw).use { pw ->
                printException(e, pw)
                pw.println("<<<<<<<<<<<<")
            }
            sw.toString()
        }
    }

    private fun printException(e: Throwable, pw: PrintWriter) {
        e.printStackTrace(pw)
        if (e.cause != null) {
            pw.print("Caused by: ")
            printException(e.cause!!, pw)
        }
    }
}

