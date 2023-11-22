package net.corda.crypto.service.impl.infra

import org.assertj.core.api.Assertions.assertThat
import java.time.Instant
import kotlin.time.Duration

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

open class ActResultTimestamps(
    val before: Instant,
    val after: Instant,
) {
    fun assertThatIsBetween(timestamp: Instant) = assertThatIsBetween(timestamp, before, after)
}


fun assertClose(actual: Instant, expected: Instant, tolerance: Duration) =
    assertThat(actual.toEpochMilli()).isBetween(
        expected.toEpochMilli() - tolerance.inWholeMilliseconds,
        expected.toEpochMilli() + tolerance.inWholeMilliseconds
    )

class ActResult<RESULT>(
    before: Instant,
    after: Instant,
    val value: RESULT?
) : ActResultTimestamps(before, after)