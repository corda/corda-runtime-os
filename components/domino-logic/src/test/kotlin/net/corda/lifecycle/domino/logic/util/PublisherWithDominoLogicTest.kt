package net.corda.lifecycle.domino.logic.util

import com.typesafe.config.Config
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.publisher.Publisher
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
        on { postEvent(any()) } doAnswer {
            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
    }
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val nodeConfig = mock<Config>()
    private val publisher = mock<Publisher>()
    private val factory = mock<PublisherFactory> {
        on { createPublisher(any(), eq(nodeConfig)) } doReturn publisher
    }

    private val wrapper = PublisherWithDominoLogic(factory, coordinatorFactory, "", nodeConfig)

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
