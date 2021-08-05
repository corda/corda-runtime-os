package net.corda.messaging.emulation.subscription.eventlog

import com.typesafe.config.Config
import net.corda.messaging.emulation.properties.InMemProperties.Companion.PARTITION_SIZE
import net.corda.messaging.emulation.properties.InMemProperties.Companion.TOPICS_POLL_SIZE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class InMemoryEventLogSubscriptionConfigTest {
    private val conf = mock<Config> {
        on { getInt(PARTITION_SIZE) } doReturn 100
        on { getInt(TOPICS_POLL_SIZE) } doReturn 35
    }
    private val testObject = InMemoryEventLogSubscriptionConfig(mock(), conf)

    @Test
    fun `partitionSize test`() {
        assertThat(testObject.partitionSize).isEqualTo(100)
    }
    @Test
    fun `pollSize test`() {
        assertThat(testObject.pollSize).isEqualTo(35)
    }
}
