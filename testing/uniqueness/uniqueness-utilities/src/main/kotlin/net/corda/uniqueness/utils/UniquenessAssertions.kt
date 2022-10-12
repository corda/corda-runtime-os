@file:Suppress("SpreadOperator", "WildcardImport")
package net.corda.uniqueness.utils

import net.corda.data.uniqueness.*
import net.corda.test.util.time.AutoTickTestClock
import net.corda.uniqueness.datamodel.common.UniquenessConstants
import net.corda.uniqueness.datamodel.common.toCharacterRepresentation
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.assertAll
import java.time.Instant

/**
 * Uniqueness check related assertions for use in tests
 */
@Suppress("SpreadOperator")
object UniquenessAssertions {
    /**
     * Checks for a valid, standard success response. If a clock is specified, will additionally
     * check the commit timestamp is valid with respect to the provider.
     */
    fun assertStandardSuccessResponse(
        response: UniquenessCheckResponseAvro,
        clock: AutoTickTestClock? = null
    ) =
        getResultOfType<UniquenessCheckResultSuccessAvro>(response).run {
            assertThat(commitTimestamp).isAfter(Instant.MIN)
            if (clock != null) {
                assertThat(commitTimestamp)
                    .isBeforeOrEqualTo(clock.peekTime())
            }
        }

    /**
     * Checks for an accepted uniqueness check result.
     */
    inline fun <reified T> assertAcceptedResult(result: UniquenessCheckResult, clock: AutoTickTestClock? = null) {
        assertInstanceOf(T::class.java, result)
        assertThat(result.toCharacterRepresentation()).isEqualTo(UniquenessConstants.RESULT_ACCEPTED_REPRESENTATION)
        assertValidTimestamp(result.resultTimestamp, clock)
    }

    /**
     * Checks for a rejected uniqueness check result.
     */
    inline fun <reified T>assertRejectedResult(result: UniquenessCheckResult, clock: AutoTickTestClock? = null) {
        assertInstanceOf(T::class.java, getErrorOfType<T>(result))
        assertThat(result.toCharacterRepresentation()).isEqualTo(UniquenessConstants.RESULT_REJECTED_REPRESENTATION)
        assertValidTimestamp(result.resultTimestamp, clock)
    }

    /**
     * Checks for a malformed request response with the specified error text
     */
    fun assertMalformedRequestResponse(
        response: UniquenessCheckResponseAvro,
        expectedErrorText: String
    ) {
        getResultOfType<UniquenessCheckResultMalformedRequestAvro>(response).run {
            assertThat(errorText).isEqualTo(expectedErrorText)
        }
    }

    /**
     * Checks for an unhandled exception response with the specified exception type
     */
    fun assertUnhandledExceptionResponse(
        response: UniquenessCheckResponseAvro,
        expectedExceptionType: String
    ) {
        getResultOfType<UniquenessCheckResultUnhandledExceptionAvro>(response).run {
            assertThat(exception.errorType).isEqualTo(expectedExceptionType)
        }
    }

    /**
     * Checks for an unknown input state response, ensuring that all specified unknown states
     *  are captured.
     */
    fun assertUnknownInputStateResponse(
        response: UniquenessCheckResponseAvro,
        expectedUnknownStates: List<String>
    ) {
        getResultOfType<UniquenessCheckResultInputStateUnknownAvro>(response).run {
            assertThat(unknownStates)
                .containsExactlyInAnyOrder(*expectedUnknownStates.toTypedArray())
        }
    }

    /**
     * Checks for an input state conflict response, ensuring that all specified conflicting
     * states are captured.
     */
    fun assertInputStateConflictResponse(
        response: UniquenessCheckResponseAvro,
        expectedConflictingStates: List<String>
    ) {
        getResultOfType<UniquenessCheckResultInputStateConflictAvro>(response).run {
            assertThat(conflictingStates)
                .containsExactlyInAnyOrder(*expectedConflictingStates.toTypedArray())
        }
    }

    /**
     * Checks for an unknown reference state response, ensuring that all specified unknown states
     *  are captured.
     */
    fun assertUnknownReferenceStateResponse(
        response: UniquenessCheckResponseAvro,
        expectedUnknownStates: List<String>
    ) {
        getResultOfType<UniquenessCheckResultReferenceStateUnknownAvro>(response).run {
            assertThat(unknownStates)
                .containsExactlyInAnyOrder(*expectedUnknownStates.toTypedArray())
        }
    }

    /**
     * Checks for a reference state conflict response, ensuring that all specified conflicting
     * states are captured.
     */
    fun assertReferenceStateConflictResponse(
        response: UniquenessCheckResponseAvro,
        expectedConflictingStates: List<String>
    ) {
        getResultOfType<UniquenessCheckResultReferenceStateConflictAvro>(response).run {
            assertThat(conflictingStates)
                .containsExactlyInAnyOrder(*expectedConflictingStates.toTypedArray())
        }
    }

    /**
     * Checks for a time window out of bounds response, ensuring that the response contains
     * the expected (optional) lower and (mandatory) upper bound.
     */
    fun assertTimeWindowOutOfBoundsResponse(
        response: UniquenessCheckResponseAvro,
        expectedLowerBound: Instant? = null,
        expectedUpperBound: Instant
    ) {
        getResultOfType<UniquenessCheckResultTimeWindowOutOfBoundsAvro>(response).run {
            assertAll(
                { assertEquals(expectedLowerBound, timeWindowLowerBound, "Lower bound") },
                { assertEquals(expectedUpperBound, timeWindowUpperBound, "Upper bound") }
            )
        }
    }

    /**
     * Checks that all commit timestamps within a list of responses are unique
     */
    fun assertUniqueCommitTimestamps(responses: Collection<UniquenessCheckResponseAvro>) {
        assertEquals(
            responses.size,
            responses.distinctBy {
                (it.result as UniquenessCheckResultSuccessAvro).commitTimestamp
            }.size
        )
    }

    /**
     * Casts to a specific uniqueness check error type.
     */
    inline fun <reified T> getErrorOfType(result: UniquenessCheckResult): T {
        val failureImpl = result as UniquenessCheckResultFailureImpl
        val errorImpl = failureImpl.error as T
        assertInstanceOf(T::class.java, errorImpl)
        return errorImpl
    }

    private inline fun<reified T> getResultOfType(response: UniquenessCheckResponseAvro): T {
        assertInstanceOf(T::class.java, response.result)
        @Suppress("UNCHECKED_CAST")
        return response.result as T
    }

    fun assertValidTimestamp(timestamp: Instant, clock: AutoTickTestClock? = null) {
        assertThat(timestamp).isAfter(Instant.MIN)
        if (clock != null) {
            assertThat(timestamp).isBeforeOrEqualTo(clock.peekTime())
        }
    }
}
