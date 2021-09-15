package net.corda.messaging.kafka.subscription.consumer.wrapper

import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.subscription.consumer.wrapper.impl.StateAndEventConsumerImpl
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.TOPIC_PREFIX
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.getTestStateAndEventConfig
import net.corda.messaging.kafka.types.StateAndEventConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock

class StateAndEventConsumerImplTest {

    private companion object {
        const val TOPIC = "topic"
    }

    @Test
    fun testClose() {
        val (stateAndEventListener, eventConsumer, stateConsumer, config, partitions) = setupMocks()
        val partitionId = partitions.first().partition()
        val partitionState = StateAndEventPartitionState<String, String>(
            mutableMapOf(partitionId to mutableMapOf()),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val consumer = StateAndEventConsumerImpl(config, eventConsumer, stateConsumer, partitionState, stateAndEventListener)
        consumer.close()

        verify(eventConsumer, times(1)).close(any())
        verify(stateConsumer, times(1)).close(any())
    }

    @Test
    fun testGetValue() {
        val (stateAndEventListener, eventConsumer, stateConsumer, config, partitions) = setupMocks()
        val partitionId = partitions.first().partition()
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(
                partitionId to mutableMapOf(
                    "key1" to Pair(
                        Long.MIN_VALUE,
                        "value1"
                    )
                )
            ), mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val consumer = StateAndEventConsumerImpl(config, eventConsumer, stateConsumer, partitionState, stateAndEventListener)
        val valueKey1 = consumer.getInMemoryStateValue("key1")
        assertThat(valueKey1).isEqualTo("value1")
        val valueKey2 = consumer.getInMemoryStateValue("key2")
        assertThat(valueKey2).isNull()
    }

    @Test
    fun testOnProcessorStateUpdated() {
        val (stateAndEventListener, eventConsumer, stateConsumer, config, partitions) = setupMocks()
        val partitionId = partitions.first().partition()
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(partitionId to mutableMapOf("key1" to Pair(Long.MIN_VALUE, "value1"))),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val consumer = StateAndEventConsumerImpl(config, eventConsumer, stateConsumer, partitionState, stateAndEventListener)

        consumer.updateInMemoryStatePostCommit(
            mutableMapOf(partitionId to mutableMapOf("key1" to null, "key2" to "value2")),
            Clock.systemUTC()
        )

        val currentStates = partitionState.currentStates
        assertThat(currentStates[partitionId]?.get("key1")).isNull()
        assertThat(currentStates[partitionId]?.get("key2")).isNotNull

        verify(stateAndEventListener, times(1)).onPostCommit(any())
    }

    @Test
    fun testUpdateStates() {
        val (stateAndEventListener, eventConsumer, stateConsumer, config, partitions) = setupMocks()
        val partitionId = partitions.first().partition()
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(partitionId to mutableMapOf("key1" to Pair(Long.MIN_VALUE, "value1"))),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val consumer = StateAndEventConsumerImpl(config, eventConsumer, stateConsumer, partitionState, stateAndEventListener)

        consumer.pollAndUpdateStates(true)

        verify(stateConsumer, times(1)).assignment()
        verify(stateConsumer, times(1)).poll()
        verify(stateConsumer, times(1)).poll()
        verify(stateAndEventListener, times(1)).onPartitionSynced(any())
    }

    @Test
    fun testUpdateStatesNoSync() {
        val (stateAndEventListener, eventConsumer, stateConsumer, config, partitions) = setupMocks()
        val partitionId = partitions.first().partition()
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(partitionId to mutableMapOf("key1" to Pair(Long.MIN_VALUE, "value1"))),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val consumer = StateAndEventConsumerImpl(config, eventConsumer, stateConsumer, partitionState, stateAndEventListener)

        consumer.pollAndUpdateStates(false)

        verify(stateConsumer, times(1)).assignment()
        verify(stateConsumer, times(1)).poll()
        verify(stateConsumer, times(1)).poll()
        verify(stateAndEventListener, times(0)).onPartitionSynced(any())
    }

    private fun setupMocks(): Mocks {
        val listener: StateAndEventListener<String, String> = mock()
        val eventConsumer: CordaKafkaConsumer<String, String> = mock()
        val stateConsumer: CordaKafkaConsumer<String, String> = mock()

        val topicPartitions = setOf(TopicPartition(TOPIC, 0))
        val config = getTestStateAndEventConfig()

        val state = ConsumerRecordAndMeta<String, String>(
            TOPIC_PREFIX,
            ConsumerRecord(TOPIC, 0, 0, "key", "state5")
        )

        doAnswer { topicPartitions }.whenever(stateConsumer).assignment()
        doAnswer { listOf(state) }.whenever(stateConsumer).poll()
        doAnswer { Long.MAX_VALUE }.whenever(stateConsumer).position(any())

        return Mocks(listener, eventConsumer, stateConsumer, config, topicPartitions)
    }

    data class Mocks(
        val stateAndEventListener: StateAndEventListener<String, String>,
        val eventConsumer: CordaKafkaConsumer<String, String>,
        val stateConsumer: CordaKafkaConsumer<String, String>,
        val config: StateAndEventConfig,
        val partitions: Set<TopicPartition>
    )
}
