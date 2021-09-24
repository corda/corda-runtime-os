package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.app.HoldingIdentity
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.ECDSA_SIGNATURE_ALGO
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.LinkManagerCryptoService
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.delivery.SessionReplayer
import net.corda.p2p.linkmanager.messaging.AvroSealedClasses.DataMessage
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.extractPayload
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.linkOutMessageFromFlowMessageAndKey
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.NewSessionNeeded
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.linkmanager.utilities.MockNetworkMap
import net.corda.v5.base.util.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.*

class SessionManagerTest {

    companion object {
        private val KEY = "KEY"
        private const val GROUP_ID = "myGroup"
        val OUTBOUND_PARTY = LinkManagerNetworkMap.HoldingIdentity("Out", GROUP_ID)
        val INBOUND_PARTY = LinkManagerNetworkMap.HoldingIdentity("In", GROUP_ID)
        val PARTY_NOT_IN_NETMAP = LinkManagerNetworkMap.HoldingIdentity("PartyImposter", GROUP_ID)
        val FAKE_ENDPOINT = LinkManagerNetworkMap.EndPoint("http://10.0.0.1/")
        const val MAX_MESSAGE_SIZE = 1024 * 1024
        private val provider = BouncyCastleProvider()
        private val signature = Signature.getInstance(ECDSA_SIGNATURE_ALGO, provider)
        lateinit var loggingInterceptor: LoggingInterceptor

        val noReplayer = object: SessionReplayer {
            override fun addMessageForReplay(uniqueId: String, messageReplay: SessionReplayer.SessionMessageReplay) {}

            override fun removeMessageFromReplay(uniqueId: String) {}
        }

        private fun sessionManagerWithNetMap(
            netMap: LinkManagerNetworkMap,
            cryptoService: LinkManagerCryptoService = Mockito.mock(LinkManagerCryptoService::class.java),
            messageReplayer: SessionReplayer = noReplayer
        ): SessionManagerImpl {
            return SessionManagerImpl(
                SessionManagerImpl.ParametersForSessionNegotiation(MAX_MESSAGE_SIZE, setOf(ProtocolMode.AUTHENTICATION_ONLY)),
                netMap,
                cryptoService,
                MockSessionMessageQueues(),
                messageReplayer
            )
        }

        private fun signDataWithKey(key: PrivateKey, data: ByteArray): ByteArray {
            signature.initSign(key)
            signature.update(data)
            return signature.sign()
        }

        internal fun LinkManagerNetworkMap.HoldingIdentity.toHoldingIdentity(): HoldingIdentity {
            return HoldingIdentity(x500Name, groupId)
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            loggingInterceptor = LoggingInterceptor.setupLogging()
        }
    }

    @AfterEach
    fun resetLogging() {
        loggingInterceptor.reset()
    }

    class MockCryptoService(private val mockNetworkMap: MockNetworkMap.MockLinkManagerNetworkMap) : LinkManagerCryptoService {
        override fun signData(publicKey: PublicKey, data: ByteArray): ByteArray {
            val key = mockNetworkMap.getPrivateKeyFromPublicKey(publicKey)
            return signDataWithKey(key, data)
        }
    }

    class MockSessionMessageQueues : LinkManager.PendingSessionMessageQueues {
        private val messageQueue = mutableListOf<AuthenticatedMessageAndKey>()
        private var negotiationStarted = false
        val processedMessageQueue = mutableListOf<LinkOutMessage>()

        override fun queueMessage(message: AuthenticatedMessageAndKey, key: SessionManagerImpl.SessionKey): Boolean {
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
                linkOutMessageFromFlowMessageAndKey(message, session, networkMap)?.let { processedMessageQueue.add(it) }
            }
        }
    }

    class MockSessionReplayer: SessionReplayer {

        val messagesForReplay = mutableMapOf<String, SessionReplayer.SessionMessageReplay>()

        override fun addMessageForReplay(uniqueId: String, messageReplay: SessionReplayer.SessionMessageReplay) {
            messagesForReplay[uniqueId] = messageReplay
        }

        override fun removeMessageFromReplay(uniqueId: String) {
            messagesForReplay.remove(uniqueId)
        }

        inline fun <reified T> assertSingleReplayMessage(dest: LinkManagerNetworkMap.HoldingIdentity) {
            assertEquals(1, messagesForReplay.size)
            val messageReplay = messagesForReplay.entries.single().value
            assertTrue(messageReplay.message is T)
            assertEquals(dest, messageReplay.dest)
        }

    }

    private val globalNetMap = MockNetworkMap(listOf(OUTBOUND_PARTY, INBOUND_PARTY))
    private val netMapOutbound = globalNetMap.getSessionNetworkMapForNode(OUTBOUND_PARTY)
    private val netMapInbound = globalNetMap.getSessionNetworkMapForNode(INBOUND_PARTY)

    private val payload = ByteBuffer.wrap("Hi inbound it's outbound here".toByteArray())

    private val wrappedMessage = AuthenticatedMessageAndKey(
        AuthenticatedMessage(
            AuthenticatedMessageHeader(
                INBOUND_PARTY.toHoldingIdentity(),
                OUTBOUND_PARTY.toHoldingIdentity(),
                null,
                "messageId",
                "", "system-1"
            ),
            payload
        ),
        KEY
    )

    private fun sessionManager(
        party: LinkManagerNetworkMap.HoldingIdentity,
        queues: MockSessionMessageQueues = MockSessionMessageQueues(),
        mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY,
        cryptoService: LinkManagerCryptoService? = null,
        messageReplayer: SessionReplayer = noReplayer
    ) : SessionManagerImpl {
        val netMap = globalNetMap.getSessionNetworkMapForNode(party)
        val realCryptoService = cryptoService ?: MockCryptoService(netMap)
        return SessionManagerImpl(
            SessionManagerImpl.ParametersForSessionNegotiation(MAX_MESSAGE_SIZE, setOf(mode)),
            netMap,
            realCryptoService,
            queues,
            messageReplayer
        )
    }

    private fun negotiateOutboundSession(
        FlowMessageAndKey: AuthenticatedMessageAndKey,
        outboundManager: SessionManager,
        supportedMode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY
    ): Session {
        val state = outboundManager.processOutboundFlowMessage(FlowMessageAndKey)
        assertTrue(state is NewSessionNeeded)
        assertTrue((state as NewSessionNeeded).sessionInitMessage.payload is InitiatorHelloMessage)

        val initiatorHelloMessage = state.sessionInitMessage.payload as InitiatorHelloMessage

        val protocolResponder = AuthenticationProtocolResponder(
            initiatorHelloMessage.header.sessionId, setOf(supportedMode), MAX_MESSAGE_SIZE
        )
        protocolResponder.receiveInitiatorHello(initiatorHelloMessage)

        val initiatorHandshakeMessage = outboundManager.processSessionMessage(LinkInMessage(protocolResponder.generateResponderHello()))

        assertTrue(initiatorHandshakeMessage!!.payload is InitiatorHandshakeMessage)
        protocolResponder.generateHandshakeSecrets()

        protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshakeMessage.payload as InitiatorHandshakeMessage,
            netMapOutbound.getKeyPair().public,
            KeyAlgorithm.ECDSA
        )

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
        val protocolInitiator = AuthenticationProtocolInitiator(
            sessionId, setOf(supportedMode),
            MAX_MESSAGE_SIZE, netMapOutbound.getKeyPair().public, GROUP_ID
        )
        val initiatorHelloMessage = protocolInitiator.generateInitiatorHello()
        val responderHelloMessage = inboundManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))?.payload
        assertTrue(responderHelloMessage is ResponderHelloMessage)
        protocolInitiator.receiveResponderHello(responderHelloMessage as ResponderHelloMessage)
        protocolInitiator.generateHandshakeSecrets()
        val initiatorHandshakeMessage = protocolInitiator.generateOurHandshakeMessage(
            netMapInbound.getKeyPair().public,
        ) { signDataWithKey(netMapOutbound.getKeyPair().private, it) }
        val responderHandshakeMessage = inboundManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage))
        assertTrue(responderHandshakeMessage?.payload is ResponderHandshakeMessage)

        protocolInitiator.validatePeerHandshakeMessage(
            responderHandshakeMessage?.payload as ResponderHandshakeMessage,
            netMapInbound.getKeyPair().public,
            KeyAlgorithm.ECDSA
        )

        return protocolInitiator.getSession()
    }

    private fun negotiateToInitiatorHandshake(
        inboundManager: SessionManager,
        sessionId: String,
        mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY,
    ): InitiatorHandshakeMessage {
        val protocolInitiator = AuthenticationProtocolInitiator(
            sessionId, setOf(mode), MAX_MESSAGE_SIZE, netMapOutbound.getKeyPair().public, GROUP_ID
        )
        val initiatorHelloMessage = protocolInitiator.generateInitiatorHello()

        val responderHello = inboundManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))?.payload
        assertTrue(responderHello is ResponderHelloMessage)

        protocolInitiator.receiveResponderHello(responderHello as ResponderHelloMessage)
        protocolInitiator.generateHandshakeSecrets()
        return protocolInitiator.generateOurHandshakeMessage(
            netMapInbound.getKeyPair().public,
        ) {
            signDataWithKey(netMapOutbound.getKeyPair().private, it)
        }
    }

    private fun negotiateToResponderHello(
        outboundManager: SessionManager,
        messageWrapper: AuthenticatedMessageAndKey,
        mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY
    ): ResponderHelloMessage {
        val message = (outboundManager.processOutboundFlowMessage(messageWrapper) as NewSessionNeeded)
            .sessionInitMessage.payload as InitiatorHelloMessage
        val protocolResponder = AuthenticationProtocolResponder(
            message.header.sessionId,
            setOf(mode),
            MAX_MESSAGE_SIZE
        )
        protocolResponder.receiveInitiatorHello(message)
        return protocolResponder.generateResponderHello()
    }

    private fun negotiateToResponderHandshake(
        outboundManager: SessionManager,
        messageWrapper: AuthenticatedMessageAndKey,
        mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY,
    ): ResponderHandshakeMessage {
        val message = (outboundManager.processOutboundFlowMessage(messageWrapper) as NewSessionNeeded)
            .sessionInitMessage.payload as InitiatorHelloMessage
        return negotiateToResponderHandshake(message, outboundManager, mode)
    }

    private fun negotiateToResponderHandshake(
        initiatorHelloMessage: InitiatorHelloMessage,
        outboundManager: SessionManager,
        mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY
    ): ResponderHandshakeMessage {
        val protocolResponder = AuthenticationProtocolResponder(
            initiatorHelloMessage.header.sessionId,
            setOf(mode),
            MAX_MESSAGE_SIZE
        )
        protocolResponder.receiveInitiatorHello(initiatorHelloMessage)
        val responderHello = protocolResponder.generateResponderHello()
        protocolResponder.generateHandshakeSecrets()
        val initiatorHandshakeMessage = outboundManager.processSessionMessage(LinkInMessage(responderHello))
        protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshakeMessage?.payload as InitiatorHandshakeMessage,
            netMapOutbound.getKeyPair().public,
            KeyAlgorithm.ECDSA
        )
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
        val messageReplayer = MockSessionReplayer()
        val outboundSessionManager = sessionManager(OUTBOUND_PARTY, queues, messageReplayer = messageReplayer)
        val responderSession = negotiateOutboundSession(wrappedMessage, outboundSessionManager)

        assertTrue(responderSession is AuthenticatedSession)
        assertEquals(queues.processedMessageQueue.size, 1)

        val messageFromQueue = queues.processedMessageQueue[0]
        assertTrue(messageFromQueue.payload is AuthenticatedDataMessage)
        val authenticatedDataMessage = (messageFromQueue.payload as AuthenticatedDataMessage)

        val responderMessage = extractPayload(
            responderSession, "",
            DataMessage.Authenticated(authenticatedDataMessage), AuthenticatedMessageAndKey::fromByteBuffer
        )

        assertNotNull(responderMessage)
        assertEquals(wrappedMessage.message.payload, responderMessage!!.message.payload)
        assertEquals(0, messageReplayer.messagesForReplay.size)
    }

    @Test
    fun `A session can be negotiated by a SessionManager and a message can be sent and decrypted (in AUTHENTICATED_ENCRYPTION mode)`() {
        val queues = MockSessionMessageQueues()
        val outboundSessionManager = sessionManager(OUTBOUND_PARTY, queues, ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responderSession = negotiateOutboundSession(wrappedMessage, outboundSessionManager, ProtocolMode.AUTHENTICATED_ENCRYPTION)

        assertTrue(responderSession is AuthenticatedEncryptionSession)
        assertEquals(queues.processedMessageQueue.size, 1)

        val messageFromQueue = queues.processedMessageQueue[0]
        assertTrue(messageFromQueue.payload is AuthenticatedEncryptedDataMessage)
        val authenticatedDataMessage = (messageFromQueue.payload as AuthenticatedEncryptedDataMessage)

        val responderMessage = extractPayload(
            responderSession, "",
            DataMessage.AuthenticatedAndEncrypted(authenticatedDataMessage),
            AuthenticatedMessageAndKey::fromByteBuffer
        )
        assertNotNull(responderMessage)

        assertEquals(wrappedMessage.message.payload, responderMessage!!.message.payload)
    }

    @Test
    fun `A session can be negotiated with a SessionManager and a message can be received (in AUTHENTICATION_ONLY mode)`() {
        val sessionId = "Session"
        val inboundSessionManager = sessionManager(INBOUND_PARTY)
        val initiatorSession = negotiateInboundSession(sessionId, inboundSessionManager)

        assertTrue(initiatorSession is AuthenticatedSession)

        val authenticatedMessage = linkOutMessageFromFlowMessageAndKey(wrappedMessage, initiatorSession, netMapInbound)
        assertTrue(authenticatedMessage!!.payload is AuthenticatedDataMessage)
        val sessionDirection = inboundSessionManager.getSessionById(sessionId)

        assertTrue(sessionDirection is SessionManager.SessionDirection.Inbound)
        val inboundSession = (sessionDirection as SessionManager.SessionDirection.Inbound).session
        assertTrue(inboundSession is AuthenticatedSession)

        val responderMessage = extractPayload(
            inboundSession,
            "",
            DataMessage.Authenticated(authenticatedMessage.payload as AuthenticatedDataMessage),
            AuthenticatedMessageAndKey::fromByteBuffer
        )
        assertNotNull(responderMessage)

        assertEquals(wrappedMessage.message.payload, responderMessage!!.message.payload)
    }

    @Test
    fun `A session can be negotiated with a SessionManager and a message can be received (in AUTHENTICATED_ENCRYPTION mode)`() {
        val sessionId = "Session"
        val inboundSessionManager = sessionManager(INBOUND_PARTY, mode = ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val initiatorSession = negotiateInboundSession(sessionId, inboundSessionManager, ProtocolMode.AUTHENTICATED_ENCRYPTION)

        assertTrue(initiatorSession is AuthenticatedEncryptionSession)

        val authenticatedEncryptedMessage = linkOutMessageFromFlowMessageAndKey(wrappedMessage, initiatorSession, netMapInbound)
        assertTrue(authenticatedEncryptedMessage!!.payload is AuthenticatedEncryptedDataMessage)
        val sessionDirection = inboundSessionManager.getSessionById(sessionId)
        assertTrue(sessionDirection is SessionManager.SessionDirection.Inbound)
        val inboundSession = (sessionDirection as SessionManager.SessionDirection.Inbound).session

        assertTrue(inboundSession is AuthenticatedEncryptionSession)

        val responderMessage = extractPayload(
            inboundSession,
            "",
            DataMessage.AuthenticatedAndEncrypted(authenticatedEncryptedMessage.payload as AuthenticatedEncryptedDataMessage),
            AuthenticatedMessageAndKey::fromByteBuffer
        )

        assertEquals(wrappedMessage.message.payload, responderMessage!!.message.payload)
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

        val mockMessages = listOf(
            mockResponderHelloMessage,
            mockResponderHandshakeMessage,
            mockInitiatorHandshakeMessage
        )

        for (mockMessage in mockMessages) {
            assertNull(inboundManager.processSessionMessage(LinkInMessage(mockMessage)))
            loggingInterceptor.assertSingleWarning(
                "Received ${mockMessage::class.java.simpleName} with sessionId" +
                    " $fakeSession but there is no pending session with this id. The message was discarded."
            )
            loggingInterceptor.reset()
        }
    }

    @Test
    fun `Duplicated session negotiation messages (ResponderHello and ResponderHandshake) are dropped (with appropriate logging)`() {
        val mode = ProtocolMode.AUTHENTICATION_ONLY
        val outboundManager = sessionManager(OUTBOUND_PARTY)
        val state = outboundManager.processOutboundFlowMessage(wrappedMessage)

        assertTrue(state is NewSessionNeeded)
        val initiatorHelloMessage = (state as NewSessionNeeded).sessionInitMessage.payload as InitiatorHelloMessage
        val sessionId = initiatorHelloMessage.header.sessionId

        val protocolResponder = AuthenticationProtocolResponder(sessionId, setOf(mode), MAX_MESSAGE_SIZE)
        protocolResponder.receiveInitiatorHello(initiatorHelloMessage)

        val responderHelloMessage = LinkInMessage(protocolResponder.generateResponderHello())
        val initiatorHandshakeMessage = outboundManager.processSessionMessage(responderHelloMessage)

        // Duplicate Responder Hello message (same initiator handshake returned idempotently).
        assertThat(outboundManager.processSessionMessage(responderHelloMessage)).isEqualTo(initiatorHandshakeMessage)
        assertTrue(initiatorHandshakeMessage!!.payload is InitiatorHandshakeMessage)

        protocolResponder.generateHandshakeSecrets()
        protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshakeMessage.payload as InitiatorHandshakeMessage,
            netMapOutbound.getKeyPair().public,
            KeyAlgorithm.ECDSA
        )

        val responderHandshakeMessage = protocolResponder.generateOurHandshakeMessage(netMapInbound.getKeyPair().public) {
            signDataWithKey(netMapInbound.getKeyPair().private, it)
        }

        assertNull(outboundManager.processSessionMessage(LinkInMessage(responderHandshakeMessage)))
        // Duplicate ResponderHandshakeMessage
        assertNull(outboundManager.processSessionMessage(LinkInMessage(responderHandshakeMessage)))
        loggingInterceptor.assertSingleWarning(
            "Received ${ResponderHandshakeMessage::class.java.simpleName} with sessionId " +
                "$sessionId but there is no pending session with this id. The message was discarded."
        )
    }

    @Test
    fun `Duplicated session negotiation messages (InitiatorHelloMessage, InitiatorHandshake) cause a duplicated response`() {
        val mode = ProtocolMode.AUTHENTICATION_ONLY
        val sessionId = "FakeSession"
        val inboundManager = sessionManager(INBOUND_PARTY)

        val protocolInitiator = AuthenticationProtocolInitiator(
            sessionId, setOf(mode), MAX_MESSAGE_SIZE,
            netMapOutbound.getKeyPair().public, GROUP_ID
        )
        val initiatorHelloMessage = protocolInitiator.generateInitiatorHello()
        val responderHelloMessage = inboundManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))
        assertTrue(responderHelloMessage!!.payload is ResponderHelloMessage)
        inboundManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))
        //Duplicate InitiatorHandshakeMessage
        assertSame(responderHelloMessage.payload, inboundManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))!!.payload)

        protocolInitiator.receiveResponderHello(responderHelloMessage.payload as ResponderHelloMessage)
        protocolInitiator.generateHandshakeSecrets()
        val initiatorHandshakeMessage = protocolInitiator.generateOurHandshakeMessage(
            netMapInbound.getKeyPair().public,
        ) { signDataWithKey(netMapOutbound.getKeyPair().private, it) }

        val responderHandshakeMessage = inboundManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage))
        assertTrue(responderHandshakeMessage?.payload is ResponderHandshakeMessage)

        //Duplicate InitiatorHandshakeMessage
        assertSame(
            responderHandshakeMessage?.payload,
            inboundManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage))!!.payload
        )

        inboundManager.inboundSessionEstablished(sessionId)
        assertNull(inboundManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage)))
    }

    @Test
    fun `Reordered duplicated session negotiation messages (InitiatorHelloMessage, InitiatorHandshake) cause the correct response`() {
        val mode = ProtocolMode.AUTHENTICATION_ONLY
        val sessionId = "FakeSession"
        val inboundManager = sessionManager(INBOUND_PARTY)

        val protocolInitiator = AuthenticationProtocolInitiator(
            sessionId,
            setOf(mode),
            MAX_MESSAGE_SIZE,
            netMapOutbound.getKeyPair().public,
            GROUP_ID
        )
        val initiatorHelloMessage = protocolInitiator.generateInitiatorHello()
        val responderHelloMessage = inboundManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))
        assertTrue(responderHelloMessage!!.payload is ResponderHelloMessage)
        inboundManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        protocolInitiator.receiveResponderHello(responderHelloMessage.payload as ResponderHelloMessage)
        protocolInitiator.generateHandshakeSecrets()
        val initiatorHandshakeMessage = protocolInitiator.generateOurHandshakeMessage(
            netMapInbound.getKeyPair().public,
        ) { signDataWithKey(netMapOutbound.getKeyPair().private, it) }

        val responderHandshakeMessage = inboundManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage))
        assertTrue(responderHandshakeMessage?.payload is ResponderHandshakeMessage)

        // Same message returned (idempotently)
        assertThat(inboundManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))).isEqualTo(responderHelloMessage)
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

        inboundManager.processSessionMessage(LinkInMessage(mockInitiatorHandshakeMessage))
        loggingInterceptor.assertSingleWarning(
            "Received ${mockInitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId," +
                " which failed validation with: The handshake message was invalid. The message was discarded."
        )
    }

    @Test
    fun `ResponderHandshakeMessage is dropped (with appropriate logging) if authentication fails`() {
        val messageReplayer = MockSessionReplayer()
        val outboundManager = sessionManager(OUTBOUND_PARTY, messageReplayer = messageReplayer)
        val sessionId = negotiateToResponderHandshake(outboundManager, wrappedMessage).header.sessionId

        val mockHeader = Mockito.mock(CommonHeader::class.java)
        Mockito.`when`(mockHeader.sessionId).thenReturn(sessionId)
        Mockito.`when`(mockHeader.toByteBuffer()).thenReturn(ByteBuffer.wrap("HEADER".toByteArray()))

        val mockResponderHandshakeMessage = Mockito.mock(ResponderHandshakeMessage::class.java)
        Mockito.`when`(mockResponderHandshakeMessage.header).thenReturn(mockHeader)
        Mockito.`when`(mockResponderHandshakeMessage.authTag).thenReturn(ByteBuffer.wrap("AuthTag".toByteArray()))
        Mockito.`when`(mockResponderHandshakeMessage.encryptedData).thenReturn(ByteBuffer.wrap("EncryptedData".toByteArray()))

        assertNull(outboundManager.processSessionMessage(LinkInMessage(mockResponderHandshakeMessage)))
        loggingInterceptor.assertSingleWarning(
            "Received ${mockResponderHandshakeMessage::class.java.simpleName} with sessionId $sessionId," +
                " which failed validation with: The handshake message was invalid. The message was discarded."
        )

        //The ResponderHandshakeMessage acts as ack for InitiatorHandshake, so this should be queued for replay
        messageReplayer.assertSingleReplayMessage<InitiatorHandshakeMessage>(INBOUND_PARTY)
    }

    @Test
    fun `Cannot generate a session init message if group id is not in the network map`() {
        val groupId = "NEW_GROUP"
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getNetworkType(groupId)).thenReturn(null)

        val outboundSessionManager = sessionManagerWithNetMap(netMap)

        val message = AuthenticatedMessageAndKey(
            AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    OUTBOUND_PARTY.toHoldingIdentity(),
                    HoldingIdentity(OUTBOUND_PARTY.x500Name, groupId),
                    null,
                    "messageId",
                    "",
                    "system-1"
                ),
                payload
            ),
            KEY
        )

        val state = outboundSessionManager.processOutboundFlowMessage(message)

        assertTrue(state is SessionManager.SessionState.CannotEstablishSession)
        loggingInterceptor.assertSingleWarning(
            "Could not find the network type in the NetworkMap for groupId $groupId." +
                " The sessionInit message was not sent."
        )
    }

    @Test
    fun `Cannot generate a session init message from a party not in the network map`() {
        val outboundSessionManager = sessionManager(OUTBOUND_PARTY)

        val message = AuthenticatedMessageAndKey(
            AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    OUTBOUND_PARTY.toHoldingIdentity(),
                    PARTY_NOT_IN_NETMAP.toHoldingIdentity(),
                    null,
                    "messageId",
                    "",
                    "system-1"
                ),
                payload
            ),
            KEY
        )

        val state = outboundSessionManager.processOutboundFlowMessage(message)

        assertTrue(state is SessionManager.SessionState.CannotEstablishSession)
        loggingInterceptor.assertSingleWarning(
            "Attempted to start session negotiation with peer $OUTBOUND_PARTY but our identity " +
                "$PARTY_NOT_IN_NETMAP is not in the network map. The sessionInit message was not sent."
        )
    }

    @Test
    fun `Cannot generate a session init message for a party not in the network map`() {
        val outboundSessionManager = sessionManager(OUTBOUND_PARTY)

        val message = AuthenticatedMessageAndKey(
            AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    PARTY_NOT_IN_NETMAP.toHoldingIdentity(),
                    OUTBOUND_PARTY.toHoldingIdentity(),
                    null,
                    "messageId",
                    "", "system-1"
                ),
                payload
            ),
            KEY
        )

        val state = outboundSessionManager.processOutboundFlowMessage(message)

        assertTrue(state is SessionManager.SessionState.CannotEstablishSession)
        loggingInterceptor.assertSingleWarning(
            "Attempted to start session negotiation with peer $PARTY_NOT_IN_NETMAP which is not in" +
                " the network map. The sessionInit message was not sent."
        )
    }

    @Test
    fun `Responder hello message is dropped if we are not in the network map`() {
        val messageReplayer = MockSessionReplayer()

        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)
        val outboundInfo = netMapOutbound.getOurMemberInfo()
        Mockito.`when`(netMap.getMemberInfo(INBOUND_PARTY)).thenReturn(netMapInbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(OUTBOUND_PARTY)).thenReturn(netMapOutbound.getOurMemberInfo()).thenReturn(null)

        val outboundManager = sessionManagerWithNetMap(netMap, messageReplayer = messageReplayer)
        val responderHello = negotiateToResponderHello(outboundManager, wrappedMessage)
        assertNull(outboundManager.processSessionMessage(LinkInMessage(responderHello)))

        loggingInterceptor.assertSingleWarning(
            "Received ${ResponderHelloMessage::class.java.simpleName} with sessionId" +
                " ${responderHello.header.sessionId} but cannot find public key for our identity ${outboundInfo.holdingIdentity}." +
                " The message was discarded."
        )

        //The ResponderHelloMessage acts as ack for InitiatorHello, so this should be queued for replay
        messageReplayer.assertSingleReplayMessage<InitiatorHelloMessage>(INBOUND_PARTY)
    }

    @Test
    fun `Responder hello message is dropped if the receiver is not in the network map`() {
        val messageReplayer = MockSessionReplayer()

        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)
        Mockito.`when`(netMap.getMemberInfo(OUTBOUND_PARTY)).thenReturn(netMapOutbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(INBOUND_PARTY)).thenReturn(netMapInbound.getOurMemberInfo()).thenReturn(null)

        val outboundManager = sessionManagerWithNetMap(netMap, messageReplayer = messageReplayer)
        val responderHello = negotiateToResponderHello(outboundManager, wrappedMessage)
        assertNull(outboundManager.processSessionMessage(LinkInMessage(responderHello)))

        loggingInterceptor.assertSingleWarning(
            "Received ResponderHelloMessage with sessionId ${responderHello.header.sessionId}" +
                " from peer $INBOUND_PARTY which is not in the network map. The message was discarded."
        )
        //The ResponderHelloMessage acts as ack for InitiatorHello, so this should be queued for replay
        messageReplayer.assertSingleReplayMessage<InitiatorHelloMessage>(INBOUND_PARTY)
    }

    @Test
    fun `Responder hello message is dropped if we are removed from the network map during processSessionMessage`() {
        val messageReplayer = MockSessionReplayer()

        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)
        Mockito.`when`(netMap.getMemberInfo(OUTBOUND_PARTY)).thenReturn(netMapOutbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(INBOUND_PARTY)).thenReturn(netMapInbound.getOurMemberInfo())

        val key = netMapInbound.getKeyPair().public
        val cryptoService = Mockito.mock(LinkManagerCryptoService::class.java)
        Mockito.`when`(cryptoService.signData(any(), any())).thenThrow(
            LinkManagerCryptoService.NoPrivateKeyForGroupException(key)
        )

        val outboundManager = sessionManagerWithNetMap(netMap, cryptoService, messageReplayer)
        val responderHello = negotiateToResponderHello(outboundManager, wrappedMessage)
        assertNull(outboundManager.processSessionMessage(LinkInMessage(responderHello)))

        loggingInterceptor.assertSingleWarning(
            "Could not find the private key corresponding to public key" +
                " $key. The ResponderHelloMessage with sessionId ${responderHello.header.sessionId} was discarded."
        )

        //The ResponderHelloMessage acts as ack for InitiatorHello, so this should be queued for replay
        messageReplayer.assertSingleReplayMessage<InitiatorHelloMessage>(INBOUND_PARTY)
    }

    @Test
    fun `Responder hello message is dropped if our network type is not in the network map`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5).thenReturn(null)
        Mockito.`when`(netMap.getMemberInfo(OUTBOUND_PARTY)).thenReturn(netMapOutbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(INBOUND_PARTY)).thenReturn(netMapInbound.getOurMemberInfo())

        val outboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapOutbound))
        val responderHello = negotiateToResponderHello(outboundManager, wrappedMessage)
        assertNull(outboundManager.processSessionMessage(LinkInMessage(responderHello)))

        loggingInterceptor.assertSingleWarning(
            "Could not find the network type in the NetworkMap for groupId $GROUP_ID. The " +
                "ResponderHelloMessage for sessionId ${responderHello.header.sessionId} was discarded."
        )
    }

    @Test
    fun `Initiator hello message is dropped if peer is not in the network map`() {
        val sessionId = "SessionId"

        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        val inboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapInbound))

        val protocolInitiator = AuthenticationProtocolInitiator(
            sessionId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            netMapOutbound.getKeyPair().public,
            GROUP_ID
        )
        val initiatorHelloMessage = protocolInitiator.generateInitiatorHello()
        inboundManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))?.payload

        loggingInterceptor.assertSingleWarning(
            "Received InitiatorHelloMessage with sessionId SessionId. The received public key hash" +
                " (${initiatorHelloMessage.source.initiatorPublicKeyHash.array().toBase64()}) " +
                "corresponding to one of the sender's holding " +
                "identities is not in the network map. The message was discarded."
        )
    }

    @Test
    fun `Initiator hello message is dropped if our network type is not in the network map`() {
        val sessionId = "SessionId"

        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapOutbound.getKeyPair().public), GROUP_ID))
            .thenReturn(netMapOutbound.getOurMemberInfo())

        val inboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapInbound))

        val protocolInitiator = AuthenticationProtocolInitiator(
            sessionId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            netMapOutbound.getKeyPair().public,
            GROUP_ID
        )
        val initiatorHelloMessage = protocolInitiator.generateInitiatorHello()
        inboundManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))?.payload

        loggingInterceptor.assertSingleWarning(
            "Could not find the network type in the NetworkMap for groupId $GROUP_ID. " +
                "The InitiatorHelloMessage for sessionId SessionId was discarded."
        )
    }

    @Test
    fun `Initiator handshake message is dropped if the sender public key hash is not in the network map`() {
        val sessionId = "SessionId"

        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)
        Mockito.`when`(netMap.getMemberInfo(OUTBOUND_PARTY)).thenReturn(netMapOutbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(INBOUND_PARTY)).thenReturn(netMapInbound.getOurMemberInfo())

        // Called first inside processInitiatorHello and then in processInitiatorHandshake
        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapOutbound.getKeyPair().public), GROUP_ID))
            .thenReturn(netMapOutbound.getOurMemberInfo()).thenReturn(null)

        val inboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapInbound))
        val message = negotiateToInitiatorHandshake(inboundManager, sessionId)
        val response = inboundManager.processSessionMessage(LinkInMessage(message))

        assertNull(response)

        val keyHash = hashKeyToBase64(netMapOutbound.getKeyPair().public)
        loggingInterceptor.assertSingleWarning(
            "Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId." +
                " The received public key hash ($keyHash) corresponding to one of the sender's holding " +
                "identities is not in the network map." +
                " The message was discarded."
        )
    }

    @Test
    fun `Initiator handshake message is dropped if the receiver public key hash is not in the network map`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        val sessionId = "SessionId"

        Mockito.`when`(netMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)
        Mockito.`when`(netMap.getMemberInfo(OUTBOUND_PARTY)).thenReturn(netMapOutbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(INBOUND_PARTY)).thenReturn(netMapInbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapInbound.getKeyPair().public), GROUP_ID)).thenReturn(null)
        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapOutbound.getKeyPair().public), GROUP_ID))
            .thenReturn(netMapOutbound.getOurMemberInfo())

        val inboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapInbound))
        val message = negotiateToInitiatorHandshake(inboundManager, sessionId)
        val response = inboundManager.processSessionMessage(LinkInMessage(message))
        assertNull(response)

        val keyHash = hashKeyToBase64(netMapInbound.getKeyPair().public)
        loggingInterceptor.assertSingleWarning(
            "Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId." +
                " The received public key hash ($keyHash) corresponding to one of our holding identities is not in the network map." +
                " The message was discarded."
        )
    }

    @Test
    fun `Initiator handshake message is dropped if the receiver is removed from the network map`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        val sessionId = "SessionId"

        Mockito.`when`(netMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)
        Mockito.`when`(netMap.getMemberInfo(OUTBOUND_PARTY)).thenReturn(netMapOutbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(INBOUND_PARTY)).thenReturn(netMapInbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapInbound.getKeyPair().public), GROUP_ID))
            .thenReturn(netMapInbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapOutbound.getKeyPair().public), GROUP_ID))
            .thenReturn(netMapOutbound.getOurMemberInfo())

        val cryptoService = Mockito.mock(LinkManagerCryptoService::class.java)
        Mockito.`when`(cryptoService.signData(any(), any())).thenThrow(
            LinkManagerCryptoService.NoPrivateKeyForGroupException(netMapOutbound.getKeyPair().public)
        )
        val inboundManager = sessionManagerWithNetMap(netMap, cryptoService)

        val message = negotiateToInitiatorHandshake(inboundManager, sessionId)
        val response = inboundManager.processSessionMessage(LinkInMessage(message))
        assertNull(response)

        loggingInterceptor.assertSingleWarning(
            "Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId." +
                " Could not find the private key corresponding to public key ${netMapOutbound.getKeyPair().public}. The message was" +
                " discarded."
        )
    }

    @Test
    fun `Initiator handshake message is dropped if our network type is not in the network map`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        val sessionId = "SessionId"

        Mockito.`when`(netMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5).thenReturn(null)
        Mockito.`when`(netMap.getMemberInfo(OUTBOUND_PARTY)).thenReturn(netMapOutbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(INBOUND_PARTY)).thenReturn(netMapInbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapInbound.getKeyPair().public), GROUP_ID))
            .thenReturn(netMapInbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapOutbound.getKeyPair().public), GROUP_ID))
            .thenReturn(netMapOutbound.getOurMemberInfo())
        val inboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapInbound))

        val message = negotiateToInitiatorHandshake(inboundManager, sessionId)
        val response = inboundManager.processSessionMessage(LinkInMessage(message))
        assertNull(response)

        loggingInterceptor.assertSingleWarning(
            "Could not find the network type in the NetworkMap for groupId" +
                " $GROUP_ID. The InitiatorHandshakeMessage for sessionId $sessionId was discarded."
        )
    }

    @Test
    fun `Replayed Initiator handshake message does not cause a response if the sender public key hash is not in the network map`() {
        val sessionId = "SessionId"

        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)
        Mockito.`when`(netMap.getMemberInfo(OUTBOUND_PARTY)).thenReturn(netMapOutbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(INBOUND_PARTY)).thenReturn(netMapInbound.getOurMemberInfo())

        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapInbound.getKeyPair().public), GROUP_ID))
            .thenReturn(netMapInbound.getOurMemberInfo())

        //Called first inside processInitiatorHello, then makeResponderHandshake and then resendResponderHandshake
        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapOutbound.getKeyPair().public), GROUP_ID))
            .thenReturn(netMapOutbound.getOurMemberInfo())
            .thenReturn(netMapOutbound.getOurMemberInfo())
            .thenReturn(null)

        val inboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapInbound))
        val message = negotiateToInitiatorHandshake(inboundManager, sessionId)
        val response = inboundManager.processSessionMessage(LinkInMessage(message))
        assertTrue(response?.payload is ResponderHandshakeMessage)
        val secondResponse = inboundManager.processSessionMessage(LinkInMessage(message))
        assertNull(secondResponse)

        val keyHash = hashKeyToBase64(netMapOutbound.getKeyPair().public)
        loggingInterceptor.assertSingleWarning("Received InitiatorHandshakeMessage with sessionId SessionId. The received public key hash ($keyHash) corresponding to one of the sender's holding" +
            " identities is not in the network map. The message was discarded.")
    }

    @Test
    fun `Replayed Initiator handshake message does not cause a response if the receiver public key hash is not in the network map`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        val sessionId = "SessionId"

        Mockito.`when`(netMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)
        Mockito.`when`(netMap.getMemberInfo(OUTBOUND_PARTY)).thenReturn(netMapOutbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(INBOUND_PARTY)).thenReturn(netMapInbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapInbound.getKeyPair().public), GROUP_ID))
            .thenReturn(netMapInbound.getOurMemberInfo())
            .thenReturn(null)
        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapOutbound.getKeyPair().public), GROUP_ID))
            .thenReturn(netMapOutbound.getOurMemberInfo())

        val inboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapInbound))
        val message = negotiateToInitiatorHandshake(inboundManager, sessionId)
        val response = inboundManager.processSessionMessage(LinkInMessage(message))
        assertTrue(response?.payload is ResponderHandshakeMessage)
        val secondResponse = inboundManager.processSessionMessage(LinkInMessage(message))
        assertNull(secondResponse)

        val keyHash = hashKeyToBase64(netMapInbound.getKeyPair().public)
        loggingInterceptor.assertSingleWarning("Received InitiatorHandshakeMessage with sessionId SessionId. " +
            "The received public key hash ($keyHash) corresponding to one of our holding" +
            " identities is not in the network map. The message was discarded.")
    }

    @Test
    fun `Replayed Initiator handshake does not cause a response if our network type is not in the network map`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        val sessionId = "SessionId"

        Mockito.`when`(netMap.getNetworkType(any()))
            .thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)
            .thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)
            .thenReturn(null)
        Mockito.`when`(netMap.getMemberInfo(OUTBOUND_PARTY)).thenReturn(netMapOutbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(INBOUND_PARTY)).thenReturn(netMapInbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapInbound.getKeyPair().public), GROUP_ID))
            .thenReturn(netMapInbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapOutbound.getKeyPair().public), GROUP_ID))
            .thenReturn(netMapOutbound.getOurMemberInfo())
        val inboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapInbound))

        val message = negotiateToInitiatorHandshake(inboundManager, sessionId)
        val response = inboundManager.processSessionMessage(LinkInMessage(message))
        assertTrue(response?.payload is ResponderHandshakeMessage)
        val secondResponse = inboundManager.processSessionMessage(LinkInMessage(message))
        assertNull(secondResponse)

        loggingInterceptor.assertSingleWarning("Could not find the network type in the NetworkMap for groupId myGroup. " +
            "The InitiatorHandshakeMessage for sessionId SessionId was discarded.")
    }

    @Test
    fun `Responder handshake message is dropped if the sender is not in the network map`() {
        val messageReplayer = MockSessionReplayer()

        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)

        Mockito.`when`(netMap.getMemberInfo(OUTBOUND_PARTY)).thenReturn(netMapOutbound.getOurMemberInfo())
        // Called for the first time in `getSessionInitMessage`, the second time in `processResponderHello` and
        // the third in processResponderHandshake.
        Mockito.`when`(netMap.getMemberInfo(INBOUND_PARTY))
            .thenReturn(netMapInbound.getOurMemberInfo())
            .thenReturn(netMapInbound.getOurMemberInfo())
            .thenReturn(null)
        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapInbound.getKeyPair().public), GROUP_ID))
            .thenReturn(netMapInbound.getOurMemberInfo())
        Mockito.`when`(netMap.getMemberInfo(hashKey(netMapOutbound.getKeyPair().public), GROUP_ID))
            .thenReturn(netMapOutbound.getOurMemberInfo())

        val outboundManager = sessionManagerWithNetMap(netMap, MockCryptoService(netMapOutbound), messageReplayer)

        val message = negotiateToResponderHandshake(outboundManager, wrappedMessage)
        val response = outboundManager.processSessionMessage(LinkInMessage(message))
        assertNull(response)

        val sessionId = message.header.sessionId

        loggingInterceptor.assertSingleWarning(
            "Received ${ResponderHandshakeMessage::class.java.simpleName} with sessionId " +
                "$sessionId from peer $INBOUND_PARTY which is not in the network map. The message was discarded."
        )
        messageReplayer.assertSingleReplayMessage<InitiatorHandshakeMessage>(INBOUND_PARTY)
    }
}
