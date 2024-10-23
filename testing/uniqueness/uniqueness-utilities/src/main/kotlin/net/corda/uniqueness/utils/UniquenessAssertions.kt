@file:Suppress("SpreadOperator", "WildcardImport")
package net.corda.uniqueness.utils

import net.corda.ledger.libs.uniqueness.data.UniquenessCheckResponse
import net.corda.test.util.time.AutoTickTestClock
import net.corda.uniqueness.datamodel.common.UniquenessConstants
import net.corda.uniqueness.datamodel.common.toCharacterRepresentation
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckTransactionDetailsInternal
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorMalformedRequest
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorNotPreviouslySeenTransaction
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorTimeWindowBeforeLowerBound
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorTimeWindowOutOfBounds
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorUnhandledException
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
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
        response: UniquenessCheckResponse,
        clock: AutoTickTestClock? = null
    ) = getResultOfType<UniquenessCheckResultSuccessImpl>(response).run {
        assertValidTimestamp(resultTimestamp, clock)
    }

    /**
     * Checks for a malformed request response with the specified error text
     */
    fun assertMalformedRequestResponse(
        response: UniquenessCheckResponse,
        expectedErrorText: String
    ) {
        val error = getResultOfType<UniquenessCheckResultFailure>(response)

        getErrorOfType<UniquenessCheckErrorMalformedRequest>(error).run {
            assertThat(errorText).isEqualTo(expectedErrorText)
        }
    }

    /**
     * Checks for an unhandled exception response with the specified exception type
     */
    fun assertUnhandledExceptionResponse(
        response: UniquenessCheckResponse,
        expectedExceptionType: String
    ) {
        val error = getResultOfType<UniquenessCheckResultFailure>(response)

        getErrorOfType<UniquenessCheckErrorUnhandledException>(error).run {
            assertThat(unhandledExceptionType).isEqualTo(expectedExceptionType)
        }
    }

    /**
     * Checks for an unknown input state response, ensuring that all specified unknown states
     *  are captured.
     */
    fun assertUnknownInputStateResponse(
        response: UniquenessCheckResponse,
        expectedUnknownStates: List<String>
    ) {
        val error = getResultOfType<UniquenessCheckResultFailure>(response)

        getErrorOfType<UniquenessCheckErrorInputStateUnknown>(error).run {
            assertThat(unknownStates.map { it.toString() })
                // This is necessary due to tactical fix CORE-18025 only storing a single failing
                // state. This should be restored to containsExactlyInAnyOrder when strategic fix
                // CORE-17155 is implemented.
                .containsAnyOf(*expectedUnknownStates.toTypedArray())
        }
    }

    /**
     * Checks for an input state conflict response, ensuring that all specified conflicting
     * states are captured.
     */
    fun assertInputStateConflictResponse(
        response: UniquenessCheckResponse,
        expectedConflictingStates: List<String>
    ) {
        val error = getResultOfType<UniquenessCheckResultFailure>(response)

        getErrorOfType<UniquenessCheckErrorInputStateConflict>(error).run {
            assertThat(conflictingStates.map { it.stateRef.toString() })
                // This is necessary due to tactical fix CORE-18025 only storing a single failing
                // state. This should be restored to containsExactlyInAnyOrder when strategic fix
                // CORE-17155 is implemented.
                .containsAnyOf(*expectedConflictingStates.toTypedArray())
        }
    }

    /**
     * Checks for an unknown reference state response, ensuring that all specified unknown states
     *  are captured.
     */
    fun assertUnknownReferenceStateResponse(
        response: UniquenessCheckResponse,
        expectedUnknownStates: List<String>
    ) {
        val error = getResultOfType<UniquenessCheckResultFailure>(response)

        getErrorOfType<UniquenessCheckErrorReferenceStateUnknown>(error).run {
            assertThat(unknownStates.map { it.toString() })
                // This is necessary due to tactical fix CORE-18025 only storing a single failing
                // state. This should be restored to containsExactlyInAnyOrder when strategic fix
                // CORE-17155 is implemented.
                .containsAnyOf(*expectedUnknownStates.toTypedArray())
        }
    }

    /**
     * Checks for a reference state conflict response, ensuring that all specified conflicting
     * states are captured.
     */
    fun assertReferenceStateConflictResponse(
        response: UniquenessCheckResponse,
        expectedConflictingStates: List<String>
    ) {
        val error = getResultOfType<UniquenessCheckResultFailure>(response)

        getErrorOfType<UniquenessCheckErrorReferenceStateConflict>(error).run {
            assertThat(conflictingStates.map { it.stateRef.toString() })
                // This is necessary due to tactical fix CORE-18025 only storing a single failing
                // state. This should be restored to containsExactlyInAnyOrder when strategic fix
                // CORE-17155 is implemented.
                .containsAnyOf(*expectedConflictingStates.toTypedArray())
        }
    }

    /**
     * Checks for a time window out of bounds response, ensuring that the response contains
     * the expected (optional) lower and (mandatory) upper bound.
     */
    fun assertTimeWindowOutOfBoundsResponse(
        response: UniquenessCheckResponse,
        expectedLowerBound: Instant? = null,
        expectedUpperBound: Instant)
    {
        val error = getResultOfType<UniquenessCheckResultFailure>(response)

        getErrorOfType<UniquenessCheckErrorTimeWindowOutOfBounds>(error).run {
            assertAll(
                { assertEquals(expectedLowerBound, timeWindowLowerBound, "Lower bound") },
                { assertEquals(expectedUpperBound, timeWindowUpperBound, "Upper bound") }
            )
        }
    }

    /**
     * Checks for a time window before lower bounds response
     */
    fun assertTimeWindowBeforeLowerBoundResponse(response: UniquenessCheckResponse) {
        val error = getResultOfType<UniquenessCheckResultFailure>(response)

        getErrorOfType<UniquenessCheckErrorTimeWindowBeforeLowerBound>(error)
    }

    /**
     * Checks for a not previously seen transaction response
     */
    fun assertNotPreviouslySeenTransactionResponse(response: UniquenessCheckResponse) {
        val error = getResultOfType<UniquenessCheckResultFailure>(response)

        getErrorOfType<UniquenessCheckErrorNotPreviouslySeenTransaction>(error)
    }

    /**
     * Checks that all commit timestamps within a list of responses are unique
     */
    fun assertUniqueCommitTimestamps(responses: Collection<UniquenessCheckResponse>) {
        assertEquals(
            responses.size,
            responses.distinctBy {
                (it.uniquenessCheckResult as UniquenessCheckResultSuccess).resultTimestamp
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
            { assertThat(unknownStates.single().stateIndex).isEqualTo(stateIdx) },
            { assertThat(unknownStates.single().txHash).isEqualTo(txId) })
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
            { assertThat(conflicts.single().stateRef.txHash).isEqualTo(txId) },
            { assertThat(conflicts.single().stateRef.stateIndex).isEqualTo(stateIdx) })
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
            { assertThat(conflicts.single().stateRef.txHash).isEqualTo(txId) },
            { assertThat(conflicts.single().stateRef.stateIndex).isEqualTo(stateIdx) })
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
            { assertThat(unknownStates.single().stateIndex).isEqualTo(stateIdx) },
            { assertThat(unknownStates.single().txHash).isEqualTo(txId) })
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
        return assertInstanceOf(T::class.java, result.error) { result.error.toString() }
    }


    private inline fun <reified T> getResultOfType(response: UniquenessCheckResponse): T {
        return assertInstanceOf(T::class.java, response.uniquenessCheckResult) { response.uniquenessCheckResult.toString() }
    }

    private fun assertValidTimestamp(timestamp: Instant, clock: AutoTickTestClock? = null) {
        assertThat(timestamp).isAfter(Instant.MIN)
        if (clock != null) {
            assertThat(timestamp).isBeforeOrEqualTo(clock.peekTime())
        }
    }
}
