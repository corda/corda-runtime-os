package net.corda.p2p.linkmanager.messaging

import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.linkmanager.LinkManagerTest.Companion.createSessionPair
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.Logger
import java.nio.ByteBuffer

class MessageCoverterTest {

    private val mockHeader = Mockito.mock(CommonHeader::class.java)

    @BeforeEach
    fun setup() {
        Mockito.`when`(mockHeader.sequenceNo).thenReturn(1)
        Mockito.`when`(mockHeader.toByteBuffer()).thenReturn(ByteBuffer.wrap("HEADER".toByteArray()))
    }

    @Test
    fun `convertAuthenticatedEncryptedMessageToFlowMessage returns null (with appropriate logging) if authentication fails`() {
        val session = createSessionPair(ProtocolMode.AUTHENTICATED_ENCRYPTION).responderSession
        val mockMessage = Mockito.mock(AuthenticatedEncryptedDataMessage::class.java)
        Mockito.`when`(mockMessage.authTag).thenReturn(ByteBuffer.wrap("AuthTag".toByteArray()))
        Mockito.`when`(mockMessage.encryptedPayload).thenReturn(ByteBuffer.wrap("PAYLOAD".toByteArray()))
        Mockito.`when`(mockMessage.header).thenReturn(mockHeader)

        val mockLogger = Mockito.mock(Logger::class.java)
        MessageConverter.setLogger(mockLogger)
        assertNull(MessageConverter.convertAuthenticatedEncryptedMessageToFlowMessage(mockMessage, session as AuthenticatedEncryptionSession))
        Mockito.verify(mockLogger).warn("Decryption failed for message for session null. Reason: Decryption failed due to bad authentication tag. The message was discarded.")
    }

    @Test
    fun `convertAuthenticatedMessageToFlowMessage returns null (with appropriate logging) if authentication fails`() {
        val session = createSessionPair().responderSession
        val mockMessage = Mockito.mock(AuthenticatedDataMessage::class.java)
        Mockito.`when`(mockMessage.authTag).thenReturn(ByteBuffer.wrap("AuthTag".toByteArray()))
        Mockito.`when`(mockMessage.payload).thenReturn(ByteBuffer.wrap("PAYLOAD".toByteArray()))
        Mockito.`when`(mockMessage.header).thenReturn(mockHeader)

        val mockLogger = Mockito.mock(Logger::class.java)
        MessageConverter.setLogger(mockLogger)
        assertNull(MessageConverter.convertAuthenticatedMessageToFlowMessage(mockMessage, session as AuthenticatedSession))
        Mockito.verify(mockLogger).warn("MAC check failed for message for session null. The message was discarded.")
    }
}