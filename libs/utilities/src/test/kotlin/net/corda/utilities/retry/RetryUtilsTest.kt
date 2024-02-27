package net.corda.utilities.retry

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

class RetryUtilsTest {
    private val backoffStrategy = mock<BackoffStrategy>()

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Test
    fun tryWithBackoffDoesNotRetryOnSuccessfulExecution() {
        val executor = Executor(1)
        val executionResult =
            tryWithBackoff(logger, maxRetries = 3, maxTimeMillis = Long.MAX_VALUE, backoffStrategy = backoffStrategy) {
                executor.execute("dummy")
            }

        verifyNoInteractions(backoffStrategy)
        assertThat(executor.calls).isEqualTo(1)
        assertThat(executionResult).isEqualTo("dummy")
    }

    @Test
    fun tryWithBackoffDoesNotRetryForNonRetryableExceptions() {
        val executor = Executor(2)
        assertThatThrownBy {
            tryWithBackoff(logger, maxRetries = 3, maxTimeMillis = Long.MAX_VALUE, backoffStrategy = backoffStrategy) {
                executor.execute("")
            }
        }.isInstanceOf(RecoverableException::class.java).hasMessage("Dummy Exception")

        verifyNoInteractions(backoffStrategy)
        assertThat(executor.calls).isEqualTo(1)
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -10])
    fun tryWithBackoffDoesNotRetryWhenMaxAttemptsIsNegativeOrZero(maxRetries: Long) {
        val executor = Executor(Int.MAX_VALUE)
        assertThatThrownBy {
            tryWithBackoff(
                logger,
                maxRetries = maxRetries,
                maxTimeMillis = Long.MAX_VALUE,
                backoffStrategy = backoffStrategy,
                shouldRetry = { _, _, t -> t is RecoverableException }
            ) {
                executor.execute("")
            }
        }.isInstanceOf(RecoverableException::class.java).hasMessage("Dummy Exception")

        verifyNoInteractions(backoffStrategy)
        assertThat(executor.calls).isEqualTo(1)
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -10])
    fun tryWithBackoffDoesNotRetryWhenMaxTimeMillisIsNegativeOrZero(maxTimeMillis: Long) {
        val executor = Executor(Int.MAX_VALUE)
        assertThatThrownBy {
            tryWithBackoff(
                logger,
                maxRetries = Long.MAX_VALUE,
                maxTimeMillis = maxTimeMillis,
                backoffStrategy = backoffStrategy,
                shouldRetry = { _, _, t -> t is RecoverableException }
            ) {
                executor.execute("")
            }
        }.isInstanceOf(RecoverableException::class.java).hasMessage("Dummy Exception")

        verifyNoInteractions(backoffStrategy)
        assertThat(executor.calls).isEqualTo(1)
    }

    @Test
    fun tryWithBackoffRetriesOnFailureAndStopsRetryingOnSuccessfulExecution() {
        val executor = Executor(5)
        val executionResult = tryWithBackoff(
            logger,
            maxRetries = 10,
            maxTimeMillis = Long.MAX_VALUE,
            backoffStrategy = backoffStrategy,
            shouldRetry = { _, _, t -> t is RecoverableException },
        ) {
            executor.execute("dummy")
        }

        assertThat(executor.calls).isEqualTo(5)
        assertThat(executionResult).isEqualTo("dummy")
        verify(backoffStrategy, times(4)).delay(any())
    }

    @Test
    fun tryWithBackoffRetriesOnFailureAndStopsRetryingWhenDelayIsNegative() {
        val executor = Executor(10)
        whenever(backoffStrategy.delay(any()))
            .thenReturn(1)
            .thenReturn(2)
            .thenReturn(-1)

        val thrownException = catchThrowable {
            tryWithBackoff(
                logger,
                maxRetries = Long.MAX_VALUE,
                maxTimeMillis = Long.MAX_VALUE,
                backoffStrategy = backoffStrategy,
                shouldRetry = { _, _, t -> t is RecoverableException },
            ) {
                executor.execute("")
            }
        }

        assertThat(executor.calls).isEqualTo(3)
        assertThat(thrownException)
            .isInstanceOf(RetryException::class.java)
            .hasCauseInstanceOf(RecoverableException::class.java)
            .hasRootCauseMessage("Dummy Exception")
        verify(backoffStrategy, times(3)).delay(any())
    }

    @Test
    fun tryWithBackoffRetriesOnFailureAndStopsRetryingWhenMaxAttemptsIsReached() {
        val executor = Executor(5)
        val thrownException = catchThrowable {
            tryWithBackoff(
                logger,
                maxRetries = 3,
                maxTimeMillis = Long.MAX_VALUE,
                backoffStrategy = backoffStrategy,
                shouldRetry = { _, _, t -> t is RecoverableException },
            ) {
                executor.execute("")
            }
        }

        assertThat(executor.calls).isEqualTo(3)
        assertThat(thrownException)
            .isInstanceOf(RetryException::class.java)
            .hasCauseInstanceOf(RecoverableException::class.java)
            .hasRootCauseMessage("Dummy Exception")
        verify(backoffStrategy, times(3)).delay(any())
    }

    @Test
    fun tryWithBackoffRetriesOnFailureAndStopsRetryingWhenMaxTimeIsReached() {
        val executor = Executor(Int.MAX_VALUE)
        whenever(backoffStrategy.delay(any())).thenReturn(1.seconds.inWholeMilliseconds)

        val thrownException = catchThrowable {
            tryWithBackoff(
                logger,
                maxRetries = Long.MAX_VALUE,
                maxTimeMillis = 2.seconds.inWholeMilliseconds,
                backoffStrategy = backoffStrategy,
                shouldRetry = { _, _, t -> t is RecoverableException },
            ) {
                executor.execute("")
            }
        }

        assertThat(executor.calls).isGreaterThanOrEqualTo(1)
        assertThat(thrownException)
            .isInstanceOf(RetryException::class.java)
            .hasCauseInstanceOf(RecoverableException::class.java)
            .hasRootCauseMessage("Dummy Exception")
        verify(backoffStrategy, atLeast(1)).delay(any())
    }

    @Test
    fun tryWithBackoffRetriesOnFailureAndThrowsCustomException() {
        val executor = Executor(Int.MAX_VALUE)
        val thrownException = catchThrowable {
            tryWithBackoff(
                logger,
                maxRetries = 1,
                maxTimeMillis = Long.MAX_VALUE,
                backoffStrategy = backoffStrategy,
                shouldRetry = { _, _, t -> t is RecoverableException },
                onRetryExhaustion = { _, _, t -> CustomException("", t) }
            ) {
                executor.execute("")
            }
        }

        assertThat(executor.calls).isEqualTo(1)
        assertThat(thrownException)
            .isInstanceOf(CustomException::class.java)
            .hasCauseInstanceOf(RecoverableException::class.java)
            .hasRootCauseMessage("Dummy Exception")
        verify(backoffStrategy, times(1)).delay(any())
    }
}

class RecoverableException(message: String) : RuntimeException(message)

class CustomException(message: String, throwable: Throwable) : CordaRuntimeException(message, throwable)

class Executor(private val timesToSuccess: Int = 1) {
    var calls = 0

    fun execute(value: Any): Any {
        calls++

        if (calls < timesToSuccess) {
            throw RecoverableException("Dummy Exception")
        }

        return value
    }
}
