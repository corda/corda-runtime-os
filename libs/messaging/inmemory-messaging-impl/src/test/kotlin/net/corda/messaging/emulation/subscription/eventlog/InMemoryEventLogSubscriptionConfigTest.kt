package net.corda.messaging.emulation.subscription.eventlog

import com.typesafe.config.Config
import io.mockk.every
import io.mockk.mockk
import net.corda.messaging.emulation.properties.InMemProperties.Companion.PARTITION_SIZE
import net.corda.messaging.emulation.properties.InMemProperties.Companion.TOPICS_POLL_SIZE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InMemoryEventLogSubscriptionConfigTest {
    private val conf = mockk<Config> {
        every { getInt(PARTITION_SIZE) } returns 100
        every { getInt(TOPICS_POLL_SIZE) } returns 35
    }
    private val testObject = InMemoryEventLogSubscriptionConfig(mockk(), conf)

    @Test
    fun `partitionSize test`() {
        assertThat(testObject.partitionSize).isEqualTo(100)
    }
    @Test
    fun `pollSize test`() {
        assertThat(testObject.pollSize).isEqualTo(35)
    }
}