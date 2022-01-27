package net.corda.p2p.gateway

import net.corda.p2p.gateway.messaging.internal.InboundMessageHandler
import net.corda.p2p.gateway.messaging.internal.OutboundMessageHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class GatewayTest {
    @Test
    fun `children contains inbound message handler and outbound message processor`() {
        val gateway = Gateway(
            mock(),
            mock {
                on { createEventLogSubscription(any(), any<OutboundMessageHandler>(), any(), anyOrNull()) } doReturn mock()
            },
            mock {
                on { createPublisher(any(), any()) } doReturn mock()
            },
            mock {
                on { createCoordinator(any(), any()) } doReturn mock()
            },
            mock(),
            1,
        )

        val children = gateway.children

        assertThat(children.map { it.name.componentName })
            .containsExactlyInAnyOrder(InboundMessageHandler::class.java.simpleName, OutboundMessageHandler::class.java.simpleName)
    }
}
