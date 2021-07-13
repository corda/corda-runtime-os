package net.corda.p2p.linkmanager.messaging

import com.nhaarman.mockito_kotlin.any
import net.corda.p2p.HoldingIdentity
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.LinkManagerTest
import net.corda.p2p.linkmanager.LinkManagerTest.Companion.complexMockFlowMessage
import net.corda.p2p.linkmanager.LinkManagerTest.Companion.createSessionPair
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.nio.ByteBuffer
import java.security.KeyPairGenerator

class MessageConverterTest {

    companion object {

        lateinit var loggingInterceptor: LoggingInterceptor
        private val mockHeader = Mockito.mock(CommonHeader::class.java)
        private val keyPairGenerator = KeyPairGenerator.getInstance("EC", LinkManagerTest.provider)

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

        assertNull(MessageConverter.convertAuthenticatedEncryptedMessageToFlowMessage(mockMessage, session as AuthenticatedEncryptionSession))
        loggingInterceptor.assertSingleWarning("Decryption failed for message for session null. Reason: Decryption failed due to bad authentication tag. The message was discarded.")
    }

    @Test
    fun `convertAuthenticatedMessageToFlowMessage returns null (with appropriate logging) if authentication fails`() {
        val session = createSessionPair().responderSession
        val mockMessage = Mockito.mock(AuthenticatedDataMessage::class.java)
        Mockito.`when`(mockMessage.authTag).thenReturn(ByteBuffer.wrap("AuthTag".toByteArray()))
        Mockito.`when`(mockMessage.payload).thenReturn(ByteBuffer.wrap("PAYLOAD".toByteArray()))
        Mockito.`when`(mockMessage.header).thenReturn(mockHeader)

        assertNull(MessageConverter.convertAuthenticatedMessageToFlowMessage(mockMessage, session as AuthenticatedSession))
        loggingInterceptor.assertSingleWarning("MAC check failed for message for session null. The message was discarded.")
    }

    @Test
    fun `createLinkOutMessageFromFlowMessage returns null (with appropriate logging) if the destination is not in the network map`() {
        val session = createSessionPair().responderSession
        val peer = HoldingIdentity("Imposter", "")
        val networkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(networkMap.getMemberInfo(any())).thenReturn(null)
        val flowMessage = complexMockFlowMessage(HoldingIdentity("", ""), peer, ByteBuffer.wrap("DATA".toByteArray()))
        assertNull(MessageConverter.createLinkOutMessageFromFlowMessage(flowMessage, session, networkMap))
        loggingInterceptor.assertSingleWarning("Attempted to send message to peer $peer which is not in the network map." +
                " The message was discarded.")
    }

    @Test
    fun `createLinkOutMessageFromFlowMessage returns null (with appropriate logging) if our network type is not in the network map`() {
        val session = createSessionPair().responderSession
        val peer = HoldingIdentity("Imposter", "")
        val us = HoldingIdentity("", "")
        val networkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(networkMap.getMemberInfo(any())).thenReturn(
            LinkManagerNetworkMap.MemberInfo(
                us.toHoldingIdentity(),
                keyPairGenerator.genKeyPair().public,
                LinkManagerNetworkMap.EndPoint("")
            )
        )
        val flowMessage = complexMockFlowMessage(us, peer, ByteBuffer.wrap("DATA".toByteArray()))
        assertNull(MessageConverter.createLinkOutMessageFromFlowMessage(flowMessage, session, networkMap))
        loggingInterceptor.assertSingleWarning("Could not find the network type in the NetworkMap for our identity = $us." +
            " The message was discarded.")
    }
}