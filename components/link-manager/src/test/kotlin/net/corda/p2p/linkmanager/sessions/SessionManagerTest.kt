package net.corda.p2p.linkmanager.sessions

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyOrNull
import net.corda.p2p.FlowMessage
import net.corda.p2p.FlowMessageHeader
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
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
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.LinkManagerCryptoService
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.messaging.MessageConverter
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.convertAuthenticatedEncryptedMessageToFlowMessage
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.convertAuthenticatedMessageToFlowMessage
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.createLinkOutMessageFromFlowMessage
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.NewSessionNeeded
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
import java.util.*
import kotlin.collections.HashMap

class SessionManagerTest {

    companion object {
        private const val GROUP_ID = "myGroup"
        val OUTBOUND_PARTY = LinkManagerNetworkMap.HoldingIdentity("Out", GROUP_ID, LinkManagerNetworkMap.NetworkType.CORDA_5)
        val INBOUND_PARTY = LinkManagerNetworkMap.HoldingIdentity("In", GROUP_ID,  LinkManagerNetworkMap.NetworkType.CORDA_5)
        val PARTY_NOT_IN_NETMAP = LinkManagerNetworkMap.HoldingIdentity("PartyImposter", GROUP_ID, LinkManagerNetworkMap.NetworkType.CORDA_5)
        val FAKE_ENDPOINT = LinkManagerNetworkMap.EndPoint("http://10.0.0.1/")
        const val MAX_MESSAGE_SIZE = 1024 * 1024
        private val provider = BouncyCastleProvider()
        private val signature = Signature.getInstance("ECDSA", provider)

        private fun sessionManagerWithNetMap(
            netMap: LinkManagerNetworkMap,
            cryptoService: LinkManagerCryptoService = Mockito.mock(LinkManagerCryptoService::class.java)
        ): SessionManagerImpl {
            return SessionManagerImpl(
                setOf(ProtocolMode.AUTHENTICATION_ONLY),
                netMap,
                cryptoService,
                MAX_MESSAGE_SIZE,
                MockSessionMessageQueues()
            )
        }

        private fun signDataWithKey(key: PrivateKey, data: ByteArray): ByteArray {
            signature.initSign(key)
            signature.update(data)
            return signature.sign()
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
            fun getKeyPair(): KeyPair
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
                    return keys[node]!!.public
                }

                override fun getOurHoldingIdentity(groupId: String?): LinkManagerNetworkMap.HoldingIdentity {
                    return node
                }

                override fun getPrivateKeyFromHash(hash: ByteArray): PrivateKey {
                    return keys[node]!!.private
                }

                override fun getKeyPair(): KeyPair {
                    return keys[node]!!
                }
            }
        }
    }

    class MockCryptoService(private val mockNetworkMap: MockNetworkMap.MockLinkManagerNetworkMap) : LinkManagerCryptoService {
        override fun signData(hash: ByteArray, data: ByteArray): ByteArray {
            val key = mockNetworkMap.getPrivateKeyFromHash(hash)
            return signDataWithKey(key, data)
        }
    }

    class MockSessionMessageQueues : LinkManager.PendingSessionMessageQueues {
        private val messageQueue = mutableListOf<FlowMessage>()
        private var negotiationStarted = false
        val processedMessageQueue = mutableListOf<LinkOutMessage>()

        override fun queueMessage(message: FlowMessage, key: SessionManagerImpl.SessionKey): Boolean {
            messageQueue.add(message)
            return if (!negotiationStarted) {
                negotiationStarted = true
                true
            } else {
                false
            }
        }

        override fun sessionNegotiatedCallback(
            key: SessionManagerImpl.SessionKey,
            session: Session,
            networkMap: LinkManagerNetworkMap
        ) {
            for (message in messageQueue) {
                createLinkOutMessageFromFlowMessage(message, session, networkMap)?.let { processedMessageQueue.add(it) }
            }
        }
    }

    private val globalNetMap = MockNetworkMap(listOf(OUTBOUND_PARTY, INBOUND_PARTY))
    private val netMapOutbound = globalNetMap.getSessionNetworkMapForNode(OUTBOUND_PARTY)
    private val netMapInbound = globalNetMap.getSessionNetworkMapForNode(INBOUND_PARTY)

    private val payload = ByteBuffer.wrap("Hi inbound it's outbound here".toByteArray())
    private val message = FlowMessage(FlowMessageHeader(
        INBOUND_PARTY.toHoldingIdentity(),
        OUTBOUND_PARTY.toHoldingIdentity(),
        null,
        "messageId",
        ""), payload)

    private fun sessionManager(
        party: LinkManagerNetworkMap.HoldingIdentity,
        queues: MockSessionMessageQueues = MockSessionMessageQueues(),
        mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY,
        cryptoService: LinkManagerCryptoService? = null
    ) : SessionManagerImpl {
        val netMap = globalNetMap.getSessionNetworkMapForNode(party)
        val realCryptoService = cryptoService ?: MockCryptoService(netMap)
        return SessionManagerImpl(
            setOf(mode),
            globalNetMap.getSessionNetworkMapForNode(party),
            realCryptoService,
            MAX_MESSAGE_SIZE,
            queues)
    }

    private fun mockGatewayResponse(message: InitiatorHelloMessage, mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY): Step2Message {
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

    private fun negotiateOutboundSession(
        flowMessage: FlowMessage,
        outboundManager: SessionManager,
        supportedMode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY
    ): Session {
        val state = outboundManager.processOutboundFlowMessage(flowMessage)
        assertTrue(state is NewSessionNeeded)
        val initiatorHelloMessage = (state as NewSessionNeeded).sessionInitMessage

        //Strip the Header from the message (as the Gateway does before sending it).
        val step2Message = mockGatewayResponse(initiatorHelloMessage.payload as InitiatorHelloMessage, supportedMode)

        val protocolResponder = AuthenticationProtocolResponder.fromStep2(
            step2Message.initiatorHello.header.sessionId,
            setOf(supportedMode),
            MAX_MESSAGE_SIZE,
            step2Message.initiatorHello,
            step2Message.responderHello,
            step2Message.privateKey.array(),
            step2Message.responderHello.responderPublicKey.array())
        protocolResponder.generateHandshakeSecrets()
        val initiatorHandshakeMessage = outboundManager.processSessionMessage(LinkInMessage(step2Message.responderHello))

        assertTrue(initiatorHandshakeMessage!!.payload is InitiatorHandshakeMessage)

        protocolResponder.validatePeerHandshakeMessage(initiatorHandshakeMessage.payload as InitiatorHandshakeMessage) {
            netMapOutbound.getKeyPair().public
        }

        val responderHandshakeMessage = protocolResponder.generateOurHandshakeMessage(netMapInbound.getKeyPair().public) {
            signDataWithKey(netMapInbound.getKeyPair().private, it)
        }
        assertNull(outboundManager.processSessionMessage(LinkInMessage(responderHandshakeMessage)))

        return protocolResponder.getSession()
    }

    private fun negotiateInboundSession(
        sessionId: String,
        inboundManager: SessionManager,
        supportedMode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY
    ): Session {
        val protocolInitiator = AuthenticationProtocolInitiator(sessionId, setOf(supportedMode), MAX_MESSAGE_SIZE)
        val initiatorHelloMessage = protocolInitiator.generateInitiatorHello()
        val step2Message = mockGatewayResponse(initiatorHelloMessage, supportedMode)
        assertNull(inboundManager.processSessionMessage(LinkInMessage(step2Message)))
        protocolInitiator.receiveResponderHello(step2Message.responderHello)
        protocolInitiator.generateHandshakeSecrets()
        val initiatorHandshakeMessage = protocolInitiator.generateOurHandshakeMessage(
            netMapOutbound.getKeyPair().public,
            netMapInbound.getKeyPair().public,
            GROUP_ID
        ) { signDataWithKey(netMapOutbound.getKeyPair().private, it) }
        val responderHandshakeMessage = inboundManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage))
        assertTrue(responderHandshakeMessage?.payload is ResponderHandshakeMessage)

        protocolInitiator.validatePeerHandshakeMessage(
            responderHandshakeMessage?.payload as ResponderHandshakeMessage,
            netMapInbound.getKeyPair().public
        )

        return protocolInitiator.getSession()
    }

    private fun negotiateToInitiatorHandshake(
        inboundManager: SessionManager,
        sessionId: String,
        mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY,
    ): InitiatorHandshakeMessage {
        val protocolInitiator = AuthenticationProtocolInitiator(sessionId, setOf(mode), MAX_MESSAGE_SIZE)
        val initiatorHelloMessage = protocolInitiator.generateInitiatorHello()
        val gatewayResponse = mockGatewayResponse(initiatorHelloMessage, mode)
        inboundManager.processSessionMessage(LinkInMessage(gatewayResponse))
        protocolInitiator.receiveResponderHello(gatewayResponse.responderHello)
        protocolInitiator.generateHandshakeSecrets()
        return protocolInitiator.generateOurHandshakeMessage(
            netMapOutbound.getKeyPair().public,
            netMapInbound.getKeyPair().public,
            GROUP_ID
        ) {
            signDataWithKey(netMapOutbound.getKeyPair().private, it)
        }
    }

    private fun negotiateToStep2Message(
        outboundManager: SessionManager,
        message: FlowMessage,
        mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY
    ): Step2Message {
        val sessionState = outboundManager.processOutboundFlowMessage(message)
        val helloMessage = ((sessionState as NewSessionNeeded).sessionInitMessage.payload as InitiatorHelloMessage)
        return mockGatewayResponse(helloMessage, mode)
    }

    private fun negotiateToResponderHandshake(
        outboundManager: SessionManager,
        message: FlowMessage,
        mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY,
    ): ResponderHandshakeMessage {
        val step2Message = negotiateToStep2Message(outboundManager, message, mode)
        return negotiateToResponderHandshake(step2Message, outboundManager, mode)
    }

    private fun negotiateToResponderHandshake(
        step2Message: Step2Message,
        outboundManager: SessionManager,
        mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY
    ): ResponderHandshakeMessage {
        val protocolResponder = AuthenticationProtocolResponder.fromStep2(
            step2Message.initiatorHello.header.sessionId,
            setOf(mode),
            MAX_MESSAGE_SIZE,
            step2Message.initiatorHello,
            step2Message.responderHello,
            step2Message.privateKey.array(),
            step2Message.responderHello.responderPublicKey.array())
        protocolResponder.generateHandshakeSecrets()
        val initiatorHandshakeMessage = outboundManager.processSessionMessage(LinkInMessage(step2Message.responderHello))
        protocolResponder.validatePeerHandshakeMessage(initiatorHandshakeMessage?.payload as InitiatorHandshakeMessage) {
            netMapOutbound.getKeyPair().public
        }
        return protocolResponder.generateOurHandshakeMessage(netMapInbound.getKeyPair().public) {
            signDataWithKey(netMapInbound.getKeyPair().private, it)
        }
    }

    private fun hashKey(key: PublicKey): ByteArray {
        val provider = BouncyCastleProvider()
        val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, provider)
        messageDigest.reset()
        messageDigest.update(key.encoded)
        return messageDigest.digest()
    }

    private fun hashKeyToBase64(key: PublicKey): String {
        return Base64.getEncoder().encodeToString(hashKey(key))
    }

    @Test
    fun `A session can be negotiated by a SessionManager and a message can be sent (in AUTHENTICATION_ONLY mode)`() {
        val queues = MockSessionMessageQueues()
        val outboundSessionManager = sessionManager(OUTBOUND_PARTY, queues)
        val responderSession = negotiateOutboundSession(message, outboundSessionManager)

        assertTrue(responderSession is AuthenticatedSession)
        assertEquals(queues.processedMessageQueue.size, 1)

        val messageFromQueue = queues.processedMessageQueue[0]
        assertTrue(messageFromQueue.payload is AuthenticatedDataMessage)
        val authenticatedDataMessage = (messageFromQueue.payload as AuthenticatedDataMessage)

        val responderMessage = convertAuthenticatedMessageToFlowMessage(authenticatedDataMessage, responderSession as AuthenticatedSession)
        assertEquals(message.payload, responderMessage!!.payload)
    }

    @Test
    fun `A session can be negotiated by a SessionManager and a message can be sent and decrypted (in AUTHENTICATED_ENCRYPTION mode)`() {
        val queues = MockSessionMessageQueues()
        val outboundSessionManager = sessionManager(OUTBOUND_PARTY, queues, ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responderSession = negotiateOutboundSession(message, outboundSessionManager, ProtocolMode.AUTHENTICATED_ENCRYPTION)

        assertTrue(responderSession is AuthenticatedEncryptionSession)
        assertEquals(queues.processedMessageQueue.size, 1)

        val messageFromQueue = queues.processedMessageQueue[0]
        assertTrue(messageFromQueue.payload is AuthenticatedEncryptedDataMessage)
        val authenticatedDataMessage = (messageFromQueue.payload as AuthenticatedEncryptedDataMessage)

        val responderMessage = convertAuthenticatedEncryptedMessageToFlowMessage(
            authenticatedDataMessage,
            responderSession as AuthenticatedEncryptionSession
        )

        assertEquals(message.payload, responderMessage!!.payload)
    }

    @Test
    fun `A session can be negotiated with a SessionManager and a message can be received (in AUTHENTICATION_ONLY mode)`() {
        val sessionId = "Session"
        val inboundSessionManager = sessionManager(INBOUND_PARTY)
        val initiatorSession = negotiateInboundSession(sessionId, inboundSessionManager)

        assertTrue(initiatorSession is AuthenticatedSession)

        val authenticatedMessage = createLinkOutMessageFromFlowMessage(message, initiatorSession, netMapInbound)
        assertTrue(authenticatedMessage!!.payload is AuthenticatedDataMessage)
        val inboundSession = inboundSessionManager.getInboundSession(sessionId)

        assertTrue(inboundSession is AuthenticatedSession)

        val responderMessage = convertAuthenticatedMessageToFlowMessage(
            authenticatedMessage.payload as AuthenticatedDataMessage,
            inboundSession as AuthenticatedSession
        )
        assertEquals(message.payload, responderMessage!!.payload)
    }

    @Test
    fun `A session can be negotiated with a SessionManager and a message can be received (in AUTHENTICATED_ENCRYPTION mode)`() {
        val sessionId = "Session"
        val inboundSessionManager = sessionManager(INBOUND_PARTY, mode = ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val initiatorSession = negotiateInboundSession(sessionId, inboundSessionManager, ProtocolMode.AUTHENTICATED_ENCRYPTION)

        assertTrue(initiatorSession is AuthenticatedEncryptionSession)

        val authenticatedEncryptedMessage = createLinkOutMessageFromFlowMessage(message, initiatorSession, netMapInbound)
        assertTrue(authenticatedEncryptedMessage!!.payload is AuthenticatedEncryptedDataMessage)
        val inboundSession = inboundSessionManager.getInboundSession(sessionId)

        assertTrue(inboundSession is AuthenticatedEncryptionSession)

        val responderMessage = convertAuthenticatedEncryptedMessageToFlowMessage(
            authenticatedEncryptedMessage.payload as AuthenticatedEncryptedDataMessage,
            inboundSession as AuthenticatedEncryptionSession
        )
        assertEquals(message.payload, responderMessage!!.payload)
    }

    @Test
    fun `Session messages are dropped (with appropriate logging) if there is no pending session`() {
        val inboundManager = sessionManager(INBOUND_PARTY)

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
        inboundManager.setLogger(mockLogger)

        for (mockMessage in mockMessages) {
            assertNull(inboundManager.processSessionMessage(LinkInMessage(mockMessage)))
            Mockito.verify(mockLogger).warn("Received ${mockMessage::class.java.simpleName} with sessionId" +
                    " $fakeSession but there is no pending session with this id. The message was discarded.")
        }
    }

    @Test
    fun `Duplicated session negotiation messages (ResponderHello and ResponderHandshake) are dropped (with appropriate logging)`() {
        val mode = ProtocolMode.AUTHENTICATION_ONLY
        val outboundManager = sessionManager(OUTBOUND_PARTY)
        val state = outboundManager.processOutboundFlowMessage(message)

        assertTrue(state is NewSessionNeeded)
        val initiatorHelloMessage = (state as NewSessionNeeded).sessionInitMessage

        val step2Message = mockGatewayResponse(initiatorHelloMessage.payload as InitiatorHelloMessage, mode)
        val sessionId = step2Message.initiatorHello.header.sessionId

        val protocolResponder = AuthenticationProtocolResponder.fromStep2(
            step2Message.initiatorHello.header.sessionId,
            setOf(mode),
            MAX_MESSAGE_SIZE,
            step2Message.initiatorHello,
            step2Message.responderHello,
            step2Message.privateKey.array(),
            step2Message.responderHello.responderPublicKey.array())
        protocolResponder.generateHandshakeSecrets()
        val initiatorHandshakeMessage = outboundManager.processSessionMessage(LinkInMessage(step2Message.responderHello))

        val mockLogger = Mockito.mock(Logger::class.java)
        outboundManager.setLogger(mockLogger)
        //Duplicate Responder Hello message (second time the SessionManager should return null).
        assertNull(outboundManager.processSessionMessage(LinkInMessage(step2Message.responderHello)))
        Mockito.verify(mockLogger).warn("Already received a ${ResponderHelloMessage::class.java.simpleName} for " +
            "$sessionId. The message was discarded.")

        assertTrue(initiatorHandshakeMessage!!.payload is InitiatorHandshakeMessage)

        protocolResponder.validatePeerHandshakeMessage(initiatorHandshakeMessage.payload as InitiatorHandshakeMessage) {
            netMapOutbound.getKeyPair().public
        }

        val responderHandshakeMessage = protocolResponder.generateOurHandshakeMessage(netMapInbound.getKeyPair().public) {
            signDataWithKey(netMapInbound.getKeyPair().private, it)
        }

        //Duplicate ResponderHandshakeMessage
        assertNull(outboundManager.processSessionMessage(LinkInMessage(responderHandshakeMessage)))
        assertNull(outboundManager.processSessionMessage(LinkInMessage(responderHandshakeMessage)))
        Mockito.verify(mockLogger).warn("Received ${ResponderHandshakeMessage::class.java.simpleName} with sessionId " +
            "$sessionId but there is no pending session with this id. The message was discarded.")
    }

    @Test
    fun `Duplicated session negotiation message (InitiatorHandshake) is dropped (with appropriate logging)`() {
        val mode = ProtocolMode.AUTHENTICATION_ONLY
        val sessionId = "FakeSession"
        val inboundManager = sessionManager(OUTBOUND_PARTY)

        val protocolInitiator = AuthenticationProtocolInitiator(sessionId, setOf(mode), MAX_MESSAGE_SIZE)
        val initiatorHelloMessage = protocolInitiator.generateInitiatorHello()
        val step2Message = mockGatewayResponse(initiatorHelloMessage, mode)
        assertNull(inboundManager.processSessionMessage(LinkInMessage(step2Message)))

        protocolInitiator.receiveResponderHello(step2Message.responderHello)
        protocolInitiator.generateHandshakeSecrets()
        val initiatorHandshakeMessage = protocolInitiator.generateOurHandshakeMessage(
            netMapOutbound.getKeyPair().public,
            netMapInbound.getKeyPair().public,
            GROUP_ID
        ) { signDataWithKey(netMapOutbound.getKeyPair().private, it) }

        val responderHandshakeMessage = inboundManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage))
        assertTrue(responderHandshakeMessage?.payload is ResponderHandshakeMessage)

        //Duplicate InitiatorHandshakeMessage
        val mockLogger = Mockito.mock(Logger::class.java)
        inboundManager.setLogger(mockLogger)
        assertNull(inboundManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage)))
        Mockito.verify(mockLogger).warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId " +
            "$sessionId but there is no pending session with this id. The message was discarded.")
    }

    @Test
    fun `InitiatorHandshakeMessage is dropped (with appropriate logging) if authentication fails`() {
        val inboundManager = sessionManager(INBOUND_PARTY)
        val sessionId = "sessionId"
        negotiateToInitiatorHandshake(inboundManager, sessionId)

        val mockHeader = Mockito.mock(CommonHeader::class.java)
        Mockito.`when`(mockHeader.sessionId).thenReturn(sessionId)
        Mockito.`when`(mockHeader.toByteBuffer()).thenReturn(ByteBuffer.wrap("HEADER".toByteArray()))

        val mockInitiatorHandshakeMessage = Mockito.mock(InitiatorHandshakeMessage::class.java)
        Mockito.`when`(mockInitiatorHandshakeMessage.header).thenReturn(mockHeader)
        Mockito.`when`(mockInitiatorHandshakeMessage.authTag).thenReturn(ByteBuffer.wrap("AuthTag".toByteArray()))
        Mockito.`when`(mockInitiatorHandshakeMessage.encryptedData).thenReturn(ByteBuffer.wrap("EncryptedData".toByteArray()))

        val mockLogger = Mockito.mock(Logger::class.java)
        inboundManager.setLogger(mockLogger)

        inboundManager.processSessionMessage(LinkInMessage(mockInitiatorHandshakeMessage))
        Mockito.verify(mockLogger).warn("Received ${mockInitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId," +
            " which failed validation with: The handshake message was invalid. The message was discarded.")
    }

    @Test
    fun `ResponderHandshakeMessage is dropped (with appropriate logging) if authentication fails`() {
        val outboundManager = sessionManager(OUTBOUND_PARTY)
        val sessionId = negotiateToResponderHandshake(outboundManager, message).header.sessionId

        val mockHeader = Mockito.mock(CommonHeader::class.java)
        Mockito.`when`(mockHeader.sessionId).thenReturn(sessionId)
        Mockito.`when`(mockHeader.toByteBuffer()).thenReturn(ByteBuffer.wrap("HEADER".toByteArray()))

        val mockResponderHandshakeMessage = Mockito.mock(ResponderHandshakeMessage::class.java)
        Mockito.`when`(mockResponderHandshakeMessage.header).thenReturn(mockHeader)
        Mockito.`when`(mockResponderHandshakeMessage.authTag).thenReturn(ByteBuffer.wrap("AuthTag".toByteArray()))
        Mockito.`when`(mockResponderHandshakeMessage.encryptedData).thenReturn(ByteBuffer.wrap("EncryptedData".toByteArray()))

        val mockLogger = Mockito.mock(Logger::class.java)
        outboundManager.setLogger(mockLogger)
        assertNull(outboundManager.processSessionMessage(LinkInMessage(mockResponderHandshakeMessage)) )
        Mockito.verify(mockLogger).warn("Received ${mockResponderHandshakeMessage::class.java.simpleName} with sessionId $sessionId," +
            " which failed validation with: The handshake message was invalid. The message was discarded.")
    }

    @Test
    fun `Cannot generate a session init message for a party not in the network map`() {
        val outboundSessionManager = sessionManager(OUTBOUND_PARTY)

        val mockLogger = Mockito.mock(Logger::class.java)
        outboundSessionManager.setLogger(mockLogger)
        MessageConverter.setLogger(mockLogger)

        val message = FlowMessage(FlowMessageHeader(
            PARTY_NOT_IN_NETMAP.toHoldingIdentity(),
            OUTBOUND_PARTY.toHoldingIdentity(),
            null,
            "messageId",
            ""), payload)

        val state = outboundSessionManager.processOutboundFlowMessage(message)

        assertTrue(state is SessionManager.SessionState.CannotEstablishSession)
        Mockito.verify(mockLogger).warn("Attempted to send message to peer ${PARTY_NOT_IN_NETMAP.toHoldingIdentity()} which is not in" +
            " the network map. The message was discarded.")
    }

    @Test
    fun `Responder hello message is dropped if we are not in the network map`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getEndPoint(any())).thenReturn(FAKE_ENDPOINT)
        Mockito.`when`(netMap.getOurPublicKey(any())).thenReturn(null)
        val mockLogger = Mockito.mock(Logger::class.java)

        val outboundManager = sessionManagerWithNetMap(netMap)
        outboundManager.setLogger(mockLogger)
        val step2Message = negotiateToStep2Message(outboundManager, message)
        assertNull(outboundManager.processSessionMessage(LinkInMessage(step2Message.responderHello)))

        val sessionId = step2Message.initiatorHello.header.sessionId
        Mockito.verify(mockLogger).warn("Received ${ResponderHelloMessage::class.java.simpleName} with sessionId $sessionId" +
            " but cannot find public key for our group identity ${GROUP_ID}. The message was discarded.")
    }

    @Test
    fun `Responder hello message is dropped if the receiver is not in the network map`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getEndPoint(any())).thenReturn(FAKE_ENDPOINT)
        Mockito.`when`(netMap.getOurPublicKey(anyOrNull())).thenReturn(netMapOutbound.getKeyPair().public)
        val mockLogger = Mockito.mock(Logger::class.java)

        val outboundManager = sessionManagerWithNetMap(netMap)
        outboundManager.setLogger(mockLogger)
        val step2Message = negotiateToStep2Message(outboundManager, message)
        assertNull(outboundManager.processSessionMessage(LinkInMessage(step2Message.responderHello)))

        val sessionId = step2Message.initiatorHello.header.sessionId
        Mockito.verify(mockLogger).warn(
            "Received ResponderHelloMessage with sessionId $sessionId from peer $INBOUND_PARTY which is not" +
                    " in the network map. The message was discarded."
        )
    }

    @Test
    fun `Responder hello message is dropped if we are removed from the network map during processSessionMessage`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getEndPoint(any())).thenReturn(FAKE_ENDPOINT)
        Mockito.`when`(netMap.getOurPublicKey(anyOrNull())).thenReturn(netMapOutbound.getKeyPair().public)
        Mockito.`when`(netMap.getPublicKey(anyOrNull())).thenReturn(netMapOutbound.getKeyPair().public)
        Mockito.`when`(netMap.hashPublicKey(anyOrNull())).thenReturn(hashKey(netMapOutbound.getKeyPair().public))
        val mockLogger = Mockito.mock(Logger::class.java)

        val keyHash = "KEY HASH"
        val cryptoService = Mockito.mock(LinkManagerCryptoService::class.java)
        Mockito.`when`(cryptoService.signData(any(), any())).thenThrow(LinkManagerCryptoService.NoPrivateKeyForGroupException(keyHash))

        val outboundManager = sessionManagerWithNetMap(netMap, cryptoService)
        outboundManager.setLogger(mockLogger)
        val step2Message = negotiateToStep2Message(outboundManager, message)
        assertNull(outboundManager.processSessionMessage(LinkInMessage(step2Message.responderHello)))

        Mockito.verify(mockLogger).warn("Could not find (our) private key in the network map corresponding to public key hash" +
            " = $keyHash. The ResponderHelloMessage was discarded.")
    }

    @Test
    fun `Responder hello message is dropped if the receiver is removed from the network map during processSessionMessage`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getEndPoint(any())).thenReturn(FAKE_ENDPOINT).thenReturn(null)
        Mockito.`when`(netMap.getOurPublicKey(anyOrNull())).thenReturn(netMapOutbound.getKeyPair().public)
        Mockito.`when`(netMap.getPublicKey(anyOrNull())).thenReturn(netMapOutbound.getKeyPair().public)
        Mockito.`when`(netMap.hashPublicKey(anyOrNull())).thenReturn(hashKey(netMapOutbound.getKeyPair().public))

        val mockLogger = Mockito.mock(Logger::class.java)
        val outboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapOutbound))
        MessageConverter.setLogger(mockLogger)
        val step2Message = negotiateToStep2Message(outboundManager, message)
        assertNull(outboundManager.processSessionMessage(LinkInMessage(step2Message.responderHello)))

        Mockito.verify(mockLogger).warn("Attempted to send message to peer ${INBOUND_PARTY.toHoldingIdentity()} which is not in the" +
            " network map. The message was discarded.")
    }

    @Test
    fun `Initiator handshake message is dropped if the sender public key hash is not in the network map`() {
        val mockLogger = Mockito.mock(Logger::class.java)
        val sessionId = "SessionId"

        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getEndPoint(INBOUND_PARTY)).thenReturn(FAKE_ENDPOINT)
        Mockito.`when`(netMap.getOurPublicKey(anyOrNull())).thenReturn(netMapInbound.getKeyPair().public)
        Mockito.`when`(netMap.getPublicKey(anyOrNull())).thenReturn(netMapInbound.getKeyPair().public)
        Mockito.`when`(netMap.hashPublicKey(anyOrNull())).thenReturn(hashKey(netMapInbound.getKeyPair().public))

        val keyHash = hashKeyToBase64(netMapOutbound.getKeyPair().public)

        val inboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapInbound))
        inboundManager.setLogger(mockLogger)
        val message = negotiateToInitiatorHandshake(inboundManager, sessionId)
        val response = inboundManager.processSessionMessage(LinkInMessage(message))

        assertNull(response)

        Mockito.verify(mockLogger).warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId." +
                " Could not find the public key in the network map by hash = $keyHash. The message was discarded.")
    }

    @Test
    fun `Initiator handshake message is dropped if the receiver public key hash is not in the network map`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        val sessionId = "SessionId"

        Mockito.`when`(netMap.getPublicKeyFromHash(anyOrNull())).thenReturn(netMapOutbound.getKeyPair().public)
        Mockito.`when`(netMap.getPeerFromHash(anyOrNull())).thenReturn(null)

        val mockLogger = Mockito.mock(Logger::class.java)

        val inboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapInbound))
        inboundManager.setLogger(mockLogger)
        val message = negotiateToInitiatorHandshake(inboundManager, sessionId)
        val response = inboundManager.processSessionMessage(LinkInMessage(message))
        assertNull(response)

        val keyHash = hashKeyToBase64(netMapInbound.getKeyPair().public)
        Mockito.verify(mockLogger).warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId." +
                " The received public key hash ($keyHash) corresponding to one of our holding identities is not in the network map." +
                " The message was discarded.")
    }

    @Test
    fun `Initiator handshake message is dropped if the receiver is removed from the network map`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        val sessionId = "SessionId"

        Mockito.`when`(netMap.getPublicKeyFromHash(anyOrNull())).thenReturn(netMapOutbound.getKeyPair().public)
        Mockito.`when`(netMap.getPeerFromHash(anyOrNull())).thenReturn(OUTBOUND_PARTY)
        Mockito.`when`(netMap.getOurPublicKey(anyOrNull())).thenReturn(null)

        val mockLogger = Mockito.mock(Logger::class.java)
        val inboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapInbound))
        inboundManager.setLogger(mockLogger)

        val message = negotiateToInitiatorHandshake(inboundManager, sessionId)
        val response = inboundManager.processSessionMessage(LinkInMessage(message))
        assertNull(response)

        Mockito.verify(mockLogger).warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId" +
            " but cannot find public key for our group identity ${INBOUND_PARTY.groupId}. The message was discarded.")
    }

    @Test
    fun `Initiator handshake message is dropped if the sender is removed from the network map`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        val sessionId = "SessionId"
        val keyHash = "key hash"

        val initiatorPublicKey = netMapOutbound.getKeyPair().public
        val initiatorKeyHash = hashKey(initiatorPublicKey!!)

        Mockito.`when`(netMap.getPublicKeyFromHash(initiatorKeyHash)).thenReturn(initiatorPublicKey)
        Mockito.`when`(netMap.getPeerFromHash(anyOrNull())).thenReturn(OUTBOUND_PARTY)
        Mockito.`when`(netMap.getOurPublicKey(anyOrNull())).thenReturn(netMapInbound.getKeyPair().public)

        val cryptoService = Mockito.mock(LinkManagerCryptoService::class.java)
        Mockito.`when`(cryptoService.signData(any(), any())).thenThrow(LinkManagerCryptoService.NoPrivateKeyForGroupException(keyHash))

        val mockLogger = Mockito.mock(Logger::class.java)
        val inboundManager = sessionManagerWithNetMap(netMap, cryptoService)
        inboundManager.setLogger(mockLogger)

        val message = negotiateToInitiatorHandshake(inboundManager, sessionId)
        val response = inboundManager.processSessionMessage(LinkInMessage(message))
        assertNull(response)

        Mockito.verify(mockLogger).warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId." +
            " Could not find (our) private key in the network map corresponding to public key hash = $keyHash. The message was" +
            " discarded.")
    }

    @Test
    fun `Initiator handshake message is dropped if the sender is removed from the network map before the second last step`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        val sessionId = "SessionId"

        val outboundKeyHash = hashKey(netMapOutbound.getKeyPair().public)
        val inboundKeyHash = hashKey(netMapInbound.getKeyPair().public)

        Mockito.`when`(netMap.getPublicKeyFromHash(outboundKeyHash)).thenReturn(netMapOutbound.getKeyPair().public)
        Mockito.`when`(netMap.getPublicKeyFromHash(inboundKeyHash)).thenReturn(netMapInbound.getKeyPair().public)
        Mockito.`when`(netMap.getPeerFromHash(any())).thenReturn(OUTBOUND_PARTY).thenReturn(null)
        Mockito.`when`(netMap.getOurPublicKey(anyOrNull())).thenReturn(netMapInbound.getKeyPair().public)

        val mockLogger = Mockito.mock(Logger::class.java)
        val inboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapInbound))
        inboundManager.setLogger(mockLogger)

        val message = negotiateToInitiatorHandshake(inboundManager, sessionId)
        val response = inboundManager.processSessionMessage(LinkInMessage(message))
        assertNull(response)

        Mockito.verify(mockLogger).warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId." +
            " The received public key hash (${hashKeyToBase64(netMapOutbound.getKeyPair().public)}) corresponding to one of the" +
            " senders holding identities is not in the network map. The message was discarded.")
    }

    @Test
    fun `Initiator handshake message is dropped if the sender is removed from the network map before the last step`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        val sessionId = "SessionId"

        val outboundKeyHash = hashKey(netMapOutbound.getKeyPair().public)
        val inboundKeyHash = hashKey(netMapInbound.getKeyPair().public)
        Mockito.`when`(netMap.getPublicKeyFromHash(outboundKeyHash)).thenReturn(netMapOutbound.getKeyPair().public)
        Mockito.`when`(netMap.getPublicKeyFromHash(inboundKeyHash)).thenReturn((netMapInbound.getKeyPair().public))
        Mockito.`when`(netMap.getPeerFromHash(anyOrNull())).thenReturn(INBOUND_PARTY)
        Mockito.`when`(netMap.getOurPublicKey(anyOrNull())).thenReturn(netMapInbound.getKeyPair().public)

        val mockLogger = Mockito.mock(Logger::class.java)
        val inboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapInbound))
        MessageConverter.setLogger(mockLogger)

        val message = negotiateToInitiatorHandshake(inboundManager, sessionId)
        val response = inboundManager.processSessionMessage(LinkInMessage(message))

        assertNull(response)

        Mockito.verify(mockLogger).warn("Attempted to send message to peer ${INBOUND_PARTY.toHoldingIdentity()} which is not in" +
                " the network map. The message was discarded.")
    }

    @Test
    fun `Responder handshake message is dropped if the sender is not in the network map`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getEndPoint(INBOUND_PARTY)).thenReturn(FAKE_ENDPOINT)
        Mockito.`when`(netMap.getOurPublicKey(anyOrNull())).thenReturn(netMapOutbound.getKeyPair().public)

        //Called for the first time in `processResponderHello` and the second time in processResponderHandshake.
        Mockito.`when`(netMap.getPublicKey(anyOrNull())).thenReturn(netMapOutbound.getKeyPair().public).thenReturn(null)
        Mockito.`when`(netMap.hashPublicKey(anyOrNull())).thenReturn(hashKey(netMapOutbound.getKeyPair().public))

        val mockLogger = Mockito.mock(Logger::class.java)

        val outboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapOutbound))
        outboundManager.setLogger(mockLogger)

        val message = negotiateToResponderHandshake(outboundManager, message)
        val response = outboundManager.processSessionMessage(LinkInMessage(message))
        assertNull(response)

        val sessionId = message.header.sessionId
        Mockito.verify(mockLogger).warn("Received ${ResponderHandshakeMessage::class.java.simpleName} with sessionId $sessionId from peer " +
            "$INBOUND_PARTY which is not in the network map. The message was discarded.")
    }
}