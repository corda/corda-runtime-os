package net.corda.messaging.mediator

import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.RoutingDestination
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SendRecordsTaskTest {
    private val record = Record("topic", "key", "value")
    private val messageRouter = mock<MessageRouter>()
    private val routingDestination = mock<RoutingDestination>()
    private val messagingClient = mock<MessagingClient>()
    private val endpoint = "endpoint"

    @BeforeEach
    fun setup() {
        `when`(messageRouter.getDestination(any())).thenReturn(
            routingDestination
        )
        `when`(routingDestination.client).thenReturn(
            messagingClient
        )
        `when`(routingDestination.endpoint).thenReturn(
            endpoint
        )
    }

    @Test
    fun `successfully sends messages`() {
        val task = SendRecordsTask(
            listOf(record),
            messageRouter
        )

        task.call()

        verify(messageRouter).getDestination(any())
        verify(messagingClient).send(any())
    }
}