@file:Suppress("SpreadOperator", "WildcardImport")
package net.corda.uniqueness.utils

import net.corda.data.uniqueness.*
import net.corda.test.util.time.AutoTickTestClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
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
    fun assertStandardSuccessResponse(response: UniquenessCheckResponse,
                                      clock: AutoTickTestClock? = null
    ) =
        getResultOfType<UniquenessCheckResultSuccess>(response).run {
            assertThat(commitTimestamp).isAfter(Instant.MIN)
            if ( clock != null) {
                assertThat(commitTimestamp)
                    .isBeforeOrEqualTo(clock.peekTime())
            }
        }


    /**
     * Checks for a malformed request response with the specified error text
     */
    fun assertMalformedRequestResponse(
        response: UniquenessCheckResponse,
        expectedErrorText: String
    ) {
        getResultOfType<UniquenessCheckResultMalformedRequest>(response).run {
            assertThat(errorText).isEqualTo(expectedErrorText)
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
        getResultOfType<UniquenessCheckResultInputStateUnknown>(response).run {
            assertThat(unknownStates)
                .containsExactlyInAnyOrder(*expectedUnknownStates.toTypedArray())
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
        getResultOfType<UniquenessCheckResultInputStateConflict>(response).run {
            assertThat(conflictingStates)
                .containsExactlyInAnyOrder(*expectedConflictingStates.toTypedArray())
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
        getResultOfType<UniquenessCheckResultReferenceStateUnknown>(response).run {
            assertThat(unknownStates)
                .containsExactlyInAnyOrder(*expectedUnknownStates.toTypedArray())
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
        getResultOfType<UniquenessCheckResultReferenceStateConflict>(response).run {
            assertThat(conflictingStates)
                .containsExactlyInAnyOrder(*expectedConflictingStates.toTypedArray())
        }
    }

    /**
     * Checks for a time window out of bounds response, ensuring that the response contains
     * the expected (optional) lower and (mandatory) upper bound.
     */
    fun assertTimeWindowOutOfBoundsResponse(
        response: UniquenessCheckResponse,
        expectedLowerBound: Instant? = null,
        expectedUpperBound: Instant
    ) {
        getResultOfType<UniquenessCheckResultTimeWindowOutOfBounds>(response).run {
            assertAll(
                { assertEquals(expectedLowerBound, timeWindowLowerBound, "Lower bound") },
                { assertEquals(expectedUpperBound, timeWindowUpperBound, "Upper bound") }
            )
        }
    }

    /**
     * Checks that all commit timestamps within a list of responses are unique
     */
    fun assertUniqueCommitTimestamps(responses: List<UniquenessCheckResponse>) {
        assertEquals(responses.size, responses.distinctBy {
            (it.result as UniquenessCheckResultSuccess).commitTimestamp
        }.size)
    }

    private inline fun<reified T> getResultOfType(response: UniquenessCheckResponse) : T {
        assertInstanceOf(T::class.java, response.result)
        @Suppress("UNCHECKED_CAST")
        return response.result as T
    }
}
