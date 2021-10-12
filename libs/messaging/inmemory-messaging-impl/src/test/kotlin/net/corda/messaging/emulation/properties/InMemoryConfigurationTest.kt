package net.corda.messaging.emulation.properties

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

@Suppress("ClassNaming")
class InMemoryConfigurationTest {
    private val configuration = InMemoryConfiguration()
    @Nested
    inner class `topicConfiguration tests` {
        @Test
        fun `will return default number of partitions if no value to overwrite`() {
            val conf = configuration.topicConfiguration("topic3")

            assertThat(conf.partitionCount).isEqualTo(10)
        }

        @Test
        fun `will override number of partitions`() {
            val conf = configuration.topicConfiguration("topic1")

            assertThat(conf.partitionCount).isEqualTo(77)
        }

        @Test
        fun `will return default max size if no value to overwrite`() {
            val conf = configuration.topicConfiguration("topic3")

            assertThat(conf.maxPartitionSize).isEqualTo(8)
        }

        @Test
        fun `will override the max size`() {
            val conf = configuration.topicConfiguration("topic2")

            assertThat(conf.maxPartitionSize).isEqualTo(20)
        }
    }
    @Nested
    inner class `subscriptionConfiguration tests` {
        @Test
        fun `will return default poll size if no value to overwrite`() {
            val conf = configuration.subscriptionConfiguration("group3")

            assertThat(conf.maxPollSize).isEqualTo(50)
        }

        @Test
        fun `will override number of poll size`() {
            val conf = configuration.subscriptionConfiguration("group2")

            assertThat(conf.maxPollSize).isEqualTo(15)
        }

        @Test
        fun `will return default timeout if no value to overwrite`() {
            val conf = configuration.subscriptionConfiguration("group3")

            assertThat(conf.threadStopTimeout).isEqualTo(Duration.ofSeconds(2))
        }

        @Test
        fun `will override the timeout`() {
            val conf = configuration.subscriptionConfiguration("group1")

            assertThat(conf.threadStopTimeout).isEqualTo(Duration.ofSeconds(5))
        }
    }
}
