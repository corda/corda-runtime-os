package net.corda.external.messaging.services

import net.corda.external.messaging.services.impl.ExternalMessagingRecordFactoryImpl
import net.corda.external.messaging.services.impl.MessageHeaders
import net.corda.libs.external.messaging.entities.InactiveResponseType
import net.corda.libs.external.messaging.entities.Route
import net.corda.messaging.api.records.Record
import net.corda.utilities.time.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.time.ZoneOffset

class ExternalMessagingRecordFactoryImplTest {

    @Test
    fun `create external event message`() {
        val now = LocalDateTime.of(2023, 3, 10, 11, 12, 13, 123_000_000).toInstant(ZoneOffset.UTC)
        val clock = mock<Clock>().apply {
            whenever(instant()).thenReturn(now)
        }
        val randomIdFn: () -> String = { "random_id1" }
        val route = Route("c1", "t1", true, InactiveResponseType.IGNORE)

        val target = ExternalMessagingRecordFactoryImpl(clock, randomIdFn)

        val result = target.createSendRecord("000000000001", route, "m_id", "m_text")

        val expected = Record(
            topic = "t1",
            key = "m_id",
            value = "m_text",
            headers = listOf(
                MessageHeaders.HOLDING_ID to "000000000001",
                MessageHeaders.CHANNEL_NAME to "c1",
                MessageHeaders.CORRELATION_ID to "random_id1",
                MessageHeaders.CREATION_TIME_UTC to "2023-03-10T11:12:13.123Z"
            )
        )

        assertThat(result).isEqualTo(expected)
    }
}
