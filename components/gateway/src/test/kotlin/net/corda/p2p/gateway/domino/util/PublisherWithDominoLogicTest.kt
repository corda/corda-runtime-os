package net.corda.p2p.gateway.domino.util

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.messaging.api.publisher.Publisher
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class PublisherWithDominoLogicTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { start() } doAnswer {
            handler.lastValue.processEvent(StartEvent(), mock)
        }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val publisher = mock<Publisher>()

    private val wrapper = PublisherWithDominoLogic(publisher, factory)

    @Test
    fun `createResources will start the publisher`() {
        wrapper.start()

        verify(publisher).start()
    }

    @Test
    fun `createResources will set the state to up`() {
        wrapper.start()

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `createResources will remember to close the publisher`() {
        wrapper.start()
        wrapper.stop()

        verify(publisher).close()
    }

    @Test
    fun `publishToPartition will call the publisher`() {
        wrapper.publishToPartition(listOf(1 to mock()))

        verify(publisher).publishToPartition(any())
    }

    @Test
    fun `publish will call the publisher`() {
        wrapper.publish(listOf(mock()))

        verify(publisher).publish(any())
    }
}
