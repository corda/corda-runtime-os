package net.corda.lifecycle.domino.logic.util

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.subscription.Subscription
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class EventLogSubscriptionWithDominoLogicTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { postEvent(any()) } doAnswer {
            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val subscription = mock<Subscription<String, String>>()

    private val wrapper = EventLogSubscriptionWithDominoLogic(subscription, factory)

    @Test
    fun `createResources will start the subscription`() {
        wrapper.start()

        verify(subscription).start()
    }

    @Test
    fun `createResources will set the state to up`() {
        wrapper.start()

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `createResources will remember to close the subscription`() {
        wrapper.start()
        wrapper.stop()

        verify(subscription).stop()
    }
}
