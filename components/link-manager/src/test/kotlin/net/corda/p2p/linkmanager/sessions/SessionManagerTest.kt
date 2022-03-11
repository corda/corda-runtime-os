package net.corda.p2p.linkmanager.sessions

import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.DataMessagePayload
import net.corda.p2p.HeartbeatMessage
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.NetworkType
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.MessageType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.internal.InitiatorHandshakeIdentity
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.AuthenticationResult
import net.corda.p2p.crypto.protocol.api.HandshakeIdentityData
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeMessageException
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeResponderKeyHash
import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.crypto.protocol.api.WrongPublicKeyHashException
import net.corda.p2p.linkmanager.GroupPolicyListener
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.LinkManagerGroupPolicyProvider
import net.corda.p2p.linkmanager.LinkManagerHostingMap
import net.corda.p2p.linkmanager.LinkManagerInternalTypes
import net.corda.p2p.linkmanager.LinkManagerMembershipGroupReader
import net.corda.p2p.linkmanager.delivery.InMemorySessionReplayer
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.NewSessionNeeded
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.linkmanager.utilities.MockTimeFacilitiesProvider
import net.corda.p2p.test.stub.crypto.processor.CouldNotFindPrivateKey
import net.corda.p2p.test.stub.crypto.processor.StubCryptoProcessor
import net.corda.p2p.test.stub.crypto.processor.UnsupportedAlgorithm
import net.corda.schema.Schemas.P2P.Companion.LINK_OUT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.SESSION_OUT_PARTITIONS
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.toBase64
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.concurrent.CompletableFuture

class SessionManagerTest {

    companion object {
        const val KEY = "KEY"
        const val GROUP_ID = "myGroup"
        const val MAX_MESSAGE_SIZE = 1024 * 1024
        val PROTOCOL_MODES = listOf(ProtocolMode.AUTHENTICATED_ENCRYPTION, ProtocolMode.AUTHENTICATION_ONLY)
        val RANDOM_BYTES = ByteBuffer.wrap("some-random-data".toByteArray())

        private const val longPeriodMilliSec = 10000000L
        private val configWithHeartbeat = SessionManagerImpl.HeartbeatManager.HeartbeatManagerConfig(
            Duration.ofMillis(100),
            Duration.ofMillis(500)
        )
        private val configNoHeartbeat = SessionManagerImpl.HeartbeatManager.HeartbeatManagerConfig(
            Duration.ofMillis(longPeriodMilliSec),
            Duration.ofMillis(longPeriodMilliSec)
        )

        private val keyGenerator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider())
        private val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, BouncyCastleProvider())

        private val OUR_PARTY = LinkManagerInternalTypes.HoldingIdentity("Alice", GROUP_ID)
        private val OUR_KEY = keyGenerator.genKeyPair()
        private val OUR_MEMBER_INFO = LinkManagerInternalTypes.MemberInfo(OUR_PARTY, OUR_KEY.public, KeyAlgorithm.ECDSA,
            LinkManagerInternalTypes.EndPoint("http://alice.com"))
        private val PEER_PARTY = LinkManagerInternalTypes.HoldingIdentity("Bob", GROUP_ID)
        private val PEER_KEY = keyGenerator.genKeyPair()
        private val PEER_MEMBER_INFO = LinkManagerInternalTypes.MemberInfo(PEER_PARTY, PEER_KEY.public, KeyAlgorithm.ECDSA,
            LinkManagerInternalTypes.EndPoint("http://bob.com"))

        lateinit var loggingInterceptor: LoggingInterceptor

        @BeforeAll
        @JvmStatic
        fun setup() {
            loggingInterceptor = LoggingInterceptor.setupLogging()
        }
    }

    @BeforeEach
    fun startSessionManager() {
        sessionManager.start()
    }

    @AfterEach
    fun cleanUp() {
        dominoTile.close()
        publisherWithDominoLogic.close()
        sessionManager.stop()
        loggingInterceptor.reset()
        resources.close()
    }

    private lateinit var configHandler: SessionManagerImpl.SessionManagerConfigChangeHandler
    private lateinit var heartbeatConfigHandler: SessionManagerImpl.HeartbeatManager.HeartbeatManagerConfigChangeHandler
    private var createResourcesCallbacks = mutableMapOf<String, ((resources: ResourcesHolder) -> CompletableFuture<Unit>)>()
    private val dominoTile = Mockito.mockConstruction(ComplexDominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleWriteLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        if (context.arguments()[5] is SessionManagerImpl.SessionManagerConfigChangeHandler) {
            configHandler = context.arguments()[5] as SessionManagerImpl.SessionManagerConfigChangeHandler
        }
        if (context.arguments()[5] is SessionManagerImpl.HeartbeatManager.HeartbeatManagerConfigChangeHandler) {
            heartbeatConfigHandler = context.arguments()[5] as SessionManagerImpl.HeartbeatManager.HeartbeatManagerConfigChangeHandler
        }
        if (context.arguments()[2] != null) {
            @Suppress("UNCHECKED_CAST")
            createResourcesCallbacks[context.arguments()[0] as String] =
                context.arguments()[2] as ((resources: ResourcesHolder) -> CompletableFuture<Unit>)
        }
    }
    private val publisherWithDominoLogicByClientId = mutableMapOf<String, MutableList<PublisherWithDominoLogic>>()
    private val publisherWithDominoLogic = Mockito.mockConstruction(PublisherWithDominoLogic::class.java) { mock, context ->
        publisherWithDominoLogicByClientId.compute((context.arguments()[2] as PublisherConfig).clientId) { _, map ->
            map?.apply { this.add(mock) } ?: mutableListOf(mock)
        }
    }

    private val groupInfo = mock<GroupPolicyListener.GroupInfo> {
        on { networkType } doReturn NetworkType.CORDA_5
    }
    private val groups = mock<LinkManagerGroupPolicyProvider> {
        on { getGroupInfo(GROUP_ID) } doReturn groupInfo
    }
    private val members = mock<LinkManagerMembershipGroupReader> {
        on { getMemberInfo(OUR_PARTY) } doReturn OUR_MEMBER_INFO
        on { getMemberInfo(messageDigest.hash(OUR_KEY.public.encoded), GROUP_ID) } doReturn OUR_MEMBER_INFO
        on { getMemberInfo(PEER_PARTY) } doReturn PEER_MEMBER_INFO
        on { getMemberInfo(messageDigest.hash(PEER_KEY.public.encoded), GROUP_ID) } doReturn PEER_MEMBER_INFO
    }
    private val linkManagerHostingMap = mock<LinkManagerHostingMap> {
        on { getTenantId(any()) } doReturn "id"
    }
    private val cryptoService = mock<StubCryptoProcessor> {
        on { sign(any(), eq(OUR_KEY.public), any(), any()) } doReturn "signature-from-A".toByteArray()
    }
    private val pendingSessionMessageQueues = Mockito.mock(LinkManager.PendingSessionMessageQueues::class.java)
    private val sessionReplayer = Mockito.mock(InMemorySessionReplayer::class.java)
    private val protocolInitiator = mock<AuthenticationProtocolInitiator>()
    private val protocolResponder = mock<AuthenticationProtocolResponder>()
    private val protocolFactory = mock<ProtocolFactory> {
        on { createInitiator(any(), any(), any(), any(), any()) } doReturn protocolInitiator
        on { createResponder(any(), any(), any()) } doReturn protocolResponder
    }
    private val resources = ResourcesHolder()

    private val mockTimeFacilitiesProvider = MockTimeFacilitiesProvider()

    private val sessionManager = SessionManagerImpl(
        groups,
        members,
        cryptoService,
        pendingSessionMessageQueues,
        mock(),
        mock(),
        mock(),
        mock(),
        linkManagerHostingMap,
        protocolFactory,
        sessionReplayer,
        mockTimeFacilitiesProvider.clock
    ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
        setRunning()
        configHandler.applyNewConfiguration(
            SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE),
            null,
            mock(),
        )
        heartbeatConfigHandler.applyNewConfiguration(configNoHeartbeat, null, mock())
        createResourcesCallbacks[SessionManagerImpl.HeartbeatManager::class.java.simpleName]!!(resources)
    }

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
    private val authenticatedSession = mock<AuthenticatedSession> {
        on { createMac(any()) } doReturn AuthenticationResult(Mockito.mock(CommonHeader::class.java), RANDOM_BYTES.array())
    }

    private fun setRunning() {
        for (tile in dominoTile.constructed()) {
            whenever(tile.isRunning).doReturn(true)
        }
    }

    @Test
    fun `when no session exists, processing outbound message creates a new session`() {
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded
        assertThat(sessionState.sessionInitMessage.payload).isEqualTo(initiatorHello)

        argumentCaptor<InMemorySessionReplayer.SessionMessageReplay> {
            verify(sessionReplayer).addMessageForReplay(
                any(),
                this.capture(),
                eq(SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY))
            )
            assertThat(this.allValues.size).isEqualTo(1)
            assertThat(this.firstValue.source).isEqualTo(OUR_PARTY)
            assertThat(this.firstValue.dest).isEqualTo(PEER_PARTY)
            assertThat(this.firstValue.message).isEqualTo(initiatorHello)
        }
    }

    @Test
    fun `when no session exists, if network type is missing from network map no message is sent`() {
        whenever(groups.getGroupInfo(GROUP_ID)).thenReturn(null)

        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.CannotEstablishSession::class.java)
        verify(sessionReplayer, never()).addMessageForReplay(any(), any(), any())
        loggingInterceptor.assertSingleWarning("Could not find the group information in the NetworkMap for groupId $GROUP_ID." +
                " The sessionInit message was not sent.")
    }

    @Test
    fun `when no session exists, if protocol mode is missing from network map no message is sent`() {
        whenever(groups.getGroupInfo(GROUP_ID)).thenReturn(null)

        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.CannotEstablishSession::class.java)
        verify(sessionReplayer, never()).addMessageForReplay(any(), any(), any())
        loggingInterceptor.assertSingleWarning("Could not find the group information in the NetworkMap for groupId $GROUP_ID." +
                " The sessionInit message was not sent.")
    }

    @Test
    fun `when no session exists, if source member info is missing from network map no message is sent`() {
        whenever(members.getMemberInfo(OUR_PARTY)).thenReturn(null)

        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.CannotEstablishSession::class.java)
        verify(sessionReplayer, never()).addMessageForReplay(any(), any(), any())
        loggingInterceptor.assertSingleWarning("Attempted to start session negotiation with peer $PEER_PARTY " +
                "but our identity $OUR_PARTY is not in the network map. The sessionInit message was not sent.")
    }

    @Test
    fun `when no session exists, if destination member info is missing from network map no message is sent`() {
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        whenever(members.getMemberInfo(PEER_PARTY)).thenReturn(null)

        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.CannotEstablishSession::class.java)

        argumentCaptor<InMemorySessionReplayer.SessionMessageReplay> {
            verify(sessionReplayer).addMessageForReplay(
                any(),
                this.capture(),
                any()
            )
            assertThat(this.allValues.size).isEqualTo(1)
            assertThat(this.firstValue.source).isEqualTo(OUR_PARTY)
            assertThat(this.firstValue.dest).isEqualTo(PEER_PARTY)
            assertThat(this.firstValue.message).isEqualTo(initiatorHello)
        }

        loggingInterceptor.assertSingleWarning("Attempted to start session negotiation with peer $PEER_PARTY " +
                "which is not in the network map. The sessionInit message was not sent.")
    }

    @Test
    fun `when messages already queued for a peer, there is already a pending session`() {
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        sessionManager.processOutboundMessage(message)
        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.SessionAlreadyPending::class.java)
        verify(pendingSessionMessageQueues, times(2)).queueMessage(message, SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY))
    }

    @Test
    fun `when messages already queued for a peer, if we applyNewConfiguration then NewSessionNeeded`() {
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        sessionManager.processOutboundMessage(message)
        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.SessionAlreadyPending::class.java)
        configHandler.applyNewConfiguration(
            SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE),
            null,
            mock(),
        )
        val sessionStateAfterUpdate = sessionManager.processOutboundMessage(message)
        assertThat(sessionStateAfterUpdate).isInstanceOf(NewSessionNeeded::class.java)
        //Once when we first get a config, once when config is updated
        verify(pendingSessionMessageQueues, times(2)).destroyAllQueues()
    }

    @Test
    fun `when session is established with a peer, it is returned when processing a new message for the same peer`() {
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
        val responderHello = mock<ResponderHelloMessage>()
        whenever(protocolResponder.generateResponderHello()).thenReturn(responderHello)
        whenever(members.getMemberInfo(initiatorKeyHash, GROUP_ID)).thenReturn(null)

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
        val responderHello = mock<ResponderHelloMessage>()
        whenever(protocolResponder.generateResponderHello()).thenReturn(responderHello)
        whenever(groups.getGroupInfo(GROUP_ID)).thenReturn(null)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorKeyHash), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMsg))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Could not find the group information in the NetworkMap for groupId $GROUP_ID." +
                " The ${InitiatorHelloMessage::class.java.simpleName} for sessionId $sessionId was discarded.")
    }

    @Test
    fun `when an initiator hello is received, but protocol modes is missing from network map, then message is dropped`() {
        val initiatorKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val sessionId = "some-session-id"
        val responderHello = mock<ResponderHelloMessage>()
        whenever(protocolResponder.generateResponderHello()).thenReturn(responderHello)
        whenever(groups.getGroupInfo(GROUP_ID)).thenReturn(null)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorKeyHash), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMsg))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Could not find the group information in the NetworkMap for groupId $GROUP_ID." +
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
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), any())).thenReturn(initiatorHandshakeMsg)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage!!.payload).isEqualTo(initiatorHandshakeMsg)
        verify(sessionReplayer).removeMessageFromReplay(
            "${sessionState.sessionId}_${InitiatorHelloMessage::class.java.simpleName}",
            SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY)
        )
        argumentCaptor<InMemorySessionReplayer.SessionMessageReplay> {
            verify(sessionReplayer).addMessageForReplay(
                eq("${sessionState.sessionId}_${InitiatorHandshakeMessage::class.java.simpleName}"),
                this.capture(),
                eq(SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY))
            )
            assertThat(this.allValues.size).isEqualTo(1)
            assertThat(this.firstValue.source).isEqualTo(OUR_PARTY)
            assertThat(this.firstValue.dest).isEqualTo(PEER_PARTY)
            assertThat(this.firstValue.message).isEqualTo(initiatorHandshakeMsg)
        }
    }

    @Test
    fun `when responder hello is received, but our member info is missing from network map, message is dropped`() {
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), any())).thenReturn(initiatorHandshakeMsg)
        whenever(members.getMemberInfo(OUR_PARTY)).thenReturn(null)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${ResponderHelloMessage::class.java.simpleName} with sessionId " +
                "${sessionState.sessionId} but cannot find public key for our identity $OUR_PARTY. The message was discarded.")
    }

    @Test
    fun `when responder hello is received, but peer's member info is missing from network map, message is dropped`() {
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), any())).thenReturn(initiatorHandshakeMsg)
        whenever(members.getMemberInfo(PEER_PARTY)).thenReturn(null)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${ResponderHelloMessage::class.java.simpleName} with sessionId " +
                "${sessionState.sessionId} from peer $PEER_PARTY which is not in the network map. The message was discarded.")
    }

    @Test
    fun `when responder hello is received, but private key cannot be found to sign, message is dropped`() {
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), any()))
            .thenThrow(CouldNotFindPrivateKey())
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("The ${ResponderHelloMessage::class.java.simpleName} with sessionId " +
                "${sessionState.sessionId} was discarded.")
    }

    @Test
    fun `when responder hello is received, but tenant ID cannot be found to sign, message is dropped`() {
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        whenever(linkManagerHostingMap.getTenantId(any())).thenReturn(null)
        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains(
            "Received ${ResponderHelloMessage::class.java.simpleName} with sessionId " +
                "${sessionState.sessionId} but $OUR_PARTY has no tenant ID." +
                " The message was discarded."
        )
    }

    @Test
    fun `when responder hello is received, but network type is missing from network map, message is dropped`() {
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), any())).thenReturn(initiatorHandshakeMsg)
        whenever(groups.getGroupInfo(GROUP_ID)).thenReturn(null)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("Could not find the group information in the NetworkMap for groupId $GROUP_ID." +
                " The ${ResponderHelloMessage::class.java.simpleName} for sessionId ${sessionState.sessionId} was discarded.")
    }

    @Test
    fun `when initiator handshake is received, a responder handshake is returned and session is established`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val responderPublicKeyHash = messageDigest.hash(OUR_KEY.public.encoded)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshakeMessage = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(
            protocolResponder.validatePeerHandshakeMessage(
                initiatorHandshakeMessage,
                PEER_KEY.public,
                ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC,
            )
        ).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
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
    fun `when applyNewConfiguration, after the inbound session is established, the session is removed`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val responderPublicKeyHash = messageDigest.hash(OUR_KEY.public.encoded)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(
            initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID)
        )
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshakeMessage = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(
            protocolResponder.validatePeerHandshakeMessage(
                initiatorHandshakeMessage,
                PEER_KEY.public,
                ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC,
            )
        ).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        val responderHandshakeMsg = mock<ResponderHandshakeMessage>()
        whenever(protocolResponder.generateOurHandshakeMessage(eq(OUR_KEY.public), any())).thenReturn(responderHandshakeMsg)
        val session = mock<Session>()
        whenever(protocolResponder.getSession()).thenReturn(session)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage))

        assertThat(responseMessage!!.payload).isEqualTo(responderHandshakeMsg)
        assertThat(
            sessionManager.getSessionById(sessionId)
        ).isInstanceOfSatisfying(
            SessionManager.SessionDirection.Inbound::class.java
        ) {
            assertThat(it.session).isEqualTo(session)
        }

        configHandler.applyNewConfiguration(
            SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE),
            null,
            mock(),
        )
        assertThat(sessionManager.getSessionById(sessionId)).isEqualTo(SessionManager.SessionDirection.NoSession)
        publisherWithDominoLogicByClientId["session-manager"]!!.forEach {
            verify(it).publish(listOf(Record(SESSION_OUT_PARTITIONS, sessionId, null)))
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
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(members.getMemberInfo(initiatorPublicKeyHash, GROUP_ID)).thenReturn(null)
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
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(initiatorHandshake, PEER_KEY.public, ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC))
            .thenThrow(WrongPublicKeyHashException(initiatorPublicKeyHash.reversedArray(), initiatorPublicKeyHash))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertErrorContains("The message was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but validation of the message fails due to invalid handshake, the message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(initiatorHandshake, PEER_KEY.public, ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC))
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
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(initiatorHandshake, PEER_KEY.public, ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC))
            .thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(members.getMemberInfo(responderPublicKeyHash, GROUP_ID)).thenReturn(null)
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
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(initiatorHandshake, PEER_KEY.public, ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC))
            .thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(groups.getGroupInfo(GROUP_ID)).thenReturn(null)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("Could not find the group information in the NetworkMap for groupId $GROUP_ID." +
                " The ${InitiatorHandshakeMessage::class.java.simpleName} for sessionId $sessionId was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but our key is not found to sign responder handshake, message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val responderPublicKeyHash = messageDigest.hash(OUR_KEY.public.encoded)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(initiatorHandshake, PEER_KEY.public, ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC))
            .thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(protocolResponder.generateOurHandshakeMessage(eq(OUR_KEY.public), any()))
            .thenThrow(UnsupportedAlgorithm(OUR_KEY.public))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but our tenant ID not found to sign responder handshake, message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val responderPublicKeyHash = messageDigest.hash(OUR_KEY.public.encoded)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())
        whenever(linkManagerHostingMap.getTenantId(any())).doReturn(null)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(initiatorHandshake, PEER_KEY.public, ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC))
            .thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, no message is returned and session is established`() {
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
        verify(sessionReplayer).removeMessageFromReplay(
            "${sessionState.sessionId}_${InitiatorHandshakeMessage::class.java.simpleName}",
            SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY)
        )
        verify(pendingSessionMessageQueues)
            .sessionNegotiatedCallback(sessionManager,
                SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY),
                session,
                groups, members
            )
    }

    @Test
    fun `when applyNewConfiguration, after the outbound session is established, the session is removed`() {
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
        verify(sessionReplayer).removeMessageFromReplay(
            "${sessionState.sessionId}_${InitiatorHandshakeMessage::class.java.simpleName}",
            SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY)
        )
        verify(pendingSessionMessageQueues)
            .sessionNegotiatedCallback(
                sessionManager,
                SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY),
                session,
                groups, members
            )

        configHandler.applyNewConfiguration(
            SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE),
            null,
            mock(),
        )
        assertThat(sessionManager.getSessionById(sessionState.sessionId)).isEqualTo(SessionManager.SessionDirection.NoSession)
        publisherWithDominoLogicByClientId["session-manager"]!!.forEach {
            verify(it).publish(listOf(Record(SESSION_OUT_PARTITIONS, sessionState.sessionId, null)))
        }
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
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(members.getMemberInfo(PEER_PARTY)).thenReturn(null)
        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()

        loggingInterceptor.assertSingleWarning("Received ${ResponderHandshakeMessage::class.java.simpleName} with sessionId " +
                "${sessionState.sessionId} from peer $PEER_PARTY which is not in the network map. The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, but validation fails due to invalid key hash, the message is dropped`() {
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(
            protocolInitiator.validatePeerHandshakeMessage(
                responderHandshakeMessage,
                PEER_KEY.public,
                ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC,
            )
        ).thenThrow(InvalidHandshakeResponderKeyHash())
        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()

        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, but validation fails due to invalid handshake, the message is dropped`() {
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(
            protocolInitiator.validatePeerHandshakeMessage(
                responderHandshakeMessage,
                PEER_KEY.public,
                ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC,
            )
        ).thenThrow(InvalidHandshakeMessageException())
        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()

        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when responder hello is received, the session is pending, if no response is received, the session times out`() {
        val resourceHolder = ResourcesHolder()
        val sessionManager = SessionManagerImpl(
            groups,
            members,
            cryptoService,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            protocolFactory,
            sessionReplayer,
            mockTimeFacilitiesProvider.clock
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
            setRunning()
            configHandler.applyNewConfiguration(
                SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE),
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, resourceHolder)
            createResourcesCallbacks[SessionManagerImpl.HeartbeatManager::class.java.simpleName]?.let { it(resourceHolder) }
            createResourcesCallbacks[PublisherWithDominoLogic::class.java.simpleName]?.let { it(resourceHolder) }
        }
        sessionManager.start()

        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), any())).thenReturn(initiatorHandshakeMsg)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        sessionManager.processSessionMessage(LinkInMessage(responderHello))
        assertTrue(sessionManager.processOutboundMessage(message) is SessionManager.SessionState.SessionAlreadyPending)
        mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.sessionTimeout.plus(5.millis))
        assertThat(sessionManager.processOutboundMessage(message)).isInstanceOf(NewSessionNeeded::class.java)
        sessionManager.stop()
        resourceHolder.close()
    }

    @Test
    fun `when responder handshake is received, the session is established, if no message is sent, the session times out`() {
        val resourceHolder = ResourcesHolder()
        val sessionManager = SessionManagerImpl(
            groups, members,
            cryptoService,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            protocolFactory,
            sessionReplayer,
            mockTimeFacilitiesProvider.clock
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
            setRunning()
            configHandler.applyNewConfiguration(
                SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE),
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, resourceHolder)
            createResourcesCallbacks[SessionManagerImpl.HeartbeatManager::class.java.simpleName]?.let { it(resourceHolder) }
            createResourcesCallbacks[PublisherWithDominoLogic::class.java.simpleName]?.let { it(resourceHolder) }
        }
        sessionManager.start()

        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        val session = mock<Session>()
        whenever(protocolInitiator.getSession()).thenReturn(session)
        sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))

        assertTrue(sessionManager.processOutboundMessage(message) is SessionManager.SessionState.SessionEstablished)

        mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.sessionTimeout.plus(5.millis))
        assertThat(sessionManager.processOutboundMessage(message)).isInstanceOf(NewSessionNeeded::class.java)
        verify(publisherWithDominoLogicByClientId["session-manager"]!!.last())
            .publish(listOf(Record(SESSION_OUT_PARTITIONS, sessionState.sessionId, null)))

        sessionManager.stop()
        resourceHolder.close()
    }

    @Test
    fun `when a data message is sent, heartbeats are sent, if these are not acknowledged the session times out`() {
        val messages = mutableListOf<AuthenticatedDataMessage>()
        fun callback(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
            val record = records.single()
            assertEquals(LINK_OUT_TOPIC, record.topic)
            messages.add((record.value as LinkOutMessage).payload as AuthenticatedDataMessage)
            return listOf(CompletableFuture.completedFuture(Unit))
        }

        val resourcesHolder = ResourcesHolder()
        val sessionManager = SessionManagerImpl(
            groups, members,
            cryptoService,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            protocolFactory,
            sessionReplayer,
            mockTimeFacilitiesProvider.clock
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
            setRunning()
            configHandler.applyNewConfiguration(
                SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE),
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, resourcesHolder)
            createResourcesCallbacks[SessionManagerImpl.HeartbeatManager::class.java.simpleName]?.let { it(resourcesHolder) }
        }
        @Suppress("UNCHECKED_CAST")
        publisherWithDominoLogicByClientId[SessionManagerImpl.HeartbeatManager.HEARTBEAT_MANAGER_CLIENT_ID]!!.forEach {
            whenever(it.publish(any())).doAnswer { invocation ->
                callback(invocation.arguments.first() as List<Record<*, *>>)
            }
        }
        sessionManager.start()

        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded
        whenever(authenticatedSession.sessionId).thenReturn(sessionState.sessionId)

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        val session = mock<Session>()
        whenever(protocolInitiator.getSession()).thenReturn(session)
        sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))

        assertTrue(sessionManager.processOutboundMessage(message) is SessionManager.SessionState.SessionEstablished)
        sessionManager.dataMessageSent(authenticatedSession)

        mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.sessionTimeout.plus(5.millis))
        assertThat(sessionManager.processOutboundMessage(message)).isInstanceOf(NewSessionNeeded::class.java)
        verify(publisherWithDominoLogicByClientId["session-manager"]!!.last())
            .publish(listOf(Record(SESSION_OUT_PARTITIONS, sessionState.sessionId, null)))
        sessionManager.stop()
        resourcesHolder.close()

        for (message in messages) {
            val heartbeatMessage = DataMessagePayload.fromByteBuffer(message.payload)
            assertThat(heartbeatMessage.message).isInstanceOf(HeartbeatMessage::class.java)
        }
    }

    @Test
    fun `when a data message is sent, heartbeats are sent, this continues even if the heartbeat manager gets a new config`() {
        val messages = Collections.synchronizedList(mutableListOf<AuthenticatedDataMessage>())

        fun callback(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
            val record = records.single()
            val message = (record.value as LinkOutMessage).payload as AuthenticatedDataMessage
            messages.add(message)
            return listOf(CompletableFuture.completedFuture(Unit))
        }

        val resourcesHolder = ResourcesHolder()
        val sessionManager = SessionManagerImpl(
            groups, members,
            cryptoService,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            protocolFactory,
            sessionReplayer,
            mockTimeFacilitiesProvider.clock
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
            setRunning()
            configHandler.applyNewConfiguration(
                SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE),
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, mock())
            createResourcesCallbacks[SessionManagerImpl.HeartbeatManager::class.java.simpleName]?.let { it(resourcesHolder) }
        }
        @Suppress("UNCHECKED_CAST")
        publisherWithDominoLogicByClientId[SessionManagerImpl.HeartbeatManager.HEARTBEAT_MANAGER_CLIENT_ID]!!.forEach {
            whenever(it.publish(any())).doAnswer { invocation ->
                callback(invocation.arguments.first() as List<Record<*, *>>)
            }
        }
        sessionManager.start()

        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded
        whenever(authenticatedSession.sessionId).thenReturn(sessionState.sessionId)
        whenever(authenticatedSession.createMac(any())).thenReturn(AuthenticationResult(
            CommonHeader(MessageType.DATA, 1, sessionState.sessionId, 5, Instant.now().toEpochMilli()),
            RANDOM_BYTES.array()
        ))

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        val session = mock<Session>()
        whenever(protocolInitiator.getSession()).thenReturn(session)
        sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))

        assertTrue(sessionManager.processOutboundMessage(message) is SessionManager.SessionState.SessionEstablished)
        sessionManager.dataMessageSent(authenticatedSession)

        mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.heartbeatPeriod.plus(5.millis))
        mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.heartbeatPeriod.plus(5.millis))
        assertThat(messages.size).isEqualTo(2)

        heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, mock())

        mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.heartbeatPeriod.plus(5.millis))
        mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.heartbeatPeriod.plus(5.millis))
        assertThat(messages.size).isEqualTo(4)

        sessionManager.stop()
        resourcesHolder.close()
    }

    @Test
    fun `when a data message is sent, heartbeats are sent, this stops if the session manager gets a new config`() {
        var linkOutMessages = 0
        val resourcesHolder = ResourcesHolder()
        fun callback(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
            for (record in records) {
                if (record.topic == LINK_OUT_TOPIC) {
                    linkOutMessages++
                }
            }
            return listOf(CompletableFuture.completedFuture(Unit))
        }

        val sessionManager = SessionManagerImpl(
            groups, members,
            cryptoService,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            protocolFactory,
            sessionReplayer,
            mockTimeFacilitiesProvider.clock
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
            setRunning()
            configHandler.applyNewConfiguration(
                SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE),
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, resourcesHolder)
            createResourcesCallbacks[SessionManagerImpl.HeartbeatManager::class.java.simpleName]?.let { it(resourcesHolder) }
        }
        @Suppress("UNCHECKED_CAST")
        publisherWithDominoLogicByClientId[SessionManagerImpl.HeartbeatManager.HEARTBEAT_MANAGER_CLIENT_ID]!!.forEach {
            whenever(it.publish(any())).doAnswer { invocation ->
                callback(invocation.arguments.first() as List<Record<*, *>>)
            }
        }
        sessionManager.start()

        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded
        whenever(authenticatedSession.sessionId).thenReturn(sessionState.sessionId)
        whenever(authenticatedSession.createMac(any())).thenReturn(
            AuthenticationResult(
                CommonHeader(MessageType.DATA, 1, sessionState.sessionId, 5, Instant.now().toEpochMilli()),
                RANDOM_BYTES.array()
            )
        )

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        val session = mock<Session>()
        whenever(protocolInitiator.getSession()).thenReturn(session)
        sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))

        assertTrue(sessionManager.processOutboundMessage(message) is SessionManager.SessionState.SessionEstablished)
        sessionManager.dataMessageSent(authenticatedSession)

        repeat(2) { mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.heartbeatPeriod.plus(5.millis)) }
        assertThat(linkOutMessages).isEqualTo(2)

        configHandler.applyNewConfiguration(
            SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE),
            null,
            resourcesHolder,
        )

        mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.heartbeatPeriod.plus(5.millis))
        assertThat(linkOutMessages).isEqualTo(2)
        verify(publisherWithDominoLogicByClientId["session-manager"]!!.last())
            .publish(listOf(Record(SESSION_OUT_PARTITIONS, sessionState.sessionId, null)))

        resourcesHolder.close()
        sessionManager.stop()
    }
    @Test
    fun `when a data message is sent, heartbeats are sent, if these are acknowledged the session does not time out`() {
        val resourcesHolder = ResourcesHolder()

        val messages = Collections.synchronizedList(mutableListOf<AuthenticatedDataMessage>())
        fun callback(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
            val record = records.single()
            assertEquals(LINK_OUT_TOPIC, record.topic)
            val message = (record.value as LinkOutMessage).payload as AuthenticatedDataMessage
            messages.add(message)
            return listOf(CompletableFuture.completedFuture(Unit))
        }

        val sessionManager = SessionManagerImpl(
            groups, members,
            cryptoService,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            protocolFactory,
            sessionReplayer,
            mockTimeFacilitiesProvider.clock
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
            setRunning()
            configHandler.applyNewConfiguration(
                SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE),
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, resourcesHolder)
            createResourcesCallbacks[SessionManagerImpl.HeartbeatManager::class.java.simpleName]?.let { it(resourcesHolder) }
        }
        publisherWithDominoLogicByClientId[SessionManagerImpl.HeartbeatManager.HEARTBEAT_MANAGER_CLIENT_ID]!!.forEach {
            whenever(it.publish(any())).doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                callback(invocation.arguments.first() as List<Record<*, *>>)
            }
        }
        sessionManager.start()

        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionNeeded
        whenever(authenticatedSession.sessionId).thenReturn(sessionState.sessionId)
        whenever(authenticatedSession.createMac(any())).thenReturn(AuthenticationResult(
            CommonHeader(MessageType.DATA, 1, sessionState.sessionId, 5, Instant.now().toEpochMilli()),
            RANDOM_BYTES.array()
        ))

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionState.sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        val session = mock<Session>()
        whenever(protocolInitiator.getSession()).thenReturn(session)
        sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))

        assertTrue(sessionManager.processOutboundMessage(message) is SessionManager.SessionState.SessionEstablished)

        sessionManager.dataMessageSent(authenticatedSession)
        // sum of heartbeats extending over the session timeout
        val numberOfHeartbeats = configWithHeartbeat.let {
            (2 * (it.sessionTimeout.toMillis() / it.heartbeatPeriod.toMillis())).toInt()
        }
        repeat(numberOfHeartbeats) {
            mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.heartbeatPeriod.plus(5.millis))
            sessionManager.messageAcknowledged(sessionState.sessionId)
        }
        assertThat(messages).hasSize(numberOfHeartbeats)
        synchronized(messages) {
            for (message in messages) {
                val heartbeatMessage = DataMessagePayload.fromByteBuffer(message.payload)
                assertThat(heartbeatMessage.message).isInstanceOf(HeartbeatMessage::class.java)
            }
        }
        assertTrue(sessionManager.processOutboundMessage(message) is SessionManager.SessionState.SessionEstablished)

        sessionManager.stop()
        resourcesHolder.close()
    }

    @Test
    fun `when sending a heartbeat, if an exception is thrown, the heartbeat is resent`() {
        var sentHeartbeats = 0
        var throwFirst = true
        fun publish(): List<CompletableFuture<Unit>> {
           sentHeartbeats++
           if (throwFirst) {
               throwFirst = false
               return listOf(CompletableFuture.failedFuture(RuntimeException("Ohh No something went wrong.")))
           }
           return listOf(CompletableFuture.completedFuture(Unit))
        }

        val resourcesHolder = ResourcesHolder()
        val sessionManager = SessionManagerImpl(
            groups, members,
            cryptoService,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            protocolFactory,
            sessionReplayer,
            mockTimeFacilitiesProvider.clock
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
            setRunning()
            configHandler.applyNewConfiguration(
                SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE),
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, resourcesHolder)
            createResourcesCallbacks[SessionManagerImpl.HeartbeatManager::class.java.simpleName]?.let { it(resourcesHolder) }
        }
        publisherWithDominoLogicByClientId[SessionManagerImpl.HeartbeatManager.HEARTBEAT_MANAGER_CLIENT_ID]!!.forEach {
            whenever(it.publish(any())).doAnswer { publish() }
        }

        sessionManager.start()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        val sessionState = sessionManager.processOutboundMessage(message)
        whenever(authenticatedSession.sessionId).thenReturn((sessionState as NewSessionNeeded).sessionId)

        sessionManager.dataMessageSent(authenticatedSession)

        repeat(3) { mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.heartbeatPeriod.plus(5.millis)) }
        assertThat(sentHeartbeats).isEqualTo(3)
        loggingInterceptor.assertSingleWarningContains("An exception was thrown when sending a heartbeat message.")
        sessionManager.stop()
        resourcesHolder.close()
    }
}
