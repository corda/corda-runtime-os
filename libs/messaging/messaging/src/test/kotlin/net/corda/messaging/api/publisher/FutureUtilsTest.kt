package net.corda.messaging.api.publisher

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


class FutureUtilsTest {

    companion object {
        private const val TEST_TIMEOUT_SECONDS = 20L
        private const val VERY_LONG_TIME = TEST_TIMEOUT_SECONDS * 10
    }

    private val callback = mock<(Exception, Boolean) -> Unit>()
    private val exceptionCaptor = argumentCaptor<Exception>()
    private val fatalCaptor = argumentCaptor<Boolean>()
    private val completableFutures = List(5) { _ -> CompletableFuture<Unit>() }

    @Test
    fun `empty list triggers error callback with non fatal error`() {
        waitOnPublisherFutures(listOf(), TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS, callback)
        verify(callback).invoke(exceptionCaptor.capture(), fatalCaptor.capture())

        assertTrue(exceptionCaptor.firstValue is IllegalArgumentException)
        assertFalse(fatalCaptor.firstValue)
    }

    @Test
    fun `all futures completed successfully results in no error`() {
        val t = thread {
            // Need to ensure this doesn't timeout before the test, or we could get a false positive
            waitOnPublisherFutures(completableFutures, VERY_LONG_TIME, TimeUnit.SECONDS, callback)
        }

        completableFutures.forEach { it.complete(Unit) }
        t.join(TEST_TIMEOUT_SECONDS * 1000)
        assertEquals(Thread.State.TERMINATED, t.state)
        verify(callback, never()).invoke(any(), any())
    }

    @Test
    fun `intermittent error results in non fatal callback`() {
        val t = thread {
            // Need to ensure this doesn't timeout before the test, or we could get a false positive
            waitOnPublisherFutures(completableFutures, VERY_LONG_TIME, TimeUnit.SECONDS, callback)
        }

        completableFutures.forEachIndexed() { index, it ->
            if (index == 3) {
                it.completeExceptionally(CordaMessageAPIIntermittentException(""))
            } else {
                it.complete(Unit)
            }
        }

        t.join(TEST_TIMEOUT_SECONDS * 1000)
        assertEquals(Thread.State.TERMINATED, t.state)
        verify(callback).invoke(exceptionCaptor.capture(), fatalCaptor.capture())

        assertTrue(exceptionCaptor.firstValue is CordaMessageAPIIntermittentException)
        assertFalse(fatalCaptor.firstValue)
    }

    @Test
    fun `fatal error results in fatal callback`() {
        val t = thread {
            // Need to ensure this doesn't timeout before the test, or we could get a false positive
            waitOnPublisherFutures(completableFutures, VERY_LONG_TIME, TimeUnit.SECONDS, callback)
        }

        completableFutures.forEachIndexed() { index, it ->
            if (index == 3) {
                it.completeExceptionally(CordaMessageAPIFatalException(""))
            } else {
                it.complete(Unit)
            }
        }

        t.join(TEST_TIMEOUT_SECONDS * 1000)
        assertEquals(Thread.State.TERMINATED, t.state)
        verify(callback).invoke(exceptionCaptor.capture(), fatalCaptor.capture())

        assertThat(exceptionCaptor.firstValue).isInstanceOf(CordaMessageAPIFatalException::class.java)
        assertTrue(fatalCaptor.firstValue)
    }

    @Test
    fun `fatal and intermittent error results in fatal callback`() {
        val t = thread {
            // Need to ensure this doesn't timeout before the test, or we could get a false positive
            waitOnPublisherFutures(completableFutures, VERY_LONG_TIME, TimeUnit.SECONDS, callback)
        }

        completableFutures.forEachIndexed() { index, it ->
            if (index == 2) {
                it.completeExceptionally(CordaMessageAPIIntermittentException(""))
            } else if (index == 3) {
                it.completeExceptionally(CordaMessageAPIFatalException(""))
            } else {
                it.complete(Unit)
            }
        }

        t.join(TEST_TIMEOUT_SECONDS * 1000)
        assertEquals(Thread.State.TERMINATED, t.state)
        verify(callback).invoke(exceptionCaptor.capture(), fatalCaptor.capture())

        assertThat(exceptionCaptor.firstValue).isInstanceOf(CordaMessageAPIFatalException::class.java)
        assertTrue(fatalCaptor.firstValue)
    }

    @Test
    fun `non Corda exception results in non fatal callback`() {
        val t = thread {
            // Need to ensure this doesn't timeout before the test, or we could get a false positive
            waitOnPublisherFutures(completableFutures, VERY_LONG_TIME, TimeUnit.SECONDS, callback)
        }

        completableFutures.forEachIndexed() { index, it ->
            if (index == 3) {
                it.completeExceptionally(IllegalAccessException())
            } else {
                it.complete(Unit)
            }
        }

        t.join(TEST_TIMEOUT_SECONDS * 1000)
        assertEquals(Thread.State.TERMINATED, t.state)
        verify(callback).invoke(exceptionCaptor.capture(), fatalCaptor.capture())

        assertThat(exceptionCaptor.firstValue).isInstanceOf(IllegalAccessException::class.java)
        assertFalse(fatalCaptor.firstValue)
    }
}
