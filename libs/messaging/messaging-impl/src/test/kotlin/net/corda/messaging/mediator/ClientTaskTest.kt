package net.corda.messaging.mediator

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_ENDPOINT
import net.corda.messaging.api.mediator.RoutingDestination
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ClientTaskTest {
    private data class StateType(val id: Int)
    private data class EventType(val id: String)

    private val messageRouter = mock<MessageRouter>()
    private val routingDestination = mock<RoutingDestination>()
    private val messagingClient = mock<MessagingClient>()
    private val clientDeferredReply = mock<Deferred<MediatorMessage<*>>>()
    private val clientReply = mock<MediatorMessage<*>>()

    @BeforeEach
    fun setup() {
        `when`(messageRouter.getDestination(any())).thenReturn(
            routingDestination
        )
        `when`(routingDestination.client).thenReturn(
            messagingClient
        )
        `when`(messagingClient.send(any())).thenReturn(
            clientDeferredReply
        )
        runBlocking {
            `when`(clientDeferredReply.await()).thenReturn(
                clientReply
            )
        }
    }

    @Test
    fun `successfully sends messages`() {

        val message = mock<MediatorMessage<Any>>()
        val task = ClientTask<String, StateType, EventType>(
            message,
            messageRouter,
            mock(),
        )

        val result = task.call()

        assertNotNull(result)
        verify(messageRouter).getDestination(message)
        verify(message).addProperty(eq(MSG_PROP_ENDPOINT), anyOrNull())
        verify(messagingClient).send(message)
        assertEquals(clientReply, result.replyMessage)
    }
}