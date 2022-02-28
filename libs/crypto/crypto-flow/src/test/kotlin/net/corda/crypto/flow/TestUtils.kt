package net.corda.crypto.flow

import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import java.time.Instant

fun assertThatIsBetween(actual: Instant, before: Instant, after: Instant) {
    MatcherAssert.assertThat(
        actual.toEpochMilli(),
        Matchers.allOf(
            Matchers.greaterThanOrEqualTo(before.toEpochMilli()),
            Matchers.lessThanOrEqualTo(after.toEpochMilli())
        )
    )
}

inline fun <reified RESULT: Any> act(block: () -> RESULT?): ActResult<RESULT> {
    val before = Instant.now()
    val result = block()
    val after = Instant.now()
    return ActResult(
        before = before,
        after = after,
        value = result
    )
}

data class ActResult<RESULT>(
    val before: Instant,
    val after: Instant,
    val value: RESULT?
) {
    fun assertThatIsBetween(timestamp: Instant) = assertThatIsBetween(timestamp, before, after)
}