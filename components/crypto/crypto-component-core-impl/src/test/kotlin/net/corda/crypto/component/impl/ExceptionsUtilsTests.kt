package net.corda.crypto.component.impl

import net.corda.crypto.core.InvalidParamsException
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.messaging.api.exception.CordaRestAPIResponderException
import net.corda.v5.crypto.exceptions.CryptoException
import net.corda.v5.crypto.exceptions.CryptoRetryException
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class ExceptionsUtilsTests {
    companion object {
        @JvmStatic
        fun knownWrappedExceptions() = listOf(
            IllegalArgumentException::class.java,
            IllegalStateException::class.java,
            CryptoSignatureException::class.java,
            CryptoRetryException::class.java,
            KeyAlreadyExistsException::class.java,
            InvalidParamsException::class.java,
        )
    }

    @ParameterizedTest
    @MethodSource("knownWrappedExceptions")
    fun `toClientException should return known wrapped exception`(
        error: Class<out Throwable>
    ) {
        val responderException = CordaRestAPIResponderException(
            errorType = error.name,
            message = "Error: ${error.name}"
        )

        val actual = responderException.toClientException()

        assertThat(actual)
            .hasMessage(responderException.message)
            .isInstanceOf(error)
    }

    @Test
    fun `toClientException should return CryptoException for unknown wrapped exception`() {
        val responderException = CordaRestAPIResponderException(
            errorType = RuntimeException::class.java.name,
            message = "Unknown"
        )

        val actual = responderException.toClientException()

        assertThat(actual)
            .hasMessage(responderException.message)
            .isInstanceOf(CryptoException::class.java)
    }

    @Test
    fun `exceptionFactories should contain only known exceptions`() {
        assertThat(exceptionFactories)
            .containsOnlyKeys(
                knownWrappedExceptions()
                    .map {
                        it.name
                    }
            )
    }
}
