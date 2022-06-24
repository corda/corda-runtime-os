package net.cordapp.testing.chat

import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ChatFlowCollaborationTests {

    val outgoingInjector = FlowTestDependencyInjector {
        mockService<FlowMessaging>()
        mockService<FlowEngine>()
        mockService<JsonMarshallingService>()
    }

    val outgoingChatFlow = ChatOutgoingFlow().also {
        outgoingInjector.injectServices(it)
    }

    val incomingInjector = FlowTestDependencyInjector {
        mockService<FlowEngine>()
    }

    val incomingChatFlow = ChatIncomingFlow().also {
        incomingInjector.injectServices(it)
    }

    val RECIPIENT_X500_NAME = "CN=Bob, O=R3, L=London, C=GB"
    val FROM_X500_NAME = "CN=Alice, O=R3, L=London, C=GB"
    val MESSAGE = "chat message"

    @Test
    fun `flow sends message to correct recipient`() {
        /**
         * Check the two Flows have the correct annotations to be part of the same protocol
         */
        validateProtocol(outgoingChatFlow, incomingChatFlow)

        whenever(incomingInjector.serviceMock<FlowEngine>().virtualNodeName).thenReturn(MemberX500Name.parse(RECIPIENT_X500_NAME))

        val outgoingFlowSession = outgoingInjector.expectFlowMessagesTo(MemberX500Name.parse(RECIPIENT_X500_NAME))
        val incomingFlowSession = expectFlowMessagesFrom(MemberX500Name.parse(FROM_X500_NAME))

        join<MessageContainer>(from = outgoingFlowSession, to = incomingFlowSession)

        ExecuteConcurrently(
            {
                outgoingChatFlow.call(
                    outgoingInjector.rpcRequestGenerator(
                        OutgoingChatMessage(recipientX500Name = RECIPIENT_X500_NAME, message = MESSAGE)
                    ))
            },
            {
                Thread.sleep(1000) // TODO remove once Join works ok
                incomingChatFlow.call(incomingFlowSession)
            })

        // This is a temporary check, when chat uses the persistence api, we should check the mock persistence service
        val receivedMessages = MessageStore.readAndClear()
        Assertions.assertThat(receivedMessages.messages.size).isEqualTo(1)
        Assertions.assertThat(receivedMessages.messages[0])
            .isEqualTo(IncomingChatMessage(FROM_X500_NAME, MESSAGE))
    }
}
