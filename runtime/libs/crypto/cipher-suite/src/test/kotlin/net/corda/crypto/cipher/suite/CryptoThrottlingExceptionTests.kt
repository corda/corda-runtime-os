package net.corda.crypto.cipher.suite

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class CryptoThrottlingExceptionTests {
    @Test
    fun `Should return default linear backoff with message only`() {
        val e =  CryptoThrottlingException("Something wrong.")
        assertNull(e.cause)
        var backoff = e.getBackoff(1)
        assertEquals(200L, backoff)
        backoff = e.getBackoff(2)
        assertEquals(200L, backoff)
        backoff = e.getBackoff(3)
        assertEquals(-1L, backoff)
    }

    @Test
    fun `Should return customized backoff with message only`() {
        val e =  CryptoThrottlingException("Something wrong.", listOf(100, 200))
        assertNull(e.cause)
        var backoff = e.getBackoff(1)
        assertEquals(100L, backoff)
        backoff = e.getBackoff(2)
        assertEquals(200L, backoff)
        backoff = e.getBackoff(3)
        assertEquals(-1L, backoff)
    }

    @Test
    fun `Should return default linear backoff with message and cause`() {
        val cause = RuntimeException()
        val e =  CryptoThrottlingException("Something wrong.", cause)
        assertSame(cause, e.cause)
        var backoff = e.getBackoff(1)
        assertEquals(200L, backoff)
        backoff = e.getBackoff(2)
        assertEquals(200L, backoff)
        backoff = e.getBackoff(3)
        assertEquals(-1L, backoff)
    }

    @Test
    fun `Should return customized backoff with message and cause`() {
        val cause = RuntimeException()
        val e =  CryptoThrottlingException("Something wrong.", cause, listOf(100, 200))
        assertSame(cause, e.cause)
        var backoff = e.getBackoff(1)
        assertEquals(100L, backoff)
        backoff = e.getBackoff(2)
        assertEquals(200L, backoff)
        backoff = e.getBackoff(3)
        assertEquals(-1L, backoff)
    }

    @Test
    fun `Should return default exponential backoff with message only`() {
        val e =  CryptoThrottlingException.createExponential("Something wrong.")
        assertNull(e.cause)
        var backoff = e.getBackoff(1,)
        assertEquals(1000L, backoff)
        backoff = e.getBackoff(2)
        assertEquals(2000L, backoff)
        backoff = e.getBackoff(3)
        assertEquals(4000L, backoff)
        backoff = e.getBackoff(4)
        assertEquals(8000L, backoff)
        backoff = e.getBackoff(5)
        assertEquals(16000L, backoff)
        backoff = e.getBackoff(6)
        assertEquals(-1, backoff)
    }

    @Test
    fun `Should return customized exponential backoff with message only`() {
        val e =  CryptoThrottlingException.createExponential("Something wrong.", 4, 3000)
        assertNull(e.cause)
        var backoff = e.getBackoff(1)
        assertEquals(3000L, backoff)
        backoff = e.getBackoff(2)
        assertEquals(6000L, backoff)
        backoff = e.getBackoff(3)
        assertEquals(12000L, backoff)
        backoff = e.getBackoff(4)
        assertEquals(-1, backoff)
    }

    @Test
    fun `Should return default exponential backoff with message and cause`() {
        val cause = RuntimeException()
        val e =  CryptoThrottlingException.createExponential("Something wrong.", cause)
        assertSame(cause, e.cause)
        var backoff = e.getBackoff(1)
        assertEquals(1000L, backoff)
        backoff = e.getBackoff(2)
        assertEquals(2000L, backoff)
        backoff = e.getBackoff(3)
        assertEquals(4000L, backoff)
        backoff = e.getBackoff(4)
        assertEquals(8000L, backoff)
        backoff = e.getBackoff(5)
        assertEquals(16000L, backoff)
        backoff = e.getBackoff(6)
        assertEquals(-1, backoff)
    }

    @Test
    fun `Should return customized exponential backoff with message and cause`() {
        val cause = RuntimeException()
        val e =  CryptoThrottlingException.createExponential("Something wrong.", cause, 4, 3000)
        assertSame(cause, e.cause)
        var backoff = e.getBackoff(1)
        assertEquals(3000L, backoff)
        backoff = e.getBackoff(2)
        assertEquals(6000L, backoff)
        backoff = e.getBackoff(3)
        assertEquals(12000L, backoff)
        backoff = e.getBackoff(4)
        assertEquals(-1, backoff)
    }
}