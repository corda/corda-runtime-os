package net.corda.messaging.emulation.subscription.stateandevent

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.service.TopicService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InMemoryStateAndEventSubscriptionTest {
    private val eventsConsumption = mock<Consumption> {
        on { isRunning } doReturn true
    }
    private val statesConsumption = mock<Consumption> {
        on { isRunning } doReturn true
    }
    private val topicService = mock<TopicService> {
        on { createConsumption(isA<EventConsumer<String, String>>()) } doReturn eventsConsumption
        on { createConsumption(isA<StatesConsumer<String, String>>()) } doReturn statesConsumption
    }

    private var lifecycleCoordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn lifecycleCoordinator
    }

    private val subscription = InMemoryStateAndEventSubscription<String, String, String>(
        SubscriptionConfig("group", "topic"),
        mock(),
        mock(),
        topicService,
        lifecycleCoordinatorFactory,
        1
    )

    @BeforeEach
    fun setup() {
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())
    }

    @Test
    fun `stateSubscriptionConfig return different config`() {
        assertThat(subscription.stateSubscriptionConfig)
            .isEqualTo(
                SubscriptionConfig(
                    "group.state",
                    "topic.state"
                )
            )
    }

    @Test
    fun `start will start  event consumer`() {
        subscription.start()

        verify(topicService).createConsumption(isA<EventConsumer<String, String>>())
    }

    @Test
    fun `start will start  state consumer`() {
        subscription.start()

        verify(topicService).createConsumption(isA<StatesConsumer<String, String>>())
    }

    @Test
    fun `is running will return false if state consumer is not running`() {
        subscription.start()
        whenever(statesConsumption.isRunning).doReturn(false)

        assertThat(subscription.isRunning).isFalse
    }

    @Test
    fun `is running will return false if event consumer is not running`() {
        subscription.start()
        whenever(eventsConsumption.isRunning).doReturn(false)

        assertThat(subscription.isRunning).isFalse
    }

    @Test
    fun `is running will return true if both consumers running`() {
        subscription.start()

        assertThat(subscription.isRunning).isTrue
    }

    @Test
    fun `stop will stop the event consumption`() {
        subscription.start()

        subscription.stop()

        verify(eventsConsumption).stop()
    }

    @Test
    fun `stop will stop the state consumption`() {
        subscription.start()

        subscription.stop()

        verify(statesConsumption).stop()
    }

    @Test
    fun `setValue will update the value`() {
        whenever(topicService.createConsumption(isA<StatesConsumer<String, String>>())).doAnswer {
            (it.arguments[0] as? Consumer)?.partitionAssignmentListener?.onPartitionsAssigned(listOf("topic" to 1))
            statesConsumption
        }
        subscription.start()
        subscription.setValue("key", "value", 1)

        assertThat(subscription.stateSubscription.getValue("key")).isEqualTo("value")
    }
}
