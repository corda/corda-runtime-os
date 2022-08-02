package net.corda.p2p.linkmanager.messaging

import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationResult
import net.corda.p2p.crypto.protocol.api.DecryptionFailedError
import net.corda.p2p.crypto.protocol.api.InvalidMac
import net.corda.p2p.linkmanager.messaging.AvroSealedClasses.SessionAndMessage
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.linkmanager.utilities.mockGroups
import net.corda.p2p.linkmanager.utilities.mockMembers
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import java.nio.ByteBuffer

class MessageConverterTest {

    companion object {

        lateinit var loggingInterceptor: LoggingInterceptor

        @BeforeAll
        @JvmStatic
        fun setup() {
            loggingInterceptor = LoggingInterceptor.setupLogging()
        }
    }

    private val mockHeader = mock<CommonHeader> {
        on { sequenceNo } doReturn 1
        on { toByteBuffer() } doReturn ByteBuffer.wrap("HEADER".toByteArray())
    }

    @AfterEach
    fun resetLogging() {
        loggingInterceptor.reset()
    }

    @Test
    fun `convertAuthenticatedEncryptedMessageToFlowMessage returns null (with appropriate logging) if authentication fails`() {
        val session = mock<AuthenticatedEncryptionSession> {
            on { decryptData(any(), any(), any()) } doThrow DecryptionFailedError(
                "Decryption failed due to bad authentication tag.",
                Exception("")
            )
        }
        val mockMessage = mock<AuthenticatedEncryptedDataMessage> {
            on { authTag } doReturn ByteBuffer.wrap("AuthTag".toByteArray())
            on { encryptedPayload } doReturn ByteBuffer.wrap("PAYLOAD".toByteArray())
            on { header } doReturn mockHeader
        }
        assertThat(
            MessageConverter.extractPayloadFromAuthenticatedEncryptedMessage(
                SessionAndMessage.AuthenticatedEncrypted(session, mockMessage),
                AuthenticatedMessageAndKey::fromByteBuffer
            )
        ).isNull()
        loggingInterceptor.assertSingleWarning(
            "Decryption failed for message for session null. Reason: Decryption failed due " +
                "to bad authentication tag. The message was discarded."
        )
    }

    @Test
    fun `convertAuthenticatedMessageToFlowMessage returns null (with appropriate logging) if authentication fails`() {
        val session = mock<AuthenticatedSession> {
            on { validateMac(any(), any(), any()) } doThrow InvalidMac()
        }
        val mockMessage = mock<AuthenticatedDataMessage> {
            on { authTag } doReturn ByteBuffer.wrap("AuthTag".toByteArray())
            on { payload } doReturn ByteBuffer.wrap("PAYLOAD".toByteArray())
            on { header } doReturn mockHeader
        }

        assertThat(
            MessageConverter.extractPayloadFromAuthenticatedMessage(
                SessionAndMessage.Authenticated(session, mockMessage),
                AuthenticatedMessageAndKey::fromByteBuffer
            )
        ).isNull()
        loggingInterceptor.assertSingleWarning("MAC check failed for message for session null. The message was discarded.")
    }

    @Test
    fun `createLinkOutMessageFromFlowMessage returns null (with appropriate logging) if the destination is not in the network map`() {
        val mac = mock<AuthenticationResult> {
            on { header } doReturn mockHeader
            on { mac } doReturn byteArrayOf()
        }
        val session = mock<AuthenticatedSession> {
            on { createMac(any()) } doReturn mac
        }
        val groupId = "group-1"
        val peer = createTestHoldingIdentity("CN=Impostor, O=Evil Corp, L=LDN, C=GB", groupId)
        val members = mockMembers(emptyList())
        val flowMessage = authenticatedMessageAndKey(createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", groupId), peer, ByteBuffer.wrap("DATA".toByteArray()))
        assertThat(MessageConverter.linkOutMessageFromAuthenticatedMessageAndKey(flowMessage, session, mock(), members)).isNull()
        loggingInterceptor.assertSingleWarning(
            "Attempted to send message to peer $peer which is not in the network map." +
                " The message was discarded."
        )
    }

    @Test
    fun `createLinkOutMessageFromFlowMessage returns null (with appropriate logging) if our network type is not in the network map`() {
        val mac = mock<AuthenticationResult> {
            on { header } doReturn mockHeader
            on { mac } doReturn byteArrayOf()
        }
        val session = mock<AuthenticatedSession> {
            on { createMac(any()) } doReturn mac
        }
        val groupId = "group-1"
        val peer = createTestHoldingIdentity("CN=Impostor, O=Evil Corp, L=LDN, C=GB", groupId)
        val us = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", groupId)
        val members = mockMembers(listOf(us, peer))
        val groups = mockGroups(emptyList())
        val flowMessage = authenticatedMessageAndKey(us, peer, ByteBuffer.wrap("DATA".toByteArray()))
        assertThat(MessageConverter.linkOutMessageFromAuthenticatedMessageAndKey(flowMessage, session, groups, members)).isNull()
        loggingInterceptor.assertSingleWarning(
            "Could not find the group info in the GroupPolicyProvider for our identity = $us." +
                " The message was discarded."
        )
    }

    @Test
    fun `linkOutFromUnauthenticatedMessage returns null (with appropriate logging) if if the destination is not in the network map`() {
        val payload = "test"
        val groupId = "group-1"
        val us = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", groupId)
        val peer = createTestHoldingIdentity("CN=Impostor, O=Evil Corp, L=LDN, C=GB", groupId)
        val unauthenticatedMsg = UnauthenticatedMessage(
            UnauthenticatedMessageHeader(peer.toAvro(), us.toAvro(), "subsystem"),
            ByteBuffer.wrap(payload.toByteArray())
        )

        val members = mockMembers(emptyList())

        assertThat(MessageConverter.linkOutFromUnauthenticatedMessage(unauthenticatedMsg, mock(), members)).isNull()
        loggingInterceptor.assertSingleWarning(
            "Attempted to send message to peer $peer which is not in the network map." +
                " The message was discarded."
        )
    }

    @Test
    fun `linkOutFromUnauthenticatedMessage returns null (with appropriate logging) if if their network type is not in the network map`() {
        val payload = "test"
        val groupId = "group-1"
        val us = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", groupId)
        val peer = createTestHoldingIdentity("CN=Impostor, O=Evil Corp, L=LDN, C=GB", groupId)
        val unauthenticatedMsg = UnauthenticatedMessage(
            UnauthenticatedMessageHeader(peer.toAvro(), us.toAvro(), "subsystem"),
            ByteBuffer.wrap(payload.toByteArray())
        )

        val members = mockMembers(listOf(us, peer))
        val groups = mockGroups(emptyList())
        assertThat(MessageConverter.linkOutFromUnauthenticatedMessage(unauthenticatedMsg, groups, members)).isNull()
        loggingInterceptor.assertSingleWarning(
            "Could not find the group information in the GroupPolicyProvider for $us." +
                " The message was discarded."
        )
    }

    private fun authenticatedMessageAndKey(
        source: HoldingIdentity,
        dest: HoldingIdentity,
        data: ByteBuffer,
        messageId: String = ""
    ): AuthenticatedMessageAndKey {
        val header = AuthenticatedMessageHeader(dest.toAvro(), source.toAvro(), null, messageId, "", "system-1")
        return AuthenticatedMessageAndKey(AuthenticatedMessage(header, data), "key")
    }
}
