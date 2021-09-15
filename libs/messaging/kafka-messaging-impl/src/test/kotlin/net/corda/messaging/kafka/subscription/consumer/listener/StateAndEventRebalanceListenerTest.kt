package net.corda.messaging.kafka.subscription.consumer.listener

import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventPartitionState
import net.corda.messaging.kafka.subscription.factory.SubscriptionMapFactory
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.getTestStateAndEventConfig
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
    }

    @Test
    fun testPartitionsRevoked() {
        val (stateAndEventListener, stateAndEventConsumer, config, mapFactory, partitions) = setupMocks()
        val partitionId = partitions.first().partition()
        val partitionState = StateAndEventPartitionState<String, String>(
            mutableMapOf(partitionId to mutableMapOf()),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val rebalanceListener =
            StateAndEventRebalanceListener(config, mapFactory, stateAndEventConsumer, partitionState, stateAndEventListener)
        rebalanceListener.onPartitionsRevoked(partitions)

        val stateConsumer = stateAndEventConsumer.stateConsumer
        verify(stateConsumer, times(1)).assignment()
        verify(stateConsumer, times(1)).assign(any())
        verify(stateAndEventConsumer, times(1)).waitForFunctionToFinish(any(), any(), any())
        verify(mapFactory, times(1)).destroyMap(any())
    }

    @Test
    fun testPartitionsAssigned() {
        val (stateAndEventListener, stateAndEventConsumer, config, mapFactory, partitions) = setupMocks()
        val partitionId = partitions.first().partition()
        val partitionState = StateAndEventPartitionState<String, String>(
            mutableMapOf(partitionId to mutableMapOf()),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val rebalanceListener =
            StateAndEventRebalanceListener(config, mapFactory, stateAndEventConsumer, partitionState, stateAndEventListener)
        rebalanceListener.onPartitionsAssigned(partitions)

        val stateConsumer = stateAndEventConsumer.stateConsumer
        val eventConsumer = stateAndEventConsumer.eventConsumer
        verify(stateConsumer, times(1)).seekToBeginning(any())
        verify(stateConsumer, times(1)).assign(any())
        verify(eventConsumer, times(1)).pause(any())
    }

    private fun setupMocks(): Mocks {
        val listener: StateAndEventListener<String, String> = mock()
        val stateAndEventConsumer: StateAndEventConsumer<String, String, String> = mock()
        val eventConsumer: CordaKafkaConsumer<String, String> = mock()
        val stateConsumer: CordaKafkaConsumer<String, String> = mock()

        val topicPartitions = setOf(TopicPartition(TOPIC, 0))
        val config = getTestStateAndEventConfig()
        val mapFactory = mock<SubscriptionMapFactory<String, Pair<Long, String>>>()

        doAnswer { topicPartitions }.whenever(stateConsumer).assignment()
        whenever(stateAndEventConsumer.eventConsumer).thenReturn(eventConsumer)
        whenever(stateAndEventConsumer.stateConsumer).thenReturn(stateConsumer)

        return Mocks(listener, stateAndEventConsumer, config, mapFactory, topicPartitions)
    }

    data class Mocks(
        val stateAndEventListener: StateAndEventListener<String, String>,
        val stateAndEventConsumer: StateAndEventConsumer<String, String, String>,
        val config: StateAndEventConfig,
        val mapFactory: SubscriptionMapFactory<String, Pair<Long, String>>,
        val partitions: Set<TopicPartition>
    )
}