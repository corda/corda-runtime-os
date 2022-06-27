package net.cordapp.testing.chat

import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.types.MemberX500Name
import net.cordapp.testing.chatframework.FlowMockHelper
import net.cordapp.testing.chatframework.createFlow
import net.cordapp.testing.chatframework.mockService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChatOutgoingFlowTest {
    /**
     * Create an injector which can inject a Flow with mock versions of necessary services
     */
    val flowMockHelper = FlowMockHelper {
        mockService<FlowMessaging>()
        mockService<FlowEngine>()
        mockService<JsonMarshallingService>()
    }

    /**
     * Create the flow under test and inject mock services into it
     */
    val flow = flowMockHelper.createFlow<ChatOutgoingFlow>()

    val RECIPIENT_X500_NAME = "CN=Bob, O=R3, L=London, C=GB"
    val MESSAGE = "chat message"

    @Test
    fun `unspecified message in parameters throws`() {
        assertThrows<IllegalArgumentException> {
            flow.call(
                flowMockHelper.rpcRequestGenerator(
                    OutgoingChatMessage(recipientX500Name = RECIPIENT_X500_NAME)
                )
            )
        }
    }

    @Test
    fun `unspecified X500 name in parameters throws`() {
        assertThrows<IllegalArgumentException> {
            flow.call(
                flowMockHelper.rpcRequestGenerator(
                    OutgoingChatMessage(message = MESSAGE)
                )
            )
        }
    }

    @Test
    fun `flow sends message to correct recipient`() {
        val flowSession = flowMockHelper.expectFlowMessagesTo(MemberX500Name.parse(RECIPIENT_X500_NAME))

        flow.call(
            flowMockHelper.rpcRequestGenerator(
                OutgoingChatMessage(recipientX500Name = RECIPIENT_X500_NAME, message = MESSAGE)
            )
        )

        flowSession.verifyMessageSent(MessageContainer(MESSAGE))
    }
}
