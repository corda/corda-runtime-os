package net.cordapp.testing.chat

import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.cordapp.testing.chatframework.FlowMockHelper
import net.cordapp.testing.chatframework.createFlow
import net.cordapp.testing.chatframework.mockService
import net.cordapp.testing.chatframework.willReceive
import net.cordapp.testing.chatframework.withCounterpartyName
import net.cordapp.testing.chatframework.withVirtualNodeName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ChatIncomingFlowTest {
    companion object {
        val X500_NAME = "CN=Alice, O=R3, L=London, C=GB"
        val COUNTERPARTY_X500_NAME = "CN=Bob, O=R3, L=London, C=GB"
        val MESSAGE = "chat message"
    }

    val flowMockHelper = FlowMockHelper {
        mockService<FlowEngine>().withVirtualNodeName(X500_NAME)
    }

    val flow = flowMockHelper.createFlow<ChatIncomingFlow>()

    @Test
    fun `flow receives message and stores it`() {
        val flowSession =
            mock<FlowSession>().withCounterpartyName(COUNTERPARTY_X500_NAME).willReceive(MessageContainer(MESSAGE))

        flow.call(flowSession)

        val receivedMessages = MessageStore.readAndClear()
        assertThat(receivedMessages.messages.size).isEqualTo(1)
        assertThat(receivedMessages.messages[0]).isEqualTo(IncomingChatMessage(COUNTERPARTY_X500_NAME, MESSAGE))
    }
}
