package net.corda.p2p.linkmanager.messaging

import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.data.identity.HoldingIdentity
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.linkmanager.LinkManagerInternalTypes.toHoldingIdentity
import net.corda.p2p.linkmanager.LinkManagerTest.Companion.authenticatedMessageAndKey
import net.corda.p2p.linkmanager.LinkManagerTest.Companion.createSessionPair
import net.corda.p2p.linkmanager.messaging.AvroSealedClasses.SessionAndMessage
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.linkmanager.utilities.mockGroups
import net.corda.p2p.linkmanager.utilities.mockMembers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import java.nio.ByteBuffer

class MessageConverterTest {

    companion object {

        lateinit var loggingInterceptor: LoggingInterceptor
        private val mockHeader = Mockito.mock(CommonHeader::class.java)

        @BeforeAll
        @JvmStatic
        fun setup() {
            loggingInterceptor = LoggingInterceptor.setupLogging()
            Mockito.`when`(mockHeader.sequenceNo).thenReturn(1)
            Mockito.`when`(mockHeader.toByteBuffer()).thenReturn(ByteBuffer.wrap("HEADER".toByteArray()))
        }
    }

    @AfterEach
    fun resetLogging() {
        loggingInterceptor.reset()
    }

    @Test
    fun `convertAuthenticatedEncryptedMessageToFlowMessage returns null (with appropriate logging) if authentication fails`() {
        val session = createSessionPair(ProtocolMode.AUTHENTICATED_ENCRYPTION).responderSession
        val mockMessage = Mockito.mock(AuthenticatedEncryptedDataMessage::class.java)
        Mockito.`when`(mockMessage.authTag).thenReturn(ByteBuffer.wrap("AuthTag".toByteArray()))
        Mockito.`when`(mockMessage.encryptedPayload).thenReturn(ByteBuffer.wrap("PAYLOAD".toByteArray()))
        Mockito.`when`(mockMessage.header).thenReturn(mockHeader)

        assertNull(
            MessageConverter.extractPayloadFromAuthenticatedEncryptedMessage(
                SessionAndMessage.AuthenticatedEncrypted(session as AuthenticatedEncryptionSession, mockMessage),
                AuthenticatedMessageAndKey::fromByteBuffer
            )
        )
        loggingInterceptor.assertSingleWarning(
            "Decryption failed for message for session null. Reason: Decryption failed due " +
                "to bad authentication tag. The message was discarded."
        )
    }

    @Test
    fun `convertAuthenticatedMessageToFlowMessage returns null (with appropriate logging) if authentication fails`() {
        val session = createSessionPair().responderSession
        val mockMessage = Mockito.mock(AuthenticatedDataMessage::class.java)
        Mockito.`when`(mockMessage.authTag).thenReturn(ByteBuffer.wrap("AuthTag".toByteArray()))
        Mockito.`when`(mockMessage.payload).thenReturn(ByteBuffer.wrap("PAYLOAD".toByteArray()))
        Mockito.`when`(mockMessage.header).thenReturn(mockHeader)

        assertNull(
            MessageConverter.extractPayloadFromAuthenticatedMessage(
                SessionAndMessage.Authenticated(session as AuthenticatedSession, mockMessage),
                AuthenticatedMessageAndKey::fromByteBuffer
            )
        )
        loggingInterceptor.assertSingleWarning("MAC check failed for message for session null. The message was discarded.")
    }

    @Test
    fun `createLinkOutMessageFromFlowMessage returns null (with appropriate logging) if the destination is not in the network map`() {
        val session = createSessionPair().responderSession
        val peer = HoldingIdentity("Imposter", "")
        val members = mockMembers(emptyList())
        val flowMessage = authenticatedMessageAndKey(HoldingIdentity("", ""), peer, ByteBuffer.wrap("DATA".toByteArray()))
        assertNull(MessageConverter.linkOutMessageFromAuthenticatedMessageAndKey(flowMessage, session, mock(), members))
        loggingInterceptor.assertSingleWarning(
            "Attempted to send message to peer $peer which is not in the network map." +
                " The message was discarded."
        )
    }

    @Test
    fun `createLinkOutMessageFromFlowMessage returns null (with appropriate logging) if our network type is not in the network map`() {
        val session = createSessionPair().responderSession
        val peer = HoldingIdentity("Imposter", "")
        val us = HoldingIdentity("", "")
        val members = mockMembers(listOf(us.toHoldingIdentity(), peer.toHoldingIdentity()))
        val groups = mockGroups(emptyList())
        val flowMessage = authenticatedMessageAndKey(us, peer, ByteBuffer.wrap("DATA".toByteArray()))
        assertNull(MessageConverter.linkOutMessageFromAuthenticatedMessageAndKey(flowMessage, session, groups, members))
        loggingInterceptor.assertSingleWarning(
            "Could not find the group info in the GroupPolicyProvider for our identity = $us." +
                " The message was discarded."
        )
    }

    @Test
    fun `linkOutFromUnauthenticatedMessage returns null (with appropriate logging) if if the destination is not in the network map`() {
        val payload = "test"
        val us = HoldingIdentity("Alice", "test-group-id")
        val peer = HoldingIdentity("Imposter", "")
        val unauthenticatedMsg = UnauthenticatedMessage(
            UnauthenticatedMessageHeader(peer, us, "subsystem"),
            ByteBuffer.wrap(payload.toByteArray())
        )

        val members = mockMembers(emptyList())

        assertNull(MessageConverter.linkOutFromUnauthenticatedMessage(unauthenticatedMsg, mock(), members))
        loggingInterceptor.assertSingleWarning(
            "Attempted to send message to peer $peer which is not in the network map." +
                " The message was discarded."
        )
    }

    @Test
    fun `linkOutFromUnauthenticatedMessage returns null (with appropriate logging) if if their network type is not in the network map`() {
        val payload = "test"
        val us = HoldingIdentity("Alice", "test-group-id")
        val peer = HoldingIdentity("Imposter", "")
        val unauthenticatedMsg = UnauthenticatedMessage(
            UnauthenticatedMessageHeader(peer, us, "subsystem"),
            ByteBuffer.wrap(payload.toByteArray())
        )

        val members = mockMembers(listOf(us.toHoldingIdentity(), peer.toHoldingIdentity()))
        val groups = mockGroups(emptyList())
        assertNull(MessageConverter.linkOutFromUnauthenticatedMessage(unauthenticatedMsg, groups, members))
        loggingInterceptor.assertSingleWarning(
            "Could not find the group information in the GroupPolicyProvider for $peer." +
                " The message was discarded."
        )
    }
}
