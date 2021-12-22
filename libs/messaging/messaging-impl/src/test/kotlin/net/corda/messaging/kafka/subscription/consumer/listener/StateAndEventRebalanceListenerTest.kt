package net.corda.messaging.kafka.subscription.consumer.listener

import com.typesafe.config.Config
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.properties.ConfigProperties
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.messaging.subscription.config.StateAndEventConfig
import net.corda.messaging.subscription.consumer.StateAndEventConsumer
import net.corda.messaging.subscription.consumer.StateAndEventPartitionState
import net.corda.messaging.subscription.consumer.listener.StateAndEventRebalanceListener
import net.corda.messaging.subscription.factory.MapFactory
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
        val (stateAndEventListener, stateAndEventConsumer, mapFactory, partitions) = setupMocks()
        val partitionId = partitions.first().partition
        val partitionState =
            StateAndEventPartitionState<String, String>(
                mutableMapOf(partitionId to mutableMapOf()),
                mutableMapOf(partitionId to Long.MAX_VALUE)
            )
        val rebalanceListener =
            StateAndEventRebalanceListener(
                stateAndEventConfig,
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
            StateAndEventRebalanceListener(
                stateAndEventConfig,
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
