package net.corda.lifecycle.domino.logic.util

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class PublisherWithDominoLogicTest {

    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        var currentStatus: LifecycleStatus = LifecycleStatus.DOWN
        on { postEvent(any()) } doAnswer {
            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
        on { start() } doAnswer {
            handler.lastValue.processEvent(StartEvent(), mock)
        }
        on { stop() } doAnswer {
            handler.lastValue.processEvent(StopEvent(), mock)
        }
        on { updateStatus(any(), any()) } doAnswer { currentStatus =  it.getArgument(0) }
        on { status } doAnswer { currentStatus }
    }
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val messagingConfig = mock<SmartConfig>()
    private val publisher = mock<Publisher>()
    private val factory = mock<PublisherFactory> {
        on { createPublisher(any(), eq(messagingConfig)) } doReturn publisher
    }

    private val wrapper = PublisherWithDominoLogic(factory, coordinatorFactory, PublisherConfig(""), messagingConfig)

    @Test
    fun `publishToPartition will call the publisher`() {
        wrapper.start()
        wrapper.publishToPartition(listOf(1 to mock()))

        verify(publisher).publishToPartition(any())
    }

    @Test
    fun `publish will call the publisher`() {
        wrapper.start()
        wrapper.publish(listOf(mock()))

        verify(publisher).publish(any())
    }

    @Test
    fun `publishToPartition will throw an exception if not started`() {
        assertThrows<IllegalStateException>() {
            wrapper.publishToPartition(listOf(1 to mock()))
        }
    }

    @Test
    fun `publish will throw an exception if not started`() {
        assertThrows<IllegalStateException>() {
            wrapper.publish(mock())
        }
    }
}