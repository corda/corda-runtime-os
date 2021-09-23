package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.LinkInMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.MessageType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.internal.InitiatorHandshakeIdentity
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.HandshakeIdentityData
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeMessageException
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeResponderKeyHash
import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.crypto.protocol.api.WrongPublicKeyHashException
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.LinkManagerCryptoService
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.delivery.SessionReplayer
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.NewSessionNeeded
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.v5.base.util.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.time.Instant

class SessionManagerTest {

    companion object {
        const val KEY = "KEY"
        const val GROUP_ID = "myGroup"
        const val MAX_MESSAGE_SIZE = 1024 * 1024
        val PROTOCOL_MODES = listOf(ProtocolMode.AUTHENTICATED_ENCRYPTION, ProtocolMode.AUTHENTICATION_ONLY)
        val RANDOM_BYTES = ByteBuffer.wrap("some-random-data".toByteArray())

        val keyGenerator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider())
        val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, BouncyCastleProvider())

        val OUR_PARTY = LinkManagerNetworkMap.HoldingIdentity("Alice", GROUP_ID)
        val OUR_KEY = keyGenerator.genKeyPair()
        val OUR_MEMBER_INFO = LinkManagerNetworkMap.MemberInfo(OUR_PARTY, OUR_KEY.public, KeyAlgorithm.ECDSA,
            LinkManagerNetworkMap.EndPoint("http://alice.com"))
        val PEER_PARTY = LinkManagerNetworkMap.HoldingIdentity("Bob", GROUP_ID)
        val PEER_KEY = keyGenerator.genKeyPair()
        val PEER_MEMBER_INFO = LinkManagerNetworkMap.MemberInfo(PEER_PARTY, PEER_KEY.public, KeyAlgorithm.ECDSA,
            LinkManagerNetworkMap.EndPoint("http://bob.com"))

        lateinit var loggingInterceptor: LoggingInterceptor

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

    private val networkMap = mock<LinkManagerNetworkMap> {
        on { getNetworkType(GROUP_ID) } doReturn LinkManagerNetworkMap.NetworkType.CORDA_5
        on { getMemberInfo(OUR_PARTY) } doReturn OUR_MEMBER_INFO
        on { getMemberInfo(messageDigest.hash(OUR_KEY.public.encoded), GROUP_ID) } doReturn OUR_MEMBER_INFO
        on { getMemberInfo(PEER_PARTY) } doReturn PEER_MEMBER_INFO
        on { getMemberInfo(messageDigest.hash(PEER_KEY.public.encoded), GROUP_ID) } doReturn PEER_MEMBER_INFO
    }
    private val cryptoService = mock<LinkManagerCryptoService> {
        on { signData(eq(OUR_KEY.public), any()) } doReturn "signature-from-A".toByteArray()
    }
    private val pendingSessionMessageQueues = Mockito.mock(LinkManager.PendingSessionMessageQueues::class.java)
    private val sessionReplayer = Mockito.mock(SessionReplayer::class.java)
    private val protocolInitiator = mock<AuthenticationProtocolInitiator>()
    private val protocolResponder = mock<AuthenticationProtocolResponder>()
    private val protocolFactory = mock<ProtocolFactory> {
        on { createInitiator(any(), any(), any(), any(), any()) } doReturn protocolInitiator
        on { createResponder(any(), any(), any()) } doReturn protocolResponder
    }
    private val parameters =
        SessionManagerImpl.ParametersForSessionNegotiation(MAX_MESSAGE_SIZE, setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION))
    private val sessionManager =
        SessionManagerImpl(parameters, networkMap, cryptoService, pendingSessionMessageQueues, sessionReplayer, protocolFactory)

    private fun MessageDigest.hash(data: ByteArray): ByteArray {
        this.reset()
        this.update(data)
        return digest()
    }

    private val payload = ByteBuffer.wrap("Hi inbound it's outbound here".toByteArray())

    private val message = AuthenticatedMessageAndKey(
        AuthenticatedMessage(
            AuthenticatedMessageHeader(
                PEER_PARTY.toHoldingIdentity(),
                OUR_PARTY.toHoldingIdentity(),
                null,
                "messageId",
                "", "system-1"
            ),
            payload
        ),
        KEY
    )

    @Test
    fun `when no session exists, processing outbound message creates a new session`() {
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded
        assertThat(sessionState.sessionInitMessage.payload).isEqualTo(initiatorHello)
        val replayedMessage = SessionReplayer.SessionMessageReplay(initiatorHello, PEER_PARTY)
        verify(sessionReplayer).addMessageForReplay("${sessionState.sessionId}_${initiatorHello::class.java.simpleName}", replayedMessage)
    }

    @Test
    fun `when no session exists, if network type is missing from network map no message is sent`() {
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        whenever(networkMap.getNetworkType(GROUP_ID)).thenReturn(null)

        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.CannotEstablishSession::class.java)
        verify(sessionReplayer, never()).addMessageForReplay(any(), any())
        loggingInterceptor.assertSingleWarning("Could not find the network type in the NetworkMap for groupId $GROUP_ID." +
                " The sessionInit message was not sent.")
    }

    @Test
    fun `when no session exists, if source member info is missing from network map no message is sent`() {
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        whenever(networkMap.getMemberInfo(OUR_PARTY)).thenReturn(null)

        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.CannotEstablishSession::class.java)
        verify(sessionReplayer, never()).addMessageForReplay(any(), any())
        loggingInterceptor.assertSingleWarning("Attempted to start session negotiation with peer $PEER_PARTY " +
                "but our identity $OUR_PARTY is not in the network map. The sessionInit message was not sent.")
    }

    @Test
    fun `when no session exists, if destination member info is missing from network map no message is sent`() {
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        whenever(networkMap.getMemberInfo(PEER_PARTY)).thenReturn(null)

        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.CannotEstablishSession::class.java)
        verify(sessionReplayer).addMessageForReplay(any(), eq(SessionReplayer.SessionMessageReplay(initiatorHello, PEER_PARTY)))
        loggingInterceptor.assertSingleWarning("Attempted to start session negotiation with peer $PEER_PARTY " +
                "which is not in the network map. The sessionInit message was not sent.")
    }

    @Test
    fun `when messages already queued for a peer, there is already a pending session`() {
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(false)
        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.SessionAlreadyPending::class.java)
        verify(pendingSessionMessageQueues).queueMessage(message, SessionManager.SessionKey(OUR_PARTY, PEER_PARTY))
    }

    @Test
    fun `when session is established with a peer, it is returned when processing a new message for the same peer`() {
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        val session = mock<Session>()
        whenever(protocolInitiator.getSession()).thenReturn(session)
        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()

        val newSessionState = sessionManager.processOutboundMessage(message)
        assertThat(newSessionState).isInstanceOfSatisfying(SessionManager.SessionState.SessionEstablished::class.java) {
            assertThat(it.session).isEqualTo(session)
        }
        assertThat(sessionManager.getSessionById(sessionState.sessionId))
            .isInstanceOfSatisfying(SessionManager.SessionDirection.Outbound::class.java) {
                assertThat(it.session).isEqualTo(session)
            }
    }

    @Test
    fun `when no session exists for a session id, get session returns nothing`() {
        assertThat(sessionManager.getSessionById("some-session-id")).isInstanceOf(SessionManager.SessionDirection.NoSession::class.java)
    }

    @Test
    fun `when an initiator hello is received, a responder hello is returned`() {
        val sessionId = "some-session-id"
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        val responderHello = mock<ResponderHelloMessage>()
        whenever(protocolResponder.generateResponderHello()).thenReturn(responderHello)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMsg))

        assertThat(responseMessage!!.payload).isInstanceOf(ResponderHelloMessage::class.java)
    }

    @Test
    fun `when an initiator hello is received, but peer's member info is missing from network map, then message is dropped`() {
        val initiatorKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val sessionId = "some-session-id"
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        val responderHello = mock<ResponderHelloMessage>()
        whenever(protocolResponder.generateResponderHello()).thenReturn(responderHello)
        whenever(networkMap.getMemberInfo(initiatorKeyHash, GROUP_ID)).thenReturn(null)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorKeyHash), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMsg))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${InitiatorHelloMessage::class.java.simpleName} with sessionId ${sessionId}. " +
                "The received public key hash (${initiatorKeyHash.toBase64()}) corresponding " +
                "to one of the sender's holding identities is not in the network map. The message was discarded.")
    }

    @Test
    fun `when an initiator hello is received, but network type is missing from network map, then message is dropped`() {
        val initiatorKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val sessionId = "some-session-id"
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        val responderHello = mock<ResponderHelloMessage>()
        whenever(protocolResponder.generateResponderHello()).thenReturn(responderHello)
        whenever(networkMap.getNetworkType(GROUP_ID)).thenReturn(null)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorKeyHash), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMsg))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Could not find the network type in the NetworkMap for groupId $GROUP_ID." +
                " The ${InitiatorHelloMessage::class.java.simpleName} for sessionId $sessionId was discarded.")
    }

    @Test
    fun `when responder hello is received without an existing session, the message is dropped`() {
        val sessionId = "some-session-id"
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${ResponderHelloMessage::class.java.simpleName} with sessionId $sessionId " +
                "but there is no pending session with this id. The message was discarded.")
    }

    @Test
    fun `when responder hello is received with an existing session, an initiator handshake is returned`() {
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), any())).thenReturn(initiatorHandshakeMsg)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage!!.payload).isEqualTo(initiatorHandshakeMsg)
        verify(sessionReplayer).removeMessageFromReplay("${sessionState.sessionId}_${InitiatorHelloMessage::class.java.simpleName}")
        val replayedMessage = SessionReplayer.SessionMessageReplay(initiatorHandshakeMsg, PEER_PARTY)
        verify(sessionReplayer)
            .addMessageForReplay("${sessionState.sessionId}_${InitiatorHandshakeMessage::class.java.simpleName}", replayedMessage)
    }

    @Test
    fun `when responder hello is received, but our member info is missing from network map, message is dropped`() {
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), any())).thenReturn(initiatorHandshakeMsg)
        whenever(networkMap.getMemberInfo(OUR_PARTY)).thenReturn(null)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${ResponderHelloMessage::class.java.simpleName} with sessionId " +
                "${sessionState.sessionId} but cannot find public key for our identity $OUR_PARTY. The message was discarded.")
    }

    @Test
    fun `when responder hello is received, but peer's member info is missing from network map, message is dropped`() {
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), any())).thenReturn(initiatorHandshakeMsg)
        whenever(networkMap.getMemberInfo(PEER_PARTY)).thenReturn(null)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${ResponderHelloMessage::class.java.simpleName} with sessionId " +
                "${sessionState.sessionId} from peer $PEER_PARTY which is not in the network map. The message was discarded.")
    }

    @Test
    fun `when responder hello is received, but private key cannot be found to sign, message is dropped`() {
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), any()))
            .thenThrow(LinkManagerCryptoService.NoPrivateKeyForGroupException(OUR_KEY.public))
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("The ${ResponderHelloMessage::class.java.simpleName} with sessionId " +
                "${sessionState.sessionId} was discarded.")
    }

    @Test
    fun `when responder hello is received, but network type is missing from network map, message is dropped`() {
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), any())).thenReturn(initiatorHandshakeMsg)
        whenever(networkMap.getNetworkType(GROUP_ID)).thenReturn(null)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("Could not find the network type in the NetworkMap for groupId $GROUP_ID." +
                " The ${ResponderHelloMessage::class.java.simpleName} for sessionId ${sessionState.sessionId} was discarded.")
    }

    @Test
    fun `when initiator handshake is received, a responder handshake is returned and session is established`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val responderPublicKeyHash = messageDigest.hash(OUR_KEY.public.encoded)
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshakeMessage = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(initiatorHandshakeMessage, PEER_KEY.public, KeyAlgorithm.ECDSA))
            .thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        val responderHandshakeMsg = mock<ResponderHandshakeMessage>()
        whenever(protocolResponder.generateOurHandshakeMessage(eq(OUR_KEY.public), any())).thenReturn(responderHandshakeMsg)
        val session = mock<Session>()
        whenever(protocolResponder.getSession()).thenReturn(session)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage))

        assertThat(responseMessage!!.payload).isEqualTo(responderHandshakeMsg)
        assertThat(sessionManager.getSessionById(sessionId)).isInstanceOfSatisfying(SessionManager.SessionDirection.Inbound::class.java) {
            assertThat(it.session).isEqualTo(session)
        }
    }

    @Test
    fun `when initiator handshake is received, but no session exists the message is discarded`() {
        val sessionId = "some-session-id"
        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshakeMessage = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId " +
                "$sessionId but there is no pending session with this id. The message was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but peer's member info is missing from network map, message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(networkMap.getMemberInfo(initiatorPublicKeyHash, GROUP_ID)).thenReturn(null)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId " +
                "${sessionId}. The received public key hash (${initiatorPublicKeyHash.toBase64()}) corresponding " +
                "to one of the sender's holding identities is not in the network map. The message was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but validation of the message fails due to invalid hash, the message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(initiatorHandshake, PEER_KEY.public, KeyAlgorithm.ECDSA))
            .thenThrow(WrongPublicKeyHashException(initiatorPublicKeyHash.reversedArray(), initiatorPublicKeyHash))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertErrorContains("The message was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but validation of the message fails due to invalid handshake, the message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(initiatorHandshake, PEER_KEY.public, KeyAlgorithm.ECDSA))
            .thenThrow(InvalidHandshakeMessageException())
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but our member info is missing from the network map, the message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val responderPublicKeyHash = messageDigest.hash(OUR_KEY.public.encoded)
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(initiatorHandshake, PEER_KEY.public, KeyAlgorithm.ECDSA))
            .thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(networkMap.getMemberInfo(responderPublicKeyHash, GROUP_ID)).thenReturn(null)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId " +
                "${sessionId}. The received public key hash (${responderPublicKeyHash.toBase64()}) corresponding " +
                "to one of our holding identities is not in the network map. The message was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but network type is missing from the network map, the message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val responderPublicKeyHash = messageDigest.hash(OUR_KEY.public.encoded)
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(initiatorHandshake, PEER_KEY.public, KeyAlgorithm.ECDSA))
            .thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(networkMap.getNetworkType(GROUP_ID)).thenReturn(null)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("Could not find the network type in the NetworkMap for groupId $GROUP_ID." +
                " The ${InitiatorHandshakeMessage::class.java.simpleName} for sessionId $sessionId was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but our key is not found to sign responder handshake, message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val responderPublicKeyHash = messageDigest.hash(OUR_KEY.public.encoded)
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(initiatorHandshake, PEER_KEY.public, KeyAlgorithm.ECDSA))
            .thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(protocolResponder.generateOurHandshakeMessage(eq(OUR_KEY.public), any()))
            .thenThrow(LinkManagerCryptoService.NoPrivateKeyForGroupException(OUR_KEY.public))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, no message is returned and session is established`() {
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        val session = mock<Session>()
        whenever(protocolInitiator.getSession()).thenReturn(session)
        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()

        assertThat(sessionManager.getSessionById(sessionState.sessionId))
            .isInstanceOfSatisfying(SessionManager.SessionDirection.Outbound::class.java) {
                assertThat(it.session).isEqualTo(session)
            }
        verify(sessionReplayer).removeMessageFromReplay("${sessionState.sessionId}_${InitiatorHandshakeMessage::class.java.simpleName}")
        verify(pendingSessionMessageQueues)
            .sessionNegotiatedCallback(SessionManager.SessionKey(OUR_PARTY, PEER_PARTY), session, networkMap)
    }

    @Test
    fun `when responder handshake is received, but no session exists for that id, the message is dropped`() {
        val sessionId = "some-session-id"
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()

        loggingInterceptor.assertSingleWarningContains("Received ${ResponderHandshakeMessage::class.java.simpleName} with sessionId " +
                "$sessionId but there is no pending session with this id. The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, but peer's member info is missing from network map, the message is dropped`() {
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(networkMap.getMemberInfo(PEER_PARTY)).thenReturn(null)
        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()

        loggingInterceptor.assertSingleWarning("Received ${ResponderHandshakeMessage::class.java.simpleName} with sessionId " +
                "${sessionState.sessionId} from peer $PEER_PARTY which is not in the network map. The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, but validation fails due to invalid key hash, the message is dropped`() {
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolInitiator.validatePeerHandshakeMessage(responderHandshakeMessage, PEER_KEY.public, KeyAlgorithm.ECDSA))
            .thenThrow(InvalidHandshakeResponderKeyHash())
        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()

        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, but validation fails due to invalid handshake, the message is dropped`() {
        whenever(pendingSessionMessageQueues.queueMessage(eq(message), any())).thenReturn(true)
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolInitiator.validatePeerHandshakeMessage(responderHandshakeMessage, PEER_KEY.public, KeyAlgorithm.ECDSA))
            .thenThrow(InvalidHandshakeMessageException())
        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()

        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

}
