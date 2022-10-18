@file:Suppress("SpreadOperator", "WildcardImport")
package net.corda.uniqueness.utils

import net.corda.data.uniqueness.*
import net.corda.test.util.time.AutoTickTestClock
import net.corda.v5.application.uniqueness.model.*
import net.corda.uniqueness.datamodel.common.UniquenessConstants
import net.corda.uniqueness.datamodel.common.toCharacterRepresentation
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.assertAll
import java.time.Instant

/**
 * Uniqueness check related assertions for use in tests
 */
@Suppress("SpreadOperator", "TooManyFunctions")
object UniquenessAssertions {
    /**
     * Checks for a valid, standard success response. If a clock is specified, will additionally
     * check the commit timestamp is valid with respect to the provider.
     */
    fun assertStandardSuccessResponse(
        response: UniquenessCheckResponseAvro,
        clock: AutoTickTestClock? = null
    ) = getResultOfType<UniquenessCheckResultSuccessAvro>(response).run { assertValidTimestamp(commitTimestamp, clock) }

    /**
     * Checks for an accepted uniqueness check result. If a clock is specified, will additionally
     * check the result timestamp is valid with respect to the provider.
     */
    inline fun <reified T> assertAcceptedResult(result: UniquenessCheckResult, clock: AutoTickTestClock? = null) {
        assertInstanceOf(T::class.java, result)
        assertThat(result.toCharacterRepresentation()).isEqualTo(UniquenessConstants.RESULT_ACCEPTED_REPRESENTATION)
        assertValidTimestamp(result.resultTimestamp, clock)
    }

    /**
     * Performs common checks for a reject result. If a clock is specified, will additionally
     * check the result timestamp is valid with respect to the provider.
     */
    private inline fun <reified T> assertRejectedResultCommon(
        result: UniquenessCheckResult,
        clock: AutoTickTestClock? = null
    ) {
        assertInstanceOf(T::class.java, getErrorOfType<T>(result))
        assertThat(result.toCharacterRepresentation()).isEqualTo(UniquenessConstants.RESULT_REJECTED_REPRESENTATION)
        assertValidTimestamp(result.resultTimestamp, clock)
    }

    /**
     * Checks for an input state unknown result. If a clock is specified, will additionally
     * check the result timestamp is valid with respect to the provider.
     */
    fun assertInputStateUnknownResult(
        txId: SecureHash,
        result: UniquenessCheckResult,
        clock: AutoTickTestClock? = null
    ) {
        assertRejectedResultCommon<UniquenessCheckErrorInputStateUnknown>(result, clock)

        val unknownStates = (getErrorOfType<UniquenessCheckErrorInputStateUnknown>(result)).unknownStates
        assertAll(
            { assertThat(unknownStates.size).isEqualTo(1) },
            { assertThat(unknownStates.single().stateIndex).isEqualTo(0) },
            { assertThat(unknownStates.single().txHash).isEqualTo(txId) })
    }

    /**
     * Checks for an input state conflict result. If a clock is specified, will additionally
     * check the result timestamp is valid with respect to the provider.
     */
    fun assertInputStateConflictResult(
        txId: SecureHash,
        consumingTxId: SecureHash,
        result: UniquenessCheckResult,
        clock: AutoTickTestClock? = null
    ) {
        assertRejectedResultCommon<UniquenessCheckErrorInputStateConflict>(result, clock)

        val conflicts = (getErrorOfType<UniquenessCheckErrorInputStateConflict>(result)).conflictingStates
        assertAll(
            { assertThat(conflicts.size).isEqualTo(1) },
            { assertThat(conflicts.single().consumingTxId).isEqualTo(consumingTxId) },
            { assertThat(conflicts.single().stateRef.txHash).isEqualTo(txId) },
            { assertThat(conflicts.single().stateRef.stateIndex).isEqualTo(0) })
    }

    /**
     * Checks for a reference state conflict result. If a clock is specified, will additionally
     * check the result timestamp is valid with respect to the provider.
     */
    fun assertReferenceStateConflictResult(
        txId: SecureHash,
        consumingTxId: SecureHash,
        result: UniquenessCheckResult,
        clock: AutoTickTestClock? = null
    ) {
        assertRejectedResultCommon<UniquenessCheckErrorReferenceStateConflict>(result, clock)

        val conflicts = (getErrorOfType<UniquenessCheckErrorReferenceStateConflict>(result)).conflictingStates
        assertAll(
            { assertThat(conflicts.size).isEqualTo(1) },
            { assertThat(conflicts.single().consumingTxId).isEqualTo(consumingTxId) },
            { assertThat(conflicts.single().stateRef.txHash).isEqualTo(txId) },
            { assertThat(conflicts.single().stateRef.stateIndex).isEqualTo(0) })
    }

    /**
     * Checks for a reference state unknown result. If a clock is specified, will additionally
     * check the result timestamp is valid with respect to the provider.
     */
    fun assertReferenceStateUnknownResult(
        txId: SecureHash,
        result: UniquenessCheckResult,
        clock: AutoTickTestClock? = null
    ) {
        assertRejectedResultCommon<UniquenessCheckErrorReferenceStateUnknown>(result, clock)

        val unknownStates = (getErrorOfType<UniquenessCheckErrorReferenceStateUnknown>(result)).unknownStates
        assertAll(
            { assertThat(unknownStates.size).isEqualTo(1) },
            { assertThat(unknownStates.single().stateIndex).isEqualTo(0) },
            { assertThat(unknownStates.single().txHash).isEqualTo(txId) })
    }

    /**
     * Checks for a time window out of bound result. If a clock is specified, will additionally check the result
     * timestamp is valid with respect to the provider.
     */
    fun assertTimeWindowOutOfBoundsResult(
        evaluationTime: Instant,
        lowerBound: Instant,
        upperBound: Instant,
        result: UniquenessCheckResult,
        clock: AutoTickTestClock? = null
    ) {
        assertRejectedResultCommon<UniquenessCheckErrorTimeWindowOutOfBounds>(result, clock)

        val error = getErrorOfType<UniquenessCheckErrorTimeWindowOutOfBounds>(result)
        assertAll(
            { assertThat(error.evaluationTimestamp).isEqualTo(evaluationTime) },
            { assertThat(error.timeWindowLowerBound).isEqualTo(lowerBound) },
            { assertThat(error.timeWindowUpperBound).isEqualTo(upperBound) })
    }

    /**
     * Checks for a malformed request result with the specified error text. If a clock is specified, will additionally
     * check the result timestamp is valid with respect to the provider.
     */
    fun assertMalformedRequestResult(
        errorMessage: String,
        result: UniquenessCheckResult,
        clock: AutoTickTestClock? = null
    ) {
        assertRejectedResultCommon<UniquenessCheckErrorMalformedRequest>(result, clock)
        assertThat((getErrorOfType<UniquenessCheckErrorMalformedRequest>(result)).errorText).isEqualTo(errorMessage)
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
        expectedUpperBound: Instant)
    {
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
    private inline fun <reified T> getErrorOfType(result: UniquenessCheckResult): T {
        val failure = result as UniquenessCheckResultFailure
        return failure.error as T
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
