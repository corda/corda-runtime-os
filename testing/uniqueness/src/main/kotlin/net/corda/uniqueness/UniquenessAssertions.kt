package net.corda.uniqueness

import net.corda.data.uniqueness.*
import net.corda.test.util.time.AutoTickTestClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

/**
 * Uniqueness check related assertions for use in tests
 */
object UniquenessAssertions {
    /**
     * Checks for a valid, standard success response. If a clock is specified, will additionally
     * check the commit timestamp is valid with respect to the provider.
     */
    fun assertStandardSuccessResponse(response: UniquenessCheckResponse,
                                      clock: AutoTickTestClock? = null
    ) =
        getResultOfType(response, UniquenessCheckResultSuccess::class.java).run {
            assertThat(commitTimestamp).isAfter(Instant.MIN)
            if ( clock != null) {
                assertThat(commitTimestamp)
                    .isBeforeOrEqualTo(clock.peekTime())
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
        getResultOfType(response, UniquenessCheckResultInputStateConflict::class.java).run {
            assertThat(conflictingStates)
                .containsExactlyInAnyOrder(*expectedConflictingStates.toTypedArray())
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
        getResultOfType(response, UniquenessCheckResultReferenceStateConflict::class.java).run {
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
        getResultOfType(response, UniquenessCheckResultTimeWindowOutOfBounds::class.java).run {
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

    private fun<T> getResultOfType(response: UniquenessCheckResponse, expectedType: Class<T>) : T {
        assertInstanceOf(expectedType, response.result)
        @Suppress("UNCHECKED_CAST")
        return response.result as T
    }
}
