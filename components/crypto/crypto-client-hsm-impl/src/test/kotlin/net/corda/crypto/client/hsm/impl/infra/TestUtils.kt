package net.corda.crypto.client.hsm.impl.infra

import net.corda.messaging.api.publisher.RPCSender
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import java.time.Instant

inline fun <reified REQUEST: Any, reified RESPONSE: Any, reified RESULT: Any> RPCSender<REQUEST, RESPONSE>.act(
    block: () -> RESULT?
): SendActResult<REQUEST, RESULT> {
    val result = actWithTimer(block)
    val messages = argumentCaptor<REQUEST>()
    verify(this).sendRequest(messages.capture())
    return SendActResult(
        before = result.first,
        after = result.second,
        value = result.third,
        messages = messages.allValues
    )
}

inline fun <reified R> actWithTimer(block: () -> R): Triple<Instant, Instant, R> {
    val before = Instant.now()
    val result = block()
    val after = Instant.now()
    return Triple(before, after, result)
}

fun assertThatIsBetween(actual: Instant, before: Instant, after: Instant) {
    assertThat(actual.toEpochMilli())
        .isGreaterThanOrEqualTo(before.toEpochMilli())
        .isLessThanOrEqualTo(after.toEpochMilli())
}

data class SendActResult<REQUEST, RESPONSE>(
    val before: Instant,
    val after: Instant,
    val value: RESPONSE?,
    val messages: List<REQUEST>
) {
    val firstRequest: REQUEST get() = messages.first()

    fun assertThatIsBetween(timestamp: Instant) = assertThatIsBetween(timestamp, before, after)
}

