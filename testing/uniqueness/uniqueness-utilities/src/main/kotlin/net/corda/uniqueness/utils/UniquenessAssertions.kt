@file:Suppress("SpreadOperator", "WildcardImport")
package net.corda.uniqueness.utils

import net.corda.data.uniqueness.*
import net.corda.test.util.time.AutoTickTestClock
import net.corda.uniqueness.datamodel.common.UniquenessConstants
import net.corda.uniqueness.datamodel.common.toCharacterRepresentation
import net.corda.uniqueness.datamodel.internal.UniquenessCheckTransactionDetailsInternal
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckErrorInputStateConflict
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckErrorInputStateUnknown
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckErrorMalformedRequest
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckErrorReferenceStateConflict
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckErrorReferenceStateUnknown
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckErrorTimeWindowOutOfBounds
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
     * Checks for an accepted uniqueness check result. If a clock is specified, will additionally
     * check the result timestamp is valid with respect to the provider.
     */
    fun assertAcceptedResult(result: UniquenessCheckResult, clock: AutoTickTestClock? = null) {
        assertThat(result.toCharacterRepresentation()).isEqualTo(UniquenessConstants.RESULT_ACCEPTED_REPRESENTATION)
        assertValidTimestamp(result.resultTimestamp, clock)
    }

    /**
     * Checks for an input state unknown result. If a clock is specified, will additionally
     * check the result timestamp is valid with respect to the provider.
     */
    fun assertInputStateUnknownResult(
        result: UniquenessCheckResult,
        txId: SecureHash,
        stateIdx: Int,
        clock: AutoTickTestClock? = null
    ) {
        assertRejectedResultCommon(result, clock)

        val unknownStates = (getErrorOfType<UniquenessCheckErrorInputStateUnknown>(
            result as UniquenessCheckResultFailure
        )).unknownStates
        assertAll(
            { assertThat(unknownStates.size).isEqualTo(1) },
            { assertThat(unknownStates.single().index).isEqualTo(stateIdx) },
            { assertThat(unknownStates.single().transactionHash).isEqualTo(txId) })
    }

    /**
     * Checks for an input state conflict result. If a clock is specified, will additionally
     * check the result timestamp is valid with respect to the provider.
     */
    fun assertInputStateConflictResult(
        result: UniquenessCheckResult,
        txId: SecureHash,
        consumingTxId: SecureHash,
        stateIdx: Int,
        clock: AutoTickTestClock? = null
    ) {
        assertRejectedResultCommon(result, clock)

        val conflicts = (getErrorOfType<UniquenessCheckErrorInputStateConflict>(
            result as UniquenessCheckResultFailure
        )).conflictingStates
        assertAll(
            { assertThat(conflicts.size).isEqualTo(1) },
            { assertThat(conflicts.single().consumingTxId).isEqualTo(consumingTxId) },
            { assertThat(conflicts.single().stateRef.transactionHash).isEqualTo(txId) },
            { assertThat(conflicts.single().stateRef.index).isEqualTo(stateIdx) })
    }

    /**
     * Checks for a reference state conflict result. If a clock is specified, will additionally
     * check the result timestamp is valid with respect to the provider.
     */
    fun assertReferenceStateConflictResult(
        result: UniquenessCheckResult,
        txId: SecureHash,
        consumingTxId: SecureHash,
        stateIdx: Int,
        clock: AutoTickTestClock? = null
    ) {
        assertRejectedResultCommon(result, clock)

        val conflicts = (getErrorOfType<UniquenessCheckErrorReferenceStateConflict>(
            result as UniquenessCheckResultFailure
        )).conflictingStates
        assertAll(
            { assertThat(conflicts.size).isEqualTo(1) },
            { assertThat(conflicts.single().consumingTxId).isEqualTo(consumingTxId) },
            { assertThat(conflicts.single().stateRef.transactionHash).isEqualTo(txId) },
            { assertThat(conflicts.single().stateRef.index).isEqualTo(stateIdx) })
    }

    /**
     * Checks for a reference state unknown result. If a clock is specified, will additionally
     * check the result timestamp is valid with respect to the provider.
     */
    fun assertReferenceStateUnknownResult(
        result: UniquenessCheckResult,
        txId: SecureHash,
        stateIdx: Int,
        clock: AutoTickTestClock? = null
    ) {
        assertRejectedResultCommon(result, clock)

        val unknownStates = (getErrorOfType<UniquenessCheckErrorReferenceStateUnknown>(
            result as UniquenessCheckResultFailure
        )).unknownStates
        assertAll(
            { assertThat(unknownStates.size).isEqualTo(1) },
            { assertThat(unknownStates.single().index).isEqualTo(stateIdx) },
            { assertThat(unknownStates.single().transactionHash).isEqualTo(txId) })
    }

    /**
     * Checks for a time window out of bound result. If a clock is specified, will additionally check the result
     * timestamp is valid with respect to the provider.
     */
    fun assertTimeWindowOutOfBoundsResult(
        result: UniquenessCheckResult,
        evaluationTime: Instant,
        lowerBound: Instant,
        upperBound: Instant,
        clock: AutoTickTestClock? = null
    ) {
        assertRejectedResultCommon(result, clock)

        val error = getErrorOfType<UniquenessCheckErrorTimeWindowOutOfBounds>(result as UniquenessCheckResultFailure)
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
        result: UniquenessCheckResult,
        errorMessage: String,
        clock: AutoTickTestClock? = null
    ) {
        assertRejectedResultCommon(result, clock)
        assertThat(
            (getErrorOfType<UniquenessCheckErrorMalformedRequest>(result as UniquenessCheckResultFailure)).errorText
        ).isEqualTo(errorMessage)
    }

    /**
     * Performs common checks for a reject result. If a clock is specified, will additionally
     * check the result timestamp is valid with respect to the provider.
     */
    private fun assertRejectedResultCommon(result: UniquenessCheckResult, clock: AutoTickTestClock? = null) {
        assertThat(result.toCharacterRepresentation()).isEqualTo(UniquenessConstants.RESULT_REJECTED_REPRESENTATION)
        assertValidTimestamp(result.resultTimestamp, clock)
    }

    /**
     * Checks if the given transaction details has the single and expected transaction ID.
     */
    fun assertContainingTxId(txnDetails: Map<SecureHash, UniquenessCheckTransactionDetailsInternal>, txId: SecureHash) {
        assertThat(txnDetails.size).isEqualTo(1)
        assertThat(txId).isEqualTo(txnDetails.entries.single().key)
    }

    /**
     * Gets the error from a result and casts it to a specific uniqueness check error type.
     */
    private inline fun <reified T> getErrorOfType(result: UniquenessCheckResultFailure): T {
        assertInstanceOf(T::class.java, result.error)
        return result.error as T
    }


    private inline fun <reified T> getResultOfType(response: UniquenessCheckResponseAvro): T {
        assertInstanceOf(T::class.java, response.result)
        @Suppress("UNCHECKED_CAST")
        return response.result as T
    }

    private fun assertValidTimestamp(timestamp: Instant, clock: AutoTickTestClock? = null) {
        assertThat(timestamp).isAfter(Instant.MIN)
        if (clock != null) {
            assertThat(timestamp).isBeforeOrEqualTo(clock.peekTime())
        }
    }
}
