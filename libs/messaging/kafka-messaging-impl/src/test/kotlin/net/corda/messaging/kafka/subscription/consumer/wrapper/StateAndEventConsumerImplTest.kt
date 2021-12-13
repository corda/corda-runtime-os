package net.corda.messaging.kafka.subscription.consumer.wrapper

import com.typesafe.config.Config
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.properties.ConfigProperties
import net.corda.messaging.kafka.subscription.consumer.wrapper.impl.StateAndEventConsumerImpl
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.TOPIC_PREFIX
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.messaging.kafka.types.StateAndEventConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.concurrent.CountDownLatch

class StateAndEventConsumerImplTest {

    private companion object {
        const val TOPIC = "topic"
        private val config: Config = createStandardTestConfig().getConfig(ConfigProperties.PATTERN_STATEANDEVENT)
        private val stateAndEventConfig = StateAndEventConfig.getStateAndEventConfig(config)
    }

    @Test
    fun testClose() {
        val (stateAndEventListener, eventConsumer, stateConsumer, partitions) = setupMocks()
        val partitionId = partitions.first().partition()
        val partitionState = StateAndEventPartitionState<String, String>(
            mutableMapOf(partitionId to mutableMapOf()),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val consumer =
            StateAndEventConsumerImpl(
                stateAndEventConfig,
                eventConsumer,
                stateConsumer,
                partitionState,
                stateAndEventListener
            )
        consumer.close()

        verify(eventConsumer, times(1)).close(any())
        verify(stateConsumer, times(1)).close(any())
    }

    @Test
    fun testGetValue() {
        val (stateAndEventListener, eventConsumer, stateConsumer, partitions) = setupMocks()
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
        val consumer =
            StateAndEventConsumerImpl(
                stateAndEventConfig,
                eventConsumer,
                stateConsumer,
                partitionState,
                stateAndEventListener
            )
        val valueKey1 = consumer.getInMemoryStateValue("key1")
        assertThat(valueKey1).isEqualTo("value1")
        val valueKey2 = consumer.getInMemoryStateValue("key2")
        assertThat(valueKey2).isNull()
    }

    @Test
    fun testOnProcessorStateUpdated() {
        val (stateAndEventListener, eventConsumer, stateConsumer, partitions) = setupMocks()
        val partitionId = partitions.first().partition()
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(partitionId to mutableMapOf("key1" to Pair(Long.MIN_VALUE, "value1"))),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val consumer =
            StateAndEventConsumerImpl(
                stateAndEventConfig,
                eventConsumer,
                stateConsumer,
                partitionState,
                stateAndEventListener
            )

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
        val (stateAndEventListener, eventConsumer, stateConsumer, partitions) = setupMocks()
        val partitionId = partitions.first().partition()
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(partitionId to mutableMapOf("key1" to Pair(Long.MIN_VALUE, "value1"))),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val consumer =
            StateAndEventConsumerImpl(
                stateAndEventConfig,
                eventConsumer,
                stateConsumer,
                partitionState,
                stateAndEventListener
            )

        consumer.pollAndUpdateStates(true)

        verify(stateConsumer, times(1)).assignment()
        verify(stateConsumer, times(1)).poll()
        verify(stateConsumer, times(1)).poll()
        verify(stateAndEventListener, times(1)).onPartitionSynced(any())
    }

    @Test
    fun testUpdateStatesNoSync() {
        val (stateAndEventListener, eventConsumer, stateConsumer, partitions) = setupMocks()
        val partitionId = partitions.first().partition()
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(partitionId to mutableMapOf("key1" to Pair(Long.MIN_VALUE, "value1"))),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val consumer = StateAndEventConsumerImpl(
            stateAndEventConfig,
            eventConsumer,
            stateConsumer,
            partitionState,
            stateAndEventListener
        )

        consumer.pollAndUpdateStates(false)

        verify(stateConsumer, times(1)).assignment()
        verify(stateConsumer, times(1)).poll()
        verify(stateConsumer, times(1)).poll()
        verify(stateAndEventListener, times(0)).onPartitionSynced(any())
    }

    @Test
    fun testWaitForFunctionToFinish() {
        val (stateAndEventListener, eventConsumer, stateConsumer, partitions) = setupMocks()
        val partitionId = partitions.first().partition()
        val partitionState = StateAndEventPartitionState(
            mutableMapOf(partitionId to mutableMapOf("key1" to Pair(Long.MIN_VALUE, "value1"))),
            mutableMapOf(partitionId to Long.MAX_VALUE)
        )
        val consumer = StateAndEventConsumerImpl(
            stateAndEventConfig,
            eventConsumer,
            stateConsumer,
            partitionState,
            stateAndEventListener
        )
        val latch = CountDownLatch(1)
        consumer.waitForFunctionToFinish({
            while (latch.count > 0) {
                Thread.sleep(10)
            }
        }, 200L, "test ")
        latch.countDown()

        verify(eventConsumer, times(1)).assignment()
        verify(eventConsumer, times(1)).paused()
        verify(eventConsumer, times(1)).pause(any())
        verify(eventConsumer, times(1)).resume(any())
        verify(stateConsumer, atLeast(1)).poll()
        verify(stateAndEventListener, times(0)).onPartitionSynced(any())
    }

    @Test
    fun testResetPollPosition() {
        val (stateAndEventListener, eventConsumer, stateConsumer, _) = setupMocks()
        val consumer = StateAndEventConsumerImpl(
            stateAndEventConfig, eventConsumer, stateConsumer, StateAndEventPartitionState
                (mutableMapOf(), mutableMapOf()), stateAndEventListener
        )

        consumer.resetPollInterval()

        verify(eventConsumer, times(1)).assignment()
        verify(eventConsumer, times(1)).paused()
        verify(eventConsumer, times(1)).pause(any())
        verify(eventConsumer, times(1)).resume(any())
    }

    private fun setupMocks(): Mocks {
        val listener: StateAndEventListener<String, String> = mock()
        val eventConsumer: CordaKafkaConsumer<String, String> = mock()
        val stateConsumer: CordaKafkaConsumer<String, String> = mock()

        val topicPartitions = setOf(TopicPartition(TOPIC, 0))

        val state = ConsumerRecord(TOPIC_PREFIX + TOPIC, 0, 0, "key", "state5")

        doAnswer { topicPartitions }.whenever(stateConsumer).assignment()
        doAnswer { listOf(state) }.whenever(stateConsumer).poll()
        doAnswer { Long.MAX_VALUE }.whenever(stateConsumer).position(any())

        return Mocks(listener, eventConsumer, stateConsumer, topicPartitions)
    }

    data class Mocks(
        val stateAndEventListener: StateAndEventListener<String, String>,
        val eventConsumer: CordaKafkaConsumer<String, String>,
        val stateConsumer: CordaKafkaConsumer<String, String>,
        val partitions: Set<TopicPartition>
    )
}
