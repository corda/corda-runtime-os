package net.corda.crypto.flow

import org.assertj.core.api.Assertions.assertThat
import java.time.Instant

fun assertThatIsBetween(actual: Instant, before: Instant, after: Instant) {
    assertThat(actual.toEpochMilli())
        .isGreaterThanOrEqualTo(before.toEpochMilli())
        .isLessThanOrEqualTo(after.toEpochMilli())
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