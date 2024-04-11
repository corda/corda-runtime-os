package net.corda.ledger.verification.processor.impl

import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.flow.external.events.responses.exceptions.CpkNotAvailableException
import net.corda.flow.external.events.responses.exceptions.NotAllowedCpkException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.NotSerializableException
import java.util.stream.Stream

class VerificationExceptionCatergorizerImplTest {

    private companion object {
        @JvmStatic
        fun platformExceptions() : Stream<Arguments> {
            return Stream.of(
                Arguments.of(NotAllowedCpkException("foo")),
                Arguments.of(NotSerializableException())
            )
        }

        @JvmStatic
        fun fatalExceptions() : Stream<Arguments> {
            return Stream.of(
                Arguments.of(CpkNotAvailableException("bar"))
            )
        }
    }

    @ParameterizedTest(name = "{0} is categorized as a fatal exception")
    @MethodSource("fatalExceptions")
    fun `fatal errors are correctly categorized`(exception: Exception) {
        val categorizer = VerificationExceptionCategorizerImpl()
        val result = categorizer.categorize(exception)
        assertThat(result).isEqualTo(ExternalEventResponseErrorType.FATAL)
    }

    @ParameterizedTest(name = "{0} is categorized as a platform exception")
    @MethodSource("platformExceptions")
    fun `platform errors are correctly categorized`(exception: Exception) {
        val categorizer = VerificationExceptionCategorizerImpl()
        val result = categorizer.categorize(exception)
        assertThat(result).isEqualTo(ExternalEventResponseErrorType.PLATFORM)
    }

    @Test
    fun `error categorization defaults to platform`() {
        val categorizer = VerificationExceptionCategorizerImpl()
        val result = categorizer.categorize(IllegalStateException())
        assertThat(result).isEqualTo(ExternalEventResponseErrorType.PLATFORM)
    }
}