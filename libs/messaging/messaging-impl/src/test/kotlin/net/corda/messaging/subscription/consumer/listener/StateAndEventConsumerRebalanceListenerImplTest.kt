package net.corda.messaging.subscription.consumer.listener

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messaging.api.subscription.data.TopicData
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.constants.SubscriptionType
import net.corda.messaging.createResolvedSubscriptionConfig
import net.corda.messaging.rocks.SimpleTopicDataImpl
import net.corda.messaging.subscription.consumer.StateAndEventConsumer
import net.corda.messaging.subscription.consumer.StateAndEventPartitionState
import net.corda.messaging.subscription.factory.TopicDataFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentHashMap

class StateAndEventConsumerRebalanceListenerImplTest {

    private companion object {
        const val TOPIC = "topic"
        private val config = createResolvedSubscriptionConfig(SubscriptionType.STATE_AND_EVENT)
    }

    @Test
    fun testPartitionsRevoked() {
        val (stateAndEventListener, stateAndEventConsumer, mapFactory, partitions) = setupMocks()
        val partitionId = partitions.first().partition
        val partitionState =
            StateAndEventPartitionState<String, String>(
                mutableMapOf(partitionId to SimpleTopicDataImpl())
            )
        val rebalanceListener =
            StateAndEventConsumerRebalanceListenerImpl(
                config,
                mapFactory,
                stateAndEventConsumer,
                partitionState,
                stateAndEventListener
            )
        rebalanceListener.onPartitionsRevoked(partitions)

        verify(stateAndEventConsumer, times(1)).onPartitionsRevoked(partitions)
        verify(stateAndEventListener, times(1)).onPartitionLost(any())
        verify(mapFactory, times(1)).destroy(any())
        assertThat(partitionState.dirty).isTrue
    }

    @Test
    fun testPartitionsAssigned() {
        val (stateAndEventListener, stateAndEventConsumer, mapFactory, partitions) = setupMocks()
        val partitionState =
            StateAndEventPartitionState<String, String>(
                mutableMapOf()
            )
        val rebalanceListener =
            StateAndEventConsumerRebalanceListenerImpl(
                config,
                mapFactory,
                stateAndEventConsumer,
                partitionState,
                stateAndEventListener
            )
        rebalanceListener.onPartitionsAssigned(partitions)

        verify(stateAndEventConsumer, times(1)).onPartitionsAssigned(partitions)
        verify(mapFactory, times(1)).create()
        assertThat(partitionState.dirty).isTrue
    }

    @Test
    fun testClose() {
        val (stateAndEventListener, stateAndEventConsumer, mapFactory) = setupMocks()
        val topicData1: TopicData<String, String> = SimpleTopicDataImpl(ConcurrentHashMap(mutableMapOf("K1" to "value1")))
        val topicData2: TopicData<String, String> = SimpleTopicDataImpl(ConcurrentHashMap(mutableMapOf("K2" to "value2")))
        val currentState = mutableMapOf(
            1 to topicData1,
            2 to topicData2
        )
        val partitionState = StateAndEventPartitionState(
            currentState
        )

        val rebalanceListener = StateAndEventConsumerRebalanceListenerImpl(
            config,
            mapFactory,
            stateAndEventConsumer,
            partitionState,
            stateAndEventListener
        )

        rebalanceListener.close()

        verify(stateAndEventListener).onPartitionLost(argThat { states -> states.get("K1") == "value1" })
        verify(stateAndEventListener).onPartitionLost(argThat { states -> states.get("K2") == "value2" })
    }

    private fun setupMocks(): Mocks {
        val listener: StateAndEventListener<String, String> = mock()
        val stateAndEventConsumer: StateAndEventConsumer<String, String, String> = mock()
        val eventConsumer: CordaConsumer<String, String> = mock()
        val stateConsumer: CordaConsumer<String, String> = mock()

        val topicPartitions = setOf(CordaTopicPartition(TOPIC, 0))
        val topicDataFactory = mock<TopicDataFactory<String, String>>()

        doAnswer { topicPartitions }.whenever(stateConsumer).assignment()
        whenever(stateAndEventConsumer.eventConsumer).thenReturn(eventConsumer)
        whenever(stateAndEventConsumer.stateConsumer).thenReturn(stateConsumer)

        return Mocks(listener, stateAndEventConsumer, topicDataFactory, topicPartitions)
    }

    data class Mocks(
        val stateAndEventListener: StateAndEventListener<String, String>,
        val stateAndEventConsumer: StateAndEventConsumer<String, String, String>,
        val topicDataFactory: TopicDataFactory<String, String>,
        val partitions: Set<CordaTopicPartition>
    )
}
