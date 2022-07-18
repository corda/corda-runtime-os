package net.cordapp.testing.chat

import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import net.cordapp.testing.chatframework.FlowMockHelper
import net.cordapp.testing.chatframework.createFlow
import net.cordapp.testing.chatframework.createMockService
import net.cordapp.testing.chatframework.rpcRequestGenerator
import net.cordapp.testing.chatframework.verifyMessageSent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChatOutgoingFlowTest {
    val flowMockHelper = FlowMockHelper {
        createMockService<FlowMessaging>()
        createMockService<FlowEngine>()
        createMockService<JsonMarshallingService>()
        createMockService<PersistenceService>()
    }

    val flow = flowMockHelper.createFlow<ChatOutgoingFlow>()

    companion object {
        val RECIPIENT_X500_NAME = "CN=Bob, O=R3, L=London, C=GB"
        val MESSAGE = "chat message"
    }

    @Test
    fun `unspecified message in parameters throws`() {
        assertThrows<IllegalArgumentException> {
            flow.call(
                flowMockHelper.rpcRequestGenerator(
                    ChatOutgoingFlowParameter(recipientX500Name = RECIPIENT_X500_NAME)
                )
            )
        }
    }

    @Test
    fun `unspecified X500 name in parameters throws`() {
        assertThrows<IllegalArgumentException> {
            flow.call(
                flowMockHelper.rpcRequestGenerator(
                    ChatOutgoingFlowParameter(message = MESSAGE)
                )
            )
        }
    }

    @Test
    fun `flow sends message to correct recipient`() {
        val flowSession = flowMockHelper.expectFlowMessagesTo(MemberX500Name.parse(RECIPIENT_X500_NAME))

        flow.call(
            flowMockHelper.rpcRequestGenerator(
                ChatOutgoingFlowParameter(recipientX500Name = RECIPIENT_X500_NAME, message = MESSAGE)
            )
        )

        flowSession.verifyMessageSent(MessageContainer(MESSAGE))
    }
}
