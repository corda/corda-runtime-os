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

        private fun sessionManager(
            netMap: MockNetworkMap.MockLinkManagerNetworkMap,
            mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY,
            sessionNegotiatedCallback: (SessionManagerImpl.SessionKey, Session, LinkManagerNetworkMap) -> Unit = { _, _, _ -> }
        ): SessionManagerImpl {
            return SessionManagerImpl(
                setOf(mode),
                netMap,
                MockCryptoService(netMap),
                MAX_MESSAGE_SIZE,
                sessionNegotiatedCallback
            )
        }
    }

    class MockNetworkMap(nodes: List<LinkManagerNetworkMap.HoldingIdentity>) {
        private val provider = BouncyCastleProvider()
        private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
        private val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, provider)

        val keys = HashMap<LinkManagerNetworkMap.HoldingIdentity, KeyPair>()
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

        interface MockLinkManagerNetworkMap : LinkManagerNetworkMap {
            fun getPrivateKeyFromHash(hash: ByteArray): PrivateKey
        }

        fun getSessionNetworkMapForNode(node: LinkManagerNetworkMap.HoldingIdentity): MockLinkManagerNetworkMap {
            return object : MockLinkManagerNetworkMap {
                override fun hashPublicKey(publicKey: PublicKey): ByteArray {
                    return messageDigest.hash(publicKey.encoded)
                }

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

                override fun getOurHoldingIdentity(groupId: String?): LinkManagerNetworkMap.HoldingIdentity {
                    assertNull(groupId) {"In this case the groupId should be null."}
                    return node
                }

                override fun getPrivateKeyFromHash(hash: ByteArray): PrivateKey {
                    return keys[node]!!.private
                }
            }
        }

    }

    class MockCryptoService(private val mockNetworkMap: MockNetworkMap.MockLinkManagerNetworkMap) : LinkManagerCryptoService {
        private val provider = BouncyCastleProvider()
        private val signature = Signature.getInstance("ECDSA", provider)

        override fun signData(hash: ByteArray, data: ByteArray): ByteArray {
            val key = mockNetworkMap.getPrivateKeyFromHash(hash)
            signature.initSign(key)
            signature.update(data)
            return signature.sign()
        }
    }

    private val netMap = MockNetworkMap(listOf(PARTY_A, PARTY_B))
    private val payload = ByteBuffer.wrap("Hello from PartyA".toByteArray())
    private val message = FlowMessage(FlowMessageHeader(
        PARTY_B.toHoldingIdentity(),
        PARTY_A.toHoldingIdentity(),
        null,
        "messageId",
        ""), payload)

    fun mockGatewayResponse(message: InitiatorHelloMessage, mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY): Step2Message {
        val authenticationProtocol = AuthenticationProtocolResponder(
            message.header.sessionId,
            setOf(mode),
            MAX_MESSAGE_SIZE
        )
        authenticationProtocol.receiveInitiatorHello(message)
        val responderHello = authenticationProtocol.generateResponderHello()
        val (privateKey, _) = authenticationProtocol.getDHKeyPair()
        return Step2Message(message, responderHello, ByteBuffer.wrap(privateKey))
    }

    private fun negotiateSession(key: SessionManagerImpl.SessionKey,
                                 initiatorSessionManager: SessionManagerImpl,
                                 responderSessionManager: SessionManagerImpl,
                                 responderSupportedMode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY): Pair<Session, Session> {
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

    private fun sessionManager(
        party: LinkManagerNetworkMap.HoldingIdentity,
        mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY,
    ) : SessionManagerImpl {
        return sessionManager(netMap.getSessionNetworkMapForNode(party), mode)
    }

    @Test
    fun `A session can be negotiated between two SessionManagers and a message can be sent (in AUTHENTICATION_ONLY mode)`() {
        val initiatorSessionManager = sessionManager(PARTY_A)
        val responderSessionManager = sessionManager(PARTY_B)

        val (initiatorSession, responderSession) = negotiateSession(getSessionKeyFromMessage(message), initiatorSessionManager, responderSessionManager)

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
        val mode =  ProtocolMode.AUTHENTICATED_ENCRYPTION
        val initiatorSessionManager = sessionManager(PARTY_A, mode)
        val responderSessionManager = sessionManager(PARTY_B, mode)

        val (initiatorSession, responderSession) = negotiateSession(
            getSessionKeyFromMessage(message),
            initiatorSessionManager,
            responderSessionManager,
            mode
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
        val networkMap = MockNetworkMap(listOf(PARTY_A, PARTY_B))
        val netMapPartyA = networkMap.getSessionNetworkMapForNode(PARTY_A)

        var sessionFromCallback : Session? = null
        fun testCallBack(key: SessionManagerImpl.SessionKey, session: Session, map: LinkManagerNetworkMap) {
            assertSame(map, netMapPartyA)
            assertEquals(key, getSessionKeyFromMessage(message))
            sessionFromCallback = session
            return
        }
        val mode =  ProtocolMode.AUTHENTICATED_ENCRYPTION
        val initiatorSessionManager = sessionManager(netMapPartyA, mode, ::testCallBack)
        val responderSessionManager = sessionManager(networkMap.getSessionNetworkMapForNode(PARTY_B), mode)

        val (initiatorSession, _) = negotiateSession(
            getSessionKeyFromMessage(message),
            initiatorSessionManager,
            responderSessionManager,
            mode
        )
        assertSame(sessionFromCallback, initiatorSession)
    }

    @Test
    fun `Session messages are dropped (with appropriate logging) if there is no pending session`() {
        val sessionManager = sessionManager(PARTY_A)

        val mockResponderHelloMessage = Mockito.mock(ResponderHelloMessage::class.java)
        val mockResponderHandshakeMessage = Mockito.mock(ResponderHandshakeMessage::class.java)
        val mockInitiatorHandshakeMessage = Mockito.mock(InitiatorHandshakeMessage::class.java)

        val mockHeader = Mockito.mock(CommonHeader::class.java)
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

    @Test
    fun `Duplicated session negotiation messages are dropped (with appropriate logging)`() {
        val initiatorSessionManager = sessionManager(PARTY_A)
        val responderSessionManager = sessionManager(PARTY_B)

        val key = getSessionKeyFromMessage(message)

        val initiatorHelloMessage = initiatorSessionManager.getSessionInitMessage(key)
        assertTrue(initiatorHelloMessage?.payload is InitiatorHelloMessage)
        val sessionId = (initiatorHelloMessage!!.payload as InitiatorHelloMessage).header.sessionId

        val step2Message = mockGatewayResponse(initiatorHelloMessage.payload as InitiatorHelloMessage)
        assertNull(responderSessionManager.processSessionMessage(LinkInMessage(step2Message)))
        //Duplicate the Step2Message
        assertNull(responderSessionManager.processSessionMessage(LinkInMessage(step2Message)))

        val initiatorHandshakeMessage = initiatorSessionManager.processSessionMessage(LinkInMessage(step2Message.responderHello))

        //Duplicate Responder Hello message (second time the SessionManager should return null).
        val initiatorMockLogger = Mockito.mock(Logger::class.java)
        initiatorSessionManager.setLogger(initiatorMockLogger)
        assertNull(initiatorSessionManager.processSessionMessage(LinkInMessage(step2Message.responderHello)))
        Mockito.verify(initiatorMockLogger).warn("Already received a ${ResponderHelloMessage::class.java.simpleName} for " +
            "${sessionId}. The message was discarded.")

        assertTrue(initiatorHandshakeMessage?.payload is InitiatorHandshakeMessage)

        val responderHandshakeMessage = responderSessionManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage?.payload))
        assertTrue(responderHandshakeMessage?.payload is ResponderHandshakeMessage)

        //Duplicate Initiator Handshake message (again the second time the SessionManager should return null).
        val responderMockLogger = Mockito.mock(Logger::class.java)
        responderSessionManager.setLogger(responderMockLogger)
        assertNull(responderSessionManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage?.payload)))
        Mockito.verify(responderMockLogger).warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId " +
            "$sessionId but there is no pending session with this id. The message was discarded.")

        assertNull(initiatorSessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage?.payload)))
        assertNull(initiatorSessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage?.payload)))
        Mockito.verify(initiatorMockLogger).warn("Received ${ResponderHandshakeMessage::class.java.simpleName} with sessionId " +
                "$sessionId but there is no pending session with this id. The message was discarded.")

        val initiatorSession = initiatorSessionManager.getInitiatorSession(key)
        assertNotNull(initiatorSession, "Authenticated Session is not stored in the initiator's session manager.")

        val responderSession = responderSessionManager.getResponderSession(sessionId)
        assertNotNull(responderSession, "Authenticated Session is not stored in the responder's session manager.")
    }

    @Test
    fun `InitiatorHandshakeMessage is dropped (with appropriate logging) if authentication fails`() {
        val initiatorSessionManager = sessionManager(PARTY_A)
        val responderSessionManager = sessionManager(PARTY_B)

        val key = getSessionKeyFromMessage(message)

        val initiatorHelloMessage = initiatorSessionManager.getSessionInitMessage(key)
        assertTrue(initiatorHelloMessage?.payload is InitiatorHelloMessage)

        val step2Message = mockGatewayResponse(initiatorHelloMessage?.payload as InitiatorHelloMessage)
        assertNull(responderSessionManager.processSessionMessage(LinkInMessage(step2Message)))

        val sessionId = (initiatorHelloMessage.payload as InitiatorHelloMessage).header.sessionId

        val mockHeader = Mockito.mock(CommonHeader::class.java)
        Mockito.`when`(mockHeader.sessionId).thenReturn(sessionId)
        Mockito.`when`(mockHeader.toByteBuffer()).thenReturn(ByteBuffer.wrap("HEADER".toByteArray()))

        val mockInitiatorHandshakeMessage = Mockito.mock(InitiatorHandshakeMessage::class.java)
        Mockito.`when`(mockInitiatorHandshakeMessage.header).thenReturn(mockHeader)
        Mockito.`when`(mockInitiatorHandshakeMessage.authTag).thenReturn(ByteBuffer.wrap("AuthTag".toByteArray()))
        Mockito.`when`(mockInitiatorHandshakeMessage.encryptedData).thenReturn(ByteBuffer.wrap("EncryptedData".toByteArray()))

        val mockLogger = Mockito.mock(Logger::class.java)
        responderSessionManager.setLogger(mockLogger)

        responderSessionManager.processSessionMessage(LinkInMessage(mockInitiatorHandshakeMessage))
        Mockito.verify(mockLogger).warn("Received ${mockInitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId." +
                " Which failed validation. The message was discarded.")
    }

    @Test
    fun `ResponderHandshakeMessage is dropped (with appropriate logging) if authentication fails`() {
        val initiatorSessionManager = sessionManager(PARTY_A)
        val responderSessionManager = sessionManager(PARTY_B)

        val key = getSessionKeyFromMessage(message)

        val initiatorHelloMessage = initiatorSessionManager.getSessionInitMessage(key)
        assertTrue(initiatorHelloMessage?.payload is InitiatorHelloMessage)

        //Strip the Header from the message (as the Gateway does before sending it).
        val step2Message = mockGatewayResponse(initiatorHelloMessage?.payload as InitiatorHelloMessage)
        assertNull(responderSessionManager.processSessionMessage(LinkInMessage(step2Message)))
        val initiatorHandshakeMessage = initiatorSessionManager.processSessionMessage(LinkInMessage(step2Message.responderHello))

        assertTrue(initiatorHandshakeMessage?.payload is InitiatorHandshakeMessage)
        val sessionId = (initiatorHandshakeMessage?.payload as InitiatorHandshakeMessage).header.sessionId

        val mockHeader = Mockito.mock(CommonHeader::class.java)
        Mockito.`when`(mockHeader.sessionId).thenReturn(sessionId)
        Mockito.`when`(mockHeader.toByteBuffer()).thenReturn(ByteBuffer.wrap("HEADER".toByteArray()))

        val mockResponderHandshakeMessage = Mockito.mock(ResponderHandshakeMessage::class.java)
        Mockito.`when`(mockResponderHandshakeMessage.header).thenReturn(mockHeader)
        Mockito.`when`(mockResponderHandshakeMessage.authTag).thenReturn(ByteBuffer.wrap("AuthTag".toByteArray()))
        Mockito.`when`(mockResponderHandshakeMessage.encryptedData).thenReturn(ByteBuffer.wrap("EncryptedData".toByteArray()))

        val mockLogger = Mockito.mock(Logger::class.java)
        initiatorSessionManager.setLogger(mockLogger)
        assertNull(initiatorSessionManager.processSessionMessage(LinkInMessage(mockResponderHandshakeMessage)) )
        Mockito.verify(mockLogger).warn("Received ${mockResponderHandshakeMessage::class.java.simpleName} with sessionId $sessionId." +
                " Which failed validation. The message was discarded.")
    }
}