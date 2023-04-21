package net.corda.lifecycle.domino.logic.util

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RPCSenderWithDominoLogicTest {
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
    private val rpcSender = mock<RPCSender<RequestType, ResponseType>>()
    private val factory = mock<PublisherFactory> {
        on { createRPCSender(any<RPCConfig<RequestType, ResponseType>>(), eq(messagingConfig)) } doReturn rpcSender
    }
    class RequestType
    class ResponseType

    private val wrapper = RPCSenderWithDominoLogic(
        factory,
        coordinatorFactory,
        RPCConfig("", "", "", RequestType::class.java, ResponseType::class.java),
        messagingConfig
    )

    @Test
    fun `send request will call the publisher`() {
        wrapper.start()
        wrapper.sendRequest(RequestType())

        verify(rpcSender).sendRequest(any())
    }

}