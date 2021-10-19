package net.corda.p2p.gateway

import net.corda.p2p.gateway.messaging.internal.InboundMessageHandler
import net.corda.p2p.gateway.messaging.internal.OutboundMessageHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class GatewayImplTest {
    @Test
    fun `children contains inbound message handler and outbound message processor`() {
        val gateway = GatewayImpl(
            mock(),
            mock {
                on { createEventLogSubscription(any(), any<OutboundMessageHandler>(), any(), anyOrNull()) } doReturn mock()
            },
            mock {
                on { createPublisher(any(), any()) } doReturn mock()
            },
            mock(),
            mock(),
        )

        val children = gateway.children

        assertThat(children)
            .hasSize(2)
            .hasAtLeastOneElementOfType(InboundMessageHandler::class.java)
            .hasAtLeastOneElementOfType(OutboundMessageHandler::class.java)
    }
}
