package net.corda.messaging.kafka.subscription.consumer.listener

import com.typesafe.config.Config
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.properties.ConfigProperties
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventPartitionState
import net.corda.messaging.kafka.subscription.factory.SubscriptionMapFactory
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.messaging.kafka.types.StateAndEventConfig
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StateAndEventRebalanceListenerTest {

    private companion object {
        const val TOPIC = "topic"
        private val config: Config = createStandardTestConfig().getConfig(ConfigProperties.PATTERN_STATEANDEVENT)
        private val stateAndEventConfig = StateAndEventConfig.getStateAndEventConfig(config)
    }

    @Test
    fun testPartitionsRevoked() {
        val (stateAndEventListener, eventConsumer, stateConsumer, mapFactory, partitions) = setupMocks()
        val partitionId = partitions.first().partition()
        val partitionState = StateAndEventPartitionState<String, String>(
            mutableMapOf(partitionId to mutableMapOf()),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val rebalanceListener =
            StateAndEventRebalanceListener(
                stateAndEventConfig,
                mapFactory,
                eventConsumer,
                stateConsumer,
                partitionState,
                stateAndEventListener
            )
        rebalanceListener.onPartitionsRevoked(partitions)

        verify(stateConsumer, times(1)).assignment()
        verify(stateConsumer, times(1)).assign(any())
        verify(stateAndEventListener, times(1)).onPartitionLost(any())
        verify(mapFactory, times(1)).destroyMap(any())
    }

    @Test
    fun testPartitionsAssigned() {
        val (stateAndEventListener, eventConsumer, stateConsumer, mapFactory, partitions) = setupMocks()
        val partitionId = partitions.first().partition()
        val partitionState = StateAndEventPartitionState<String, String>(
            mutableMapOf(partitionId to mutableMapOf()),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val rebalanceListener =
            StateAndEventRebalanceListener(
                stateAndEventConfig, mapFactory, eventConsumer, stateConsumer, partitionState,
                stateAndEventListener
            )
        rebalanceListener.onPartitionsAssigned(partitions)

        verify(stateConsumer, times(1)).seekToBeginning(any())
        verify(stateConsumer, times(1)).assign(any())
        verify(eventConsumer, times(1)).pause(any())
    }

    private fun setupMocks(): Mocks {
        val listener: StateAndEventListener<String, String> = mock()
        val eventConsumer: CordaKafkaConsumer<String, String> = mock()
        val stateConsumer: CordaKafkaConsumer<String, String> = mock()

        val topicPartitions = setOf(TopicPartition(TOPIC, 0))
        val mapFactory = mock<SubscriptionMapFactory<String, Pair<Long, String>>>()

        doAnswer { topicPartitions }.whenever(stateConsumer).assignment()

        return Mocks(listener, eventConsumer, stateConsumer, mapFactory, topicPartitions)
    }

    data class Mocks(
        val stateAndEventListener: StateAndEventListener<String, String>,
        val eventConsumer: CordaKafkaConsumer<String, String>,
        val stateConsumer: CordaKafkaConsumer<String, String>,
        val mapFactory: SubscriptionMapFactory<String, Pair<Long, String>>,
        val partitions: Set<TopicPartition>
    )
}