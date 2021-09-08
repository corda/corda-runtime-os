package net.corda.messaging.kafka.subscription.consumer.wrapper

import com.typesafe.config.Config
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.subscription.consumer.listener.StateAndEventRebalanceListenerTest
import net.corda.messaging.kafka.subscription.consumer.wrapper.impl.StateAndEventConsumerImpl
import net.corda.messaging.kafka.subscription.factory.SubscriptionMapFactory
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StateAndEventConsumerImplTest {

    private companion object {
        const val TOPIC = "topic"
    }

    @Test
    fun testClose() {
        val (stateAndEventListener, eventConsumer, stateConsumer, config, mapFactory, partitions) = setupMocks()
        val partitionState = StateAndEventPartitionState<String, String>(mutableMapOf(partitions.first().partition() to mutableMapOf()), mutableMapOf(partitions.first().partition() to Long.MAX_VALUE))
        val consumer = StateAndEventConsumerImpl(config, mapFactory, eventConsumer, stateConsumer, partitionState, stateAndEventListener)
        consumer.close()

        verify(eventConsumer, times(1)).close(any())
        verify(stateConsumer, times(1)).close(any())
    }


    private fun setupMocks(): Mocks {
        val listener: StateAndEventListener<String, String> = mock()
        val eventConsumer: CordaKafkaConsumer<String, String> = mock()
        val stateConsumer: CordaKafkaConsumer<String, String> = mock()

        val topicPartitions = setOf(TopicPartition(TOPIC, 0))
        val config = mock<Config>()
        val mapFactory = mock<SubscriptionMapFactory<String, Pair<Long, String>>>()

        doAnswer { "string" }.whenever(config).getString(any())
        doAnswer { topicPartitions }.whenever(stateConsumer).assignment()

        return Mocks(listener, eventConsumer, stateConsumer, config, mapFactory, topicPartitions)
    }

    data class Mocks(
        val stateAndEventListener: StateAndEventListener<String, String>,
        val eventConsumer: CordaKafkaConsumer<String, String>,
        val stateConsumer: CordaKafkaConsumer<String, String>,
        val config : Config,
        val mapFactory : SubscriptionMapFactory<String, Pair<Long, String>>,
        val partitions : Set<TopicPartition>
    )
}