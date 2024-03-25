package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.InboundUnauthenticatedMessage
import net.corda.data.p2p.app.InboundUnauthenticatedMessageHeader
import net.corda.data.p2p.app.OutboundUnauthenticatedMessage
import net.corda.data.p2p.app.OutboundUnauthenticatedMessageHeader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AppMessageExt {
    private companion object {
        const val ID = "ID"
    }
    @Test
    fun `id return the correct ID for AuthenticatedMessage`() {
        val messageHeader = mock<AuthenticatedMessageHeader> {
            on { messageId } doReturn ID
        }
        val authenticatedMessage = mock<AuthenticatedMessage> {
            on { header } doReturn messageHeader
        }
        val appMessage = mock<AppMessage> {
            on { message } doReturn authenticatedMessage
        }

        assertThat(appMessage.id).isEqualTo(ID)
    }

    @Test
    fun `id return the correct ID for OutboundUnauthenticatedMessage`() {
        val messageHeader = mock<OutboundUnauthenticatedMessageHeader> {
            on { messageId } doReturn ID
        }
        val outboundUnauthenticatedMessage = mock<OutboundUnauthenticatedMessage> {
            on { header } doReturn messageHeader
        }
        val appMessage = mock<AppMessage> {
            on { message } doReturn outboundUnauthenticatedMessage
        }

        assertThat(appMessage.id).isEqualTo(ID)
    }

    @Test
    fun `id return the correct ID for InboundUnauthenticatedMessage`() {
        val messageHeader = mock<InboundUnauthenticatedMessageHeader> {
            on { messageId } doReturn ID
        }
        val inboundUnauthenticatedMessageHeader = mock<InboundUnauthenticatedMessage> {
            on { header } doReturn messageHeader
        }
        val appMessage = mock<AppMessage> {
            on { message } doReturn inboundUnauthenticatedMessageHeader
        }

        assertThat(appMessage.id).isEqualTo(ID)
    }

    @Test
    fun `id return the null for unknown message`() {
        val appMessage = AppMessage()

        assertThat(appMessage.id).isNull()
    }
}
