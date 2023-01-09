package net.corda.messaging.subscription.consumer.listener

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.constants.SubscriptionType
import net.corda.messaging.createResolvedSubscriptionConfig
import net.corda.messaging.subscription.consumer.StateAndEventConsumer
import net.corda.messaging.subscription.consumer.StateAndEventPartitionState
import net.corda.messaging.subscription.factory.MapFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
                mutableMapOf(partitionId to mutableMapOf()),
                mutableMapOf(partitionId to Long.MAX_VALUE)
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

        val stateConsumer = stateAndEventConsumer.stateConsumer
        verify(stateConsumer, times(1)).assignment()
        verify(stateConsumer, times(1)).assign(any())
        verify(stateAndEventListener, times(1)).onPartitionLost(any())
        verify(mapFactory, times(1)).destroyMap(any())
        assertThat(partitionState.dirty).isTrue
    }

    @Test
    fun testPartitionsAssigned() {
        val (stateAndEventListener, stateAndEventConsumer, mapFactory, partitions) = setupMocks()
        val partitionId = partitions.first().partition
        val partitionState =
            StateAndEventPartitionState<String, String>(
                mutableMapOf(),
                mutableMapOf(partitionId to Long.MAX_VALUE)
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

        val stateConsumer = stateAndEventConsumer.stateConsumer
        val eventConsumer = stateAndEventConsumer.eventConsumer
        verify(stateConsumer, times(1)).seekToBeginning(any())
        verify(stateConsumer, times(1)).assign(any())
        verify(eventConsumer, times(1)).pause(any())
        verify(mapFactory, times(1)).createMap()
        assertThat(partitionState.dirty).isTrue
    }

    @Test
    fun testClose() {
        val (stateAndEventListener, stateAndEventConsumer, mapFactory) = setupMocks()
        val currentState = mutableMapOf(
            1 to mutableMapOf("K1" to Pair(1L, "value1")),
            2 to mutableMapOf("K2" to Pair(2L, "value2"))
        )
        val partitionState = StateAndEventPartitionState(
            currentState,
            mutableMapOf()
        )

        val rebalanceListener = StateAndEventConsumerRebalanceListenerImpl(
            config,
            mapFactory,
            stateAndEventConsumer,
            partitionState,
            stateAndEventListener
        )

        rebalanceListener.close()

        verify(stateAndEventListener).onPartitionLost(argThat { states -> states["K1"] == "value1" })
        verify(stateAndEventListener).onPartitionLost(argThat { states -> states["K2"] == "value2" })
    }

    private fun setupMocks(): Mocks {
        val listener: StateAndEventListener<String, String> = mock()
        val stateAndEventConsumer: StateAndEventConsumer<String, String, String> = mock()
        val eventConsumer: CordaConsumer<String, String> = mock()
        val stateConsumer: CordaConsumer<String, String> = mock()

        val topicPartitions = setOf(CordaTopicPartition(TOPIC, 0))
        val mapFactory = mock<MapFactory<String, Pair<Long, String>>>()

        doAnswer { topicPartitions }.whenever(stateConsumer).assignment()
        whenever(stateAndEventConsumer.eventConsumer).thenReturn(eventConsumer)
        whenever(stateAndEventConsumer.stateConsumer).thenReturn(stateConsumer)

        return Mocks(listener, stateAndEventConsumer, mapFactory, topicPartitions)
    }

    data class Mocks(
        val stateAndEventListener: StateAndEventListener<String, String>,
        val stateAndEventConsumer: StateAndEventConsumer<String, String, String>,
        val mapFactory: MapFactory<String, Pair<Long, String>>,
        val partitions: Set<CordaTopicPartition>
    )
}
