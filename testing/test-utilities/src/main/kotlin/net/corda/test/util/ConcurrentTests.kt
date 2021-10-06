package net.corda.test.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Useful to test concurrent execution. Like
 *
 *      threads.create(1..100) { i ->
 *          // test case
 *      }
 *      threads.runAndValidate()
 *
 *  or using the extension function
 *
 *      (1..100).createTestCase { i ->
 *          // test case
 *      }.runAndValidate()
 */
class ConcurrentTests(
    private val joinTimeoutMilliseconds: Long = 25_000,
    private val startTimeoutMilliseconds: Long = 5_000,
) {
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

    fun run(): List<Throwable> {
        latch.countDown()
        threads.forEach {
            it.join(joinTimeoutMilliseconds)
        }
        return exceptions.keys().toList()
    }

    fun validateResult() {
        if(exceptions.isNotEmpty()) {
            throw AssertionError(
                exceptions.keys.joinToString( "${System.lineSeparator()}>>>>${System.lineSeparator()}")
            )
        }
    }

    fun runAndValidate() {
        run()
        validateResult()
    }
}

fun <S> Iterable<S>.createTestCase(block: (state: S) -> Unit): ConcurrentTests {
    val tests = ConcurrentTests()
    tests.create(this, block)
    return tests
}

fun <S> Iterable<S>.createTestCase(
    joinTimeoutMilliseconds: Long,
    startTimeoutMilliseconds: Long = 5_000,
    block: (state: S) -> Unit
): ConcurrentTests {
    val tests = ConcurrentTests(startTimeoutMilliseconds, joinTimeoutMilliseconds)
    tests.create(this, block)
    return tests
}