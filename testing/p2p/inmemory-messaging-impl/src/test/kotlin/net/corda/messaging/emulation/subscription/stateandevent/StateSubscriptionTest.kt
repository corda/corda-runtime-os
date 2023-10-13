package net.corda.messaging.emulation.subscription.stateandevent

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock

class StateSubscriptionTest {
    private val runningConsumption = mock<Consumption> {
        on { isRunning } doReturn true
    }
    private val topicService = mock<TopicService> {
        on { createConsumption(any()) } doReturn runningConsumption
        on { getLatestOffsets("topic1") } doReturn mapOf(1 to 3L, 3 to 4L)
    }
    private val stateListener = mock<StateAndEventListener<String, String>>()
    private val subscription = mock<InMemoryStateAndEventSubscription<String, String, String>> {
        on { topicService } doReturn topicService
        on { subscriptionConfig } doReturn SubscriptionConfig("group", "topic")
        on { stateSubscriptionConfig } doReturn SubscriptionConfig("group1", "topic1")
        on { processor } doReturn object : StateAndEventProcessor<String, String, String> {
            override fun onNext(state: String?, event: Record<String, String>): StateAndEventProcessor.Response<String> {
                return StateAndEventProcessor.Response(null, emptyList())
            }

            override val keyClass = String::class.java
            override val stateValueClass = String::class.java
            override val eventValueClass = String::class.java
        }
        on { stateAndEventListener } doReturn stateListener
    }
    private val stateSubscription = StateSubscription(subscription)

    @Test
    fun `start will create a new consumption`() {
        stateSubscription.start()

        verify(topicService).createConsumption(isA<StatesConsumer<String, String>>())
    }

    @Test
    fun `second start will create a new consumption only once`() {
        stateSubscription.start()
        stateSubscription.start()

        verify(topicService, times(1)).createConsumption(isA<StatesConsumer<String, String>>())
    }

    @Test
    fun `isRunning will return false if not started`() {
        assertThat(stateSubscription.isRunning).isFalse
    }

    @Test
    fun `isRunning will return consumption state if running`() {
        stateSubscription.start()

        assertThat(stateSubscription.isRunning).isTrue
    }

    @Test
    fun `stop will stop the consumption`() {
        stateSubscription.start()

        stateSubscription.stop()

        verify(runningConsumption).stop()
    }

    @Test
    fun `second stop will not stop the consumption again`() {
        stateSubscription.start()

        stateSubscription.stop()
        stateSubscription.stop()

        verify(runningConsumption, times(1)).stop()
    }

    @Test
    fun `gotStates will update the states`() {
        stateSubscription.onPartitionsAssigned(listOf("topic" to 1))
        val records = listOf(
            RecordMetadata(1, Record("topic", "key1", "value"), 1),
            RecordMetadata(1, Record("topic", "key1", 2), 1),
            RecordMetadata(1, Record("topic", 2, "value1"), 1),
            RecordMetadata(1, Record("topic", "key1", "value2"), 2),
        )

        stateSubscription.gotStates(records)

        assertThat(stateSubscription.getValue("key1")).isEqualTo("value")
    }

    @Test
    fun `gotStates will not update listener if not ready`() {
        stateSubscription.onPartitionsAssigned(listOf("topic" to 1))
        val records = listOf(
            RecordMetadata(1, Record("topic", "key1", "value"), 1),
            RecordMetadata(1, Record("topic", "key1", 2), 1),
            RecordMetadata(1, Record("topic", 2, "value1"), 1),
            RecordMetadata(1, Record("topic", "key1", "value2"), 2),
        )

        stateSubscription.gotStates(records)

        verify(stateListener, never()).onPostCommit(any())
    }

    @Test
    fun `gotStates will report synch for empty partition`() {
        stateSubscription.onPartitionsAssigned(listOf("topic" to 5))

        verify(stateListener).onPartitionSynced(emptyMap())
    }

    @Test
    fun `gotStates will report synch when partition is ready`() {
        stateSubscription.onPartitionsAssigned(listOf("topic" to 1))

        val records = (0..3).map {
            RecordMetadata(it.toLong(), Record("topic", "key:$it", "value:$it"), 1)
        }

        stateSubscription.gotStates(records)

        verify(stateListener).onPartitionSynced(
            (0..3)
                .associate { "key:$it" to "value:$it" }
        )
    }

    @Test
    fun `onPartitionsUnassigned will report lost partitions`() {
        stateSubscription.onPartitionsAssigned(listOf("topic" to 4, "topic" to 5))
        stateSubscription.setValue("key1", "value1", 4)
        stateSubscription.setValue("key2", null, 4)
        stateSubscription.setValue("key3", "value3", 4)
        stateSubscription.setValue("key4", "value4", 5)
        stateSubscription.setValue("key6", "value6", 6)

        stateSubscription.onPartitionsUnassigned(listOf("topic" to 4, "topic" to 6))

        verify(stateListener).onPartitionLost(
            mapOf(
                "key1" to "value1",
                "key3" to "value3",
            )
        )
    }

    @Test
    fun `onPostCommit not called for irrelevant data`() {
        stateSubscription.onPartitionsAssigned(listOf("topic" to 4, "topic" to 5))
        stateSubscription.setValue("key1", "value1", 4)
        stateSubscription.setValue("key2", null, 4)
        stateSubscription.setValue("key3", "value3", 4)
        stateSubscription.setValue("key4", "value4", 5)
        stateSubscription.setValue("key6", "value6", 6)
        stateSubscription.setValue("key7", "value7", 4)

        stateSubscription.gotStates(
            listOf(
                RecordMetadata(1L, Record("topic", "key44", null), 5),
            )
        )

        verify(stateListener, never()).onPostCommit(any())
    }

    @Test
    fun `getValue return first existing value`() {
        stateSubscription.onPartitionsAssigned(listOf("topic" to 4, "topic" to 5))
        stateSubscription.setValue("key1", "value1", 4)
        stateSubscription.setValue("key2", null, 4)
        stateSubscription.setValue("key3", "value3", 4)
        stateSubscription.setValue("key4", "value4", 5)
        stateSubscription.setValue("key6", "value6", 6)

        assertThat(stateSubscription.getValue("key3")).isEqualTo("value3")
    }

    @Test
    fun `getValue return null for non existing value`() {
        stateSubscription.onPartitionsAssigned(listOf("topic" to 4, "topic" to 5))
        stateSubscription.setValue("key1", "value1", 4)
        stateSubscription.setValue("key2", null, 4)
        stateSubscription.setValue("key3", "value3", 4)
        stateSubscription.setValue("key4", "value4", 5)
        stateSubscription.setValue("key6", "value6", 6)

        assertThat(stateSubscription.getValue("key6")).isNull()
    }

    @Test
    fun `getValue return null for null value`() {
        stateSubscription.onPartitionsAssigned(listOf("topic" to 4, "topic" to 5))
        stateSubscription.setValue("key1", "value1", 4)
        stateSubscription.setValue("key2", null, 4)
        stateSubscription.setValue("key3", "value3", 4)
        stateSubscription.setValue("key4", "value4", 5)
        stateSubscription.setValue("key6", "value6", 6)

        assertThat(stateSubscription.getValue("key2")).isNull()
    }

    @Test
    fun `waitForReady will return if subscription is not running`() {
        stateSubscription.waitForReady()
    }

    @Test
    fun `waitForReady will return if all partitions are ready`() {
        stateSubscription.start()
        stateSubscription.onPartitionsAssigned(listOf("topic" to 4, "topic" to 5))

        stateSubscription.waitForReady()
    }

    @Test
    fun `waitForReady will return only when all partitions are ready`() {
        val condition = mock<Condition>()
        val lock = mock<Lock> {
            on { newCondition() } doReturn condition
        }
        val stateSubscription = StateSubscription(subscription, lock)
        doAnswer {
            stateSubscription.gotStates(
                listOf(
                    RecordMetadata(4L, Record("topic", "key1", null), 3),
                )
            )
            false
        }.whenever(condition).await(any(), any())
        stateSubscription.start()
        stateSubscription.onPartitionsAssigned(listOf("topic" to 3, "topic" to 5))

        stateSubscription.waitForReady()

        verify(condition, times(1)).await(any(), any())
    }
}
