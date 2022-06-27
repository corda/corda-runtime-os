package net.cordapp.testing.chat

import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ChatIncomingFlowTest {
    val X500_NAME = "CN=Alice, O=R3, L=London, C=GB"
    val COUNTERPARTY_X500_NAME = "CN=Bob, O=R3, L=London, C=GB"
    val MESSAGE = "chat message"

    /**
     * Create an injector which can inject a Flow with mock versions of necessary services
     */
    val injector = FlowTestDependencyInjector {
        mockService<FlowEngine>().withVirtualNodeName(X500_NAME)
    }

    /**
     * Create the flow under test and inject mock services into it
     */
    val flow = ChatIncomingFlow().also {
        injector.injectServices(it)
    }

    @Test
    fun `flow receives message and stores it`() {
        val flowSession = mock<FlowSession>()
                .withCounterpartyName(COUNTERPARTY_X500_NAME)
                .willReceive(MessageContainer(MESSAGE))

        flow.call(flowSession)

        val receivedMessages = MessageStore.readAndClear()
        assertThat(receivedMessages.messages.size).isEqualTo(1)
        assertThat(receivedMessages.messages[0]).isEqualTo(IncomingChatMessage(COUNTERPARTY_X500_NAME, MESSAGE))
    }
}
