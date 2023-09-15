@file:Suppress("SpreadOperator", "WildcardImport")
package net.corda.applications.workers.smoketest.utils

import net.corda.data.uniqueness.*
import net.corda.test.util.time.AutoTickTestClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
