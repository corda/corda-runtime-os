package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.FlowMessage
import net.corda.p2p.FlowMessageHeader
import net.corda.p2p.LinkInMessage
import net.corda.p2p.Step2Message
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManager.Companion.getSessionKeyFromMessage
import net.corda.p2p.linkmanager.LinkManagerCryptoService
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.messaging.Messaging.Companion.convertAuthenticatedEncryptedMessageToFlowMessage
import net.corda.p2p.linkmanager.messaging.Messaging.Companion.convertAuthenticatedMessageToFlowMessage
import net.corda.p2p.linkmanager.messaging.Messaging.Companion.createLinkOutMessageFromFlowMessage
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

class SessionManagerTest {

    companion object {
        private val GROUP_ID = null
        val PARTY_A = LinkManagerNetworkMap.HoldingIdentity("PartyA", GROUP_ID)
        val PARTY_B = LinkManagerNetworkMap.HoldingIdentity("PartyB", GROUP_ID)
        val FAKE_ENDPOINT = LinkManagerNetworkMap.EndPoint("10.0.0.1:hello")
        const val MAX_MESSAGE_SIZE = 1024 * 1024
    }

    class MockNetworkMap(nodes: List<LinkManagerNetworkMap.HoldingIdentity>) {
        private val provider = BouncyCastleProvider()
        private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
        private val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, provider)

        private val keys = HashMap<LinkManagerNetworkMap.HoldingIdentity, KeyPair>()
        private val peerForHash = HashMap<Int, LinkManagerNetworkMap.HoldingIdentity>()

        private fun MessageDigest.hash(data: ByteArray): ByteArray {
            this.reset()
            this.update(data)
            return digest()
        }

        init {
            for (node in nodes) {
                val keyPair = keyPairGenerator.generateKeyPair()
                keys[node] = keyPair
                peerForHash[messageDigest.hash(keyPair.public.encoded).contentHashCode()] = node
            }
        }

        fun getSessionNetworkMapForNode(node: LinkManagerNetworkMap.HoldingIdentity): LinkManagerNetworkMap {
            return object : LinkManagerNetworkMap {
                override fun getPublicKey(holdingIdentity: LinkManagerNetworkMap.HoldingIdentity): PublicKey? {
                    return keys[holdingIdentity]?.public
                }

                override fun getPublicKeyFromHash(hash: ByteArray): PublicKey? {
                    val peer = getPeerFromHash(hash)
                    return keys[peer]?.public
                }

                override fun getPeerFromHash(hash: ByteArray): LinkManagerNetworkMap.HoldingIdentity? {
                    return peerForHash[hash.contentHashCode()]
                }

                override fun getEndPoint(holdingIdentity: LinkManagerNetworkMap.HoldingIdentity): LinkManagerNetworkMap.EndPoint? {
                    //The actual end point does not need to be meaningful in this test as it is only used by the Gateway.
                    return if (keys[holdingIdentity] != null) {
                        FAKE_ENDPOINT
                    } else {
                        null
                    }
                }

                override fun getOurPublicKey(groupId: String?): PublicKey? {
                    assertNull(groupId) {"In this case the groupId should be null."}
                    return keys[node]!!.public
                }

                override fun getOurPrivateKey(groupId: String?): PrivateKey? {
                    assertNull(groupId) {"In this case the groupId should be null."}
                    return keys[node]!!.private
                }

                override fun getOurHoldingIdentity(groupId: String?): LinkManagerNetworkMap.HoldingIdentity {
                    assertNull(groupId) {"In this case the groupId should be null."}
                    return node
                }
            }
        }

    }

    class MockCryptoService : LinkManagerCryptoService {
        private val provider = BouncyCastleProvider()
        private val signature = Signature.getInstance("ECDSA", provider)

        override fun signData(key: PrivateKey, data: ByteArray): ByteArray {
            signature.initSign(key)
            signature.update(data)
            return signature.sign()
        }
    }

    fun mockGatewayResponse(message: InitiatorHelloMessage, supportedModes: Set<ProtocolMode>): Step2Message {
        val authenticationProtocol = AuthenticationProtocolResponder(
            message.header.sessionId,
            supportedModes,
            MAX_MESSAGE_SIZE
        )
        authenticationProtocol.receiveInitiatorHello(message)
        val responderHello = authenticationProtocol.generateResponderHello()
        val (privateKey, _) = authenticationProtocol.getDHKeyPair()
        return Step2Message(message, responderHello, ByteBuffer.wrap(privateKey))
    }

    private fun negotiateSession(key: SessionManager.SessionKey,
                                 initiatorSessionManager: SessionManager,
                                 responderSessionManager: SessionManager,
                                 responderSupportedMode: Set<ProtocolMode>): Pair<Session, Session> {
        val initiatorHelloMessage = initiatorSessionManager.getSessionInitMessage(key)
        assertTrue(initiatorHelloMessage?.payload is InitiatorHelloMessage)

        //Strip the Header from the message (as the Gateway does before sending it).
        val step2Message = mockGatewayResponse(initiatorHelloMessage?.payload as InitiatorHelloMessage, responderSupportedMode)
        assertNull(responderSessionManager.processSessionMessage(LinkInMessage(step2Message)))
        val initiatorHandshakeMessage = initiatorSessionManager.processSessionMessage(LinkInMessage(step2Message.responderHello))

        assertTrue(initiatorHandshakeMessage?.payload is InitiatorHandshakeMessage)
        val sessionId = (initiatorHandshakeMessage?.payload as InitiatorHandshakeMessage).header.sessionId

        val responderHandshakeMessage = responderSessionManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage.payload))
        assertTrue(responderHandshakeMessage?.payload is ResponderHandshakeMessage)
        assertNull(initiatorSessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage?.payload)))

        val initiatorSession = initiatorSessionManager.getInitiatorSession(key)
        assertNotNull(initiatorSession, "Authenticated Session is not stored in the initiator's session manager.")

        val responderSession = responderSessionManager.getResponderSession(sessionId)
        assertNotNull(responderSession, "Authenticated Session is not stored in the responder's session manager.")

        return Pair(initiatorSession!!, responderSession!!)
    }

    @Test
    fun `A session can be negotiated between two SessionManagers and a message can be sent (in AUTHENTICATION_ONLY mode)`() {
        val netMap = MockNetworkMap(listOf(PARTY_A, PARTY_B))
        val supportedMode =  setOf(ProtocolMode.AUTHENTICATION_ONLY)
        val initiatorSessionManager = SessionManager(
            supportedMode,
            netMap.getSessionNetworkMapForNode(PARTY_A),
            MockCryptoService(),
            MAX_MESSAGE_SIZE
        ) { _, _, _ -> return@SessionManager }
        val responderSessionManager = SessionManager(
            supportedMode,
            netMap.getSessionNetworkMapForNode(PARTY_B),
            MockCryptoService(),
            MAX_MESSAGE_SIZE
        ) { _, _, _ -> return@SessionManager }

        val payload = ByteBuffer.wrap("Hello from PartyA".toByteArray())
        val message = FlowMessage(FlowMessageHeader(
            PARTY_B.toHoldingIdentity(),
            PARTY_A.toHoldingIdentity(),
            null,
            "messageId",
            ""), payload)

        val (initiatorSession, responderSession) = negotiateSession(
            getSessionKeyFromMessage(message),
            initiatorSessionManager,
            responderSessionManager,
            supportedMode
        )

        assertTrue(initiatorSession is AuthenticatedSession)
        assertTrue(responderSession is AuthenticatedSession)

        val authenticatedMessage = createLinkOutMessageFromFlowMessage(message, initiatorSession as AuthenticatedSession, netMap.getSessionNetworkMapForNode(PARTY_A))
        assertTrue(authenticatedMessage?.payload is AuthenticatedDataMessage)
        val authenticatedDataMessage = (authenticatedMessage?.payload as AuthenticatedDataMessage)

        val responderMessage = convertAuthenticatedMessageToFlowMessage(authenticatedDataMessage, responderSession as AuthenticatedSession)
        assertEquals(message.payload, responderMessage!!.payload)
    }

    @Test
    fun `A session can be negotiated between two SessionManagers and a message can be sent and decrypted (in AUTHENTICATED_ENCRYPTION mode)`() {
        val netMap = MockNetworkMap(listOf(PARTY_A, PARTY_B))
        val supportedMode =  setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val initiatorSessionManager = SessionManager(
            supportedMode,
            netMap.getSessionNetworkMapForNode(PARTY_A),
            MockCryptoService(),
            MAX_MESSAGE_SIZE
        ) { _, _, _ -> return@SessionManager }
        val responderSessionManager = SessionManager(
            supportedMode,
            netMap.getSessionNetworkMapForNode(PARTY_B),
            MockCryptoService(),
            MAX_MESSAGE_SIZE
        ) { _, _, _ -> return@SessionManager }

        val payload = ByteBuffer.wrap("Hello from PartyA".toByteArray())
        val message = FlowMessage(FlowMessageHeader(
            PARTY_B.toHoldingIdentity(),
            PARTY_A.toHoldingIdentity(),
            null,
            "messageId",
            ""), payload)

        val (initiatorSession, responderSession) = negotiateSession(
            getSessionKeyFromMessage(message),
            initiatorSessionManager,
            responderSessionManager,
            supportedMode
        )

        assertTrue(initiatorSession is AuthenticatedEncryptionSession)
        assertTrue(responderSession is AuthenticatedEncryptionSession)

        val authenticatedMessage = createLinkOutMessageFromFlowMessage(message, initiatorSession, netMap.getSessionNetworkMapForNode(PARTY_A))
        assertTrue(authenticatedMessage?.payload is AuthenticatedEncryptedDataMessage)
        val encryptedDataMessage = (authenticatedMessage?.payload as AuthenticatedEncryptedDataMessage)

        val responderMessage = convertAuthenticatedEncryptedMessageToFlowMessage(encryptedDataMessage, responderSession as AuthenticatedEncryptionSession)
        assertEquals(message.payload, responderMessage!!.payload)
    }

    @Test
    fun `The callback function is called with the correct arguments after a session is negotiation`() {
        val payload = ByteBuffer.wrap("Hello from PartyA".toByteArray())
        val message = FlowMessage(FlowMessageHeader(
            PARTY_B.toHoldingIdentity(),
            PARTY_A.toHoldingIdentity(),
            null,
            "messageId",
            ""), payload)

        val netMap = MockNetworkMap(listOf(PARTY_A, PARTY_B))
        val netMapPartyA = netMap.getSessionNetworkMapForNode(PARTY_A)

        var sessionFromCallback : Session? = null
        fun testCallBack(key: SessionManager.SessionKey, session: Session, map: LinkManagerNetworkMap) {
            assertSame(map, netMapPartyA)
            assertEquals(key, getSessionKeyFromMessage(message))
            sessionFromCallback = session
            return
        }
        val supportedMode =  setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val initiatorSessionManager = SessionManager(
            supportedMode,
            netMapPartyA,
            MockCryptoService(),
            MAX_MESSAGE_SIZE,
            ::testCallBack
        )
        val responderSessionManager = SessionManager(
            supportedMode,
            netMap.getSessionNetworkMapForNode(PARTY_B),
            MockCryptoService(),
            MAX_MESSAGE_SIZE
        ) { _, _, _ -> return@SessionManager }

        val (initiatorSession, _) = negotiateSession(
            getSessionKeyFromMessage(message),
            initiatorSessionManager,
            responderSessionManager,
            supportedMode
        )
        assertSame(sessionFromCallback, initiatorSession)
    }

    @Test
    fun `Session messages are dropped (with appropriate logging) if there is no pending session`() {
        val netMap = MockNetworkMap(listOf(PARTY_A, PARTY_B))
        val supportedMode =  setOf(ProtocolMode.AUTHENTICATION_ONLY)
        val sessionManager = SessionManager(
            supportedMode,
            netMap.getSessionNetworkMapForNode(PARTY_A),
            MockCryptoService(),
            MAX_MESSAGE_SIZE
        ) { _, _, _ -> return@SessionManager }

        val mockHeader = Mockito.mock(CommonHeader::class.java)

        val mockResponderHelloMessage = Mockito.mock(ResponderHelloMessage::class.java)
        val mockResponderHandshakeMessage = Mockito.mock(ResponderHandshakeMessage::class.java)
        val mockInitiatorHandshakeMessage = Mockito.mock(InitiatorHandshakeMessage::class.java)

        Mockito.`when`(mockResponderHelloMessage.header).thenReturn(mockHeader)
        Mockito.`when`(mockResponderHandshakeMessage.header).thenReturn(mockHeader)
        Mockito.`when`(mockInitiatorHandshakeMessage.header).thenReturn(mockHeader)

        val fakeSession = "Fake Session"
        Mockito.`when`(mockHeader.sessionId).thenReturn(fakeSession)

        val mockMessages = listOf(mockResponderHelloMessage,
            mockResponderHandshakeMessage,
            mockInitiatorHandshakeMessage)

        val mockLogger = Mockito.mock(Logger::class.java)
        sessionManager.setLogger(mockLogger)

        for (mockMessage in mockMessages) {
            assertNull(sessionManager.processSessionMessage(LinkInMessage(mockMessage)))
            Mockito.verify(mockLogger).warn("Received ${mockMessage::class.java.simpleName} with sessionId" +
                    " $fakeSession but there is no pending session with this id. The message was discarded.")

        }
    }

}