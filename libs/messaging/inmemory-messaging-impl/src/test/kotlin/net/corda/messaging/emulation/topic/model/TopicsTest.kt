package net.corda.messaging.emulation.topic.model

import net.corda.messaging.emulation.properties.InMemoryConfiguration
import net.corda.messaging.emulation.properties.SubscriptionConfiguration
import net.corda.messaging.emulation.properties.TopicConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class TopicsTest {
    private val config = mock<InMemoryConfiguration> {
        on { topicConfiguration(any()) } doReturn TopicConfiguration(1, 1)
        on { subscriptionConfiguration(any()) } doReturn SubscriptionConfiguration(1, 2)
    }
    private val topics = Topics(config)

    @Test
    fun `getTopic return a new topic when needed`() {
        val topic = topics.getTopic("topic")

        assertThat(topic).isNotNull
    }

    @Test
    fun `getTopic return the same topic when can`() {
        val topic1 = topics.getTopic("topic")
        val topic2 = topics.getTopic("topic")

        assertThat(topic1).isEqualTo(topic2)
    }

    @Test
    fun `getTopic return the different topic when cant`() {
        val topic1 = topics.getTopic("topic1")
        val topic2 = topics.getTopic("topic2")

        assertThat(topic1).isNotEqualTo(topic2)
    }

    @Test
    fun `createConsumerThread return valid thread`() {
        val consumer = mock<Consumer> {
            on { groupName } doReturn "group"
            on { topicName } doReturn "topic"
        }
        val thread = topics.createConsumerThread(consumer)

        assertThat(thread).isNotNull
    }
}
