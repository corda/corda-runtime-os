package net.corda.crypto.client.impl

import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.v5.crypto.failures.CryptoException
import net.corda.v5.crypto.failures.CryptoRetryException
import net.corda.v5.crypto.failures.CryptoSignatureException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID
import kotlin.test.assertEquals

class ExceptionsUtilsTests {
    companion object {
        @JvmStatic
        fun knownWrappedExceptions() = listOf(
            Pair(IllegalArgumentException::class.java.name, IllegalArgumentException::class.java),
            Pair(IllegalStateException::class.java.name, IllegalStateException::class.java),
            Pair(CryptoSignatureException::class.java.name, CryptoSignatureException::class.java),
            Pair(CryptoRetryException::class.java.name, CryptoRetryException::class.java)
        )
    }

    @ParameterizedTest
    @MethodSource("knownWrappedExceptions")
    fun `toClientException should return known wrapped exception`(
        error: Pair<String, Class<out Throwable>>
    ) {
        val responderException = CordaRPCAPIResponderException(
            errorType = error.first,
            message = UUID.randomUUID().toString()
        )
        val actual = responderException.toClientException()
        assertThat(actual).isInstanceOf(error.second)
        assertEquals(responderException.message, actual.message)
    }

    @Test
    fun `toClientException should return CryptoException for unknown wrapped exception`() {
        val responderException = CordaRPCAPIResponderException(
            errorType = RuntimeException::class.java.name,
            message = UUID.randomUUID().toString()
        )
        val actual = responderException.toClientException()
        assertEquals(CryptoException::class.java, actual::class.java)
        assertEquals(responderException.message, actual.message)
    }

    @Test
    fun `exceptionFactories should contain only known exceptions`() {
        assertThat(exceptionFactories).hasSize(4)
        assertThat(exceptionFactories.keys).contains(
            IllegalArgumentException::class.java.name,
            IllegalStateException::class.java.name,
            CryptoSignatureException::class.java.name,
            CryptoRetryException::class.java.name
        )
    }
}