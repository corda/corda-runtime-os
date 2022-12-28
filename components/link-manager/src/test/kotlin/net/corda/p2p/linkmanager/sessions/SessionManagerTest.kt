package net.corda.p2p.linkmanager.sessions

import net.corda.crypto.client.CryptoOpsClient
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.SimpleDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.DataMessagePayload
import net.corda.p2p.HeartbeatMessage
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
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
import net.corda.p2p.crypto.protocol.api.InvalidPeerCertificate
import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import net.corda.p2p.crypto.protocol.api.RevocationCheckMode
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.crypto.protocol.api.WrongPublicKeyHashException
import net.corda.p2p.linkmanager.delivery.InMemorySessionReplayer
import net.corda.p2p.linkmanager.hosting.HostingMapListener
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.membership.LinkManagerMembershipGroupReader
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.NewSessionsNeeded
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.schema.Schemas.P2P.Companion.LINK_OUT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.SESSION_OUT_PARTITIONS
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.MockTimeFacilitiesProvider
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.days
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.minutes
import net.corda.v5.base.util.toBase64
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
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
import org.mockito.kotlin.atLeast
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
import java.util.*
import java.util.concurrent.CompletableFuture

class SessionManagerTest {

    companion object {
        const val KEY = "KEY"
        const val GROUP_ID = "myGroup"
        const val MAX_MESSAGE_SIZE = 1024 * 1024
        const val SESSION_REFRESH_THRESHOLD_KEY = 432000
        const val SESSIONS_PER_COUNTERPARTIES = 2
        val PROTOCOL_MODES = listOf(ProtocolMode.AUTHENTICATED_ENCRYPTION, ProtocolMode.AUTHENTICATION_ONLY)
        val RANDOM_BYTES = ByteBuffer.wrap("some-random-data".toByteArray())

        private val sixDaysInMillis = 6.days.toMillis()
        private val configWithHeartbeat = SessionManagerImpl.HeartbeatManager.HeartbeatManagerConfig(
            Duration.ofMillis(100),
            Duration.ofMillis(500)
        )
        private val configNoHeartbeat = SessionManagerImpl.HeartbeatManager.HeartbeatManagerConfig(
            Duration.ofMillis(sixDaysInMillis),
            Duration.ofMillis(sixDaysInMillis)
        )

        private val keyGenerator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider())
        private val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, BouncyCastleProvider())

        private val OUR_PARTY = createTestHoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", GROUP_ID)
        private val OUR_KEY = keyGenerator.genKeyPair()
        private val OUR_MEMBER_INFO =
            LinkManagerMembershipGroupReader.MemberInfo(OUR_PARTY, OUR_KEY.public, KeyAlgorithm.ECDSA,
                "http://alice.com")
        private val PEER_PARTY = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", GROUP_ID)
        private val PEER_KEY = keyGenerator.genKeyPair()
        private val PEER_MEMBER_INFO =
            LinkManagerMembershipGroupReader.MemberInfo(PEER_PARTY, PEER_KEY.public, KeyAlgorithm.ECDSA,
                "http://bob.com")

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
        outboundSessionPool.close()
    }

    private lateinit var configHandler: SessionManagerImpl.SessionManagerConfigChangeHandler
    private lateinit var heartbeatConfigHandler: SessionManagerImpl.HeartbeatManager.HeartbeatManagerConfigChangeHandler
    private val dominoTile = Mockito.mockConstruction(ComplexDominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleWriteLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        if (context.arguments()[6] is SessionManagerImpl.SessionManagerConfigChangeHandler) {
            configHandler = context.arguments()[6] as SessionManagerImpl.SessionManagerConfigChangeHandler
        }
        if (context.arguments()[6] is SessionManagerImpl.HeartbeatManager.HeartbeatManagerConfigChangeHandler) {
            heartbeatConfigHandler = context.arguments()[6] as SessionManagerImpl.HeartbeatManager.HeartbeatManagerConfigChangeHandler
        }
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
    }
    private val publisherWithDominoLogicByClientId = mutableMapOf<String, MutableList<PublisherWithDominoLogic>>()
    private val publisherWithDominoLogic = Mockito.mockConstruction(PublisherWithDominoLogic::class.java) { mock, context ->
        publisherWithDominoLogicByClientId.compute((context.arguments()[2] as PublisherConfig).clientId) { _, map ->
            map?.apply { this.add(mock) } ?: mutableListOf(mock)
        }
        val mockDominoTile = mock<DominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        whenever(mock.dominoTile).thenReturn(mockDominoTile)
    }
    private val parameters = mock<GroupPolicy.P2PParameters> {
        on { tlsPki } doReturn GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode.STANDARD
        on { sessionPki } doReturn GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI
        on { protocolMode } doReturn GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH_ENCRYPT
    }
    private val groupPolicy = mock<GroupPolicy> {
        on { p2pParameters } doReturn parameters
    }
    private val groupPolicyProvider = mock<GroupPolicyProvider> {
        on { getGroupPolicy(OUR_PARTY) } doReturn groupPolicy
    }
    private val members = mock<LinkManagerMembershipGroupReader> {
        val membersDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        on { getMemberInfo(OUR_PARTY, OUR_PARTY) } doReturn OUR_MEMBER_INFO
        on { getMemberInfo(OUR_PARTY, messageDigest.hash(OUR_KEY.public.encoded)) } doReturn OUR_MEMBER_INFO
        on { getMemberInfo(OUR_PARTY, PEER_PARTY) } doReturn PEER_MEMBER_INFO
        on { getMemberInfo(OUR_PARTY, messageDigest.hash(PEER_KEY.public.encoded)) } doReturn PEER_MEMBER_INFO
        on { dominoTile } doReturn membersDominoTile
    }
    private val hostingIdentity = HostingMapListener.IdentityInfo(
        holdingIdentity = OUR_PARTY,
        tlsCertificates = emptyList(),
        tlsTenantId = "tlsId",
        sessionKeyTenantId = "id",
        sessionPublicKey = OUR_KEY.public,
        sessionCertificates = null
    )

    private val counterparties = SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY)
    private val linkManagerHostingMap = mock<LinkManagerHostingMap> {
        val hostingMapDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        on { getInfo(OUR_PARTY) } doReturn hostingIdentity
        on { getInfo(messageDigest.hash(OUR_KEY.public.encoded), OUR_PARTY.groupId) } doReturn hostingIdentity
        on { dominoTile } doReturn hostingMapDominoTile
        on { allLocallyHostedIdentities() } doReturn listOf(OUR_PARTY)
    }
    private val signature = mock<DigitalSignature.WithKey> {
        on { bytes } doReturn "signature-from-A".toByteArray()
    }
    private val cryptoOpsClient = mock<CryptoOpsClient> {
        on { sign(any(), eq(OUR_KEY.public), any<SignatureSpec>(), any(), any()) } doReturn signature
    }
    private val pendingSessionMessageQueues = mock<PendingSessionMessageQueues> {
        val pendingSessionMessageQueuesDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        on { dominoTile } doReturn pendingSessionMessageQueuesDominoTile
    }
    private val sessionReplayer = mock<InMemorySessionReplayer> {
        val sessionReplayerDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        on { dominoTile } doReturn sessionReplayerDominoTile
    }
    private val protocolInitiator = mock<AuthenticationProtocolInitiator> {
        on { sessionId } doReturn "sessionId"
    }
    private val secondProtocolInitiator = mock<AuthenticationProtocolInitiator> {
        on { sessionId } doReturn "anotherSessionId"
    }
    private val protocolResponder = mock<AuthenticationProtocolResponder>()
    private val protocolFactory = mock<ProtocolFactory> {
        on { createInitiator(any(), any(), any(), any(), any(), any()) } doReturn protocolInitiator doReturn secondProtocolInitiator
        on { createResponder(any(), any(), any(), any()) } doReturn protocolResponder
    }
    private val resources = ResourcesHolder()

    private val mockTimeFacilitiesProvider = MockTimeFacilitiesProvider()
    private val outboundSessionPool = Mockito.mockConstruction(OutboundSessionPool::class.java)

    private val sessionManager = SessionManagerImpl(
        groupPolicyProvider,
        members,
        cryptoOpsClient,
        pendingSessionMessageQueues,
        mock(),
        mock(),
        mock(),
        mock(),
        mock {
            val dominoTile = mock<SimpleDominoTile> {
                whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
            }
            on { it.dominoTile } doReturn dominoTile
        },
        linkManagerHostingMap,
        protocolFactory,
        mockTimeFacilitiesProvider.clock,
        sessionReplayer,
    ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
        setRunning()
        configHandler.applyNewConfiguration(
            SessionManagerImpl.SessionManagerConfig(
                MAX_MESSAGE_SIZE,
                SESSIONS_PER_COUNTERPARTIES,
                RevocationCheckMode.OFF,
                SESSION_REFRESH_THRESHOLD_KEY,
            ),
            null,
            mock(),
        )
        heartbeatConfigHandler.applyNewConfiguration(configNoHeartbeat, null, mock())
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
                PEER_PARTY.toAvro(),
                OUR_PARTY.toAvro(),
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

    /**
     * Send the [sessionManager] an authenticatedMessage and a [ResponderHandshakeMessage] so that it starts sending Heartbeats.
     */
    private fun startSendingHeartbeats(sessionManager: SessionManager) {
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        whenever(outboundSessionPool.constructed().last().getNextSession(counterparties)).thenReturn(
            OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded
        )
        sessionManager.processOutboundMessage(message)
        whenever(outboundSessionPool.constructed().last().getSession(protocolInitiator.sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, protocolInitiator.sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(authenticatedSession.sessionId).doAnswer { protocolInitiator.sessionId }
        whenever(protocolInitiator.getSession()).thenReturn(authenticatedSession)
        sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))
    }

    @Test
    fun `when no session exists, processing outbound message creates a new session`() {
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties))
            .thenReturn(OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded)
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        val anotherInitiatorHello = mock<InitiatorHelloMessage>()
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(anotherInitiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message) as NewSessionsNeeded
        assertThat(sessionState.messages).extracting<Any> {
            it.second.payload
        }.containsExactlyInAnyOrder(initiatorHello, anotherInitiatorHello)

        argumentCaptor<InMemorySessionReplayer.SessionMessageReplay> {
            verify(sessionReplayer, times(2)).addMessageForReplay(
                any(),
                this.capture(),
                eq(counterparties)
            )
            assertThat(this.allValues.size).isEqualTo(2)
            assertThat(this.allValues).extracting<HoldingIdentity> { it.source }.containsOnly(OUR_PARTY)
            assertThat(this.allValues).extracting<HoldingIdentity> { it.dest }.containsOnly(PEER_PARTY)
            assertThat(this.allValues).extracting<InitiatorHelloMessage> { it.message as InitiatorHelloMessage }
                .containsExactlyInAnyOrder(initiatorHello, anotherInitiatorHello)
        }
    }

    @Test
    fun `when no session exists, if source member info is missing from network map no message is sent`() {
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties))
            .thenReturn(OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded)
        whenever(linkManagerHostingMap.getInfo(OUR_PARTY)).thenReturn(null)
        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.CannotEstablishSession::class.java)
        verify(sessionReplayer, never()).addMessageForReplay(any(), any(), any())
        loggingInterceptor.assertSingleWarning("Attempted to start session negotiation with peer $PEER_PARTY " +
                "but our identity $OUR_PARTY is not in the members map. The sessionInit message was not sent.")
    }

    @Test
    fun `when no session exists, if destination member info is missing from network map no message is sent`() {
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties))
            .thenReturn(OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded)
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        val anotherInitiatorHello = mock<InitiatorHelloMessage>()
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(anotherInitiatorHello)
        whenever(members.getMemberInfo(OUR_PARTY, PEER_PARTY)).thenReturn(null)

        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.CannotEstablishSession::class.java)

        argumentCaptor<InMemorySessionReplayer.SessionMessageReplay> {
            verify(sessionReplayer, times(2)).addMessageForReplay(
                any(),
                this.capture(),
                eq(SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY))
            )
            assertThat(this.allValues.size).isEqualTo(2)
            assertThat(this.allValues).extracting<HoldingIdentity> { it.source }.containsOnly(OUR_PARTY)
            assertThat(this.allValues).extracting<HoldingIdentity> { it.dest }.containsOnly(PEER_PARTY)
            assertThat(this.allValues).extracting<InitiatorHelloMessage> { it.message as InitiatorHelloMessage }
                .containsExactlyInAnyOrder(initiatorHello, anotherInitiatorHello)
        }

        loggingInterceptor.assertSingleWarning("Attempted to start session negotiation with peer $PEER_PARTY " +
                "which is not in ${OUR_PARTY}'s members map. The sessionInit message was not sent.")
    }

    @Test
    fun `when no session exists, if network type is missing from network map no message is sent`() {
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties))
            .thenReturn(OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded)
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        val anotherInitiatorHello = mock<InitiatorHelloMessage>()
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(anotherInitiatorHello)
        whenever(groupPolicyProvider.getGroupPolicy(OUR_PARTY)).thenReturn(null)

        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.CannotEstablishSession::class.java)

        loggingInterceptor.assertSingleWarningContains("Could not find the group information in the GroupPolicyProvider")
        loggingInterceptor.assertSingleWarningContains("The sessionInit message was not sent.")
    }

    @Test
    fun `when no session exists, if protocol mode is missing from network map no message is sent`() {
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties))
            .thenReturn(OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded)
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        val anotherInitiatorHello = mock<InitiatorHelloMessage>()
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(anotherInitiatorHello)
        whenever(groupPolicyProvider.getGroupPolicy(OUR_PARTY)).thenReturn(null)

        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.CannotEstablishSession::class.java)
        verify(sessionReplayer, never()).addMessageForReplay(any(), any(), any())
        loggingInterceptor.assertSingleWarningContains("Could not find the group information in the GroupPolicyProvider")
        loggingInterceptor.assertSingleWarningContains("The sessionInit message was not sent.")
    }

    @Test
    fun `when messages already queued for a peer, there is already a pending session`() {
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties))
            .thenReturn(OutboundSessionPool.SessionPoolStatus.SessionPending)
        sessionManager.processOutboundMessage(message)
        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.SessionAlreadyPending::class.java)
    }

    @Test
    fun `when we applyNewConfiguration, then all queues are destroyed and the outbound session pool is cleared`() {
        val sessionIds = listOf("firstSession", "anotherSession")
        whenever(outboundSessionPool.constructed().first().getAllSessionIds()).thenReturn(sessionIds)
        configHandler.applyNewConfiguration(
            SessionManagerImpl.SessionManagerConfig(
                MAX_MESSAGE_SIZE,
                SESSIONS_PER_COUNTERPARTIES,
                RevocationCheckMode.OFF,
                SESSION_REFRESH_THRESHOLD_KEY,
            ),
            SessionManagerImpl.SessionManagerConfig(
                MAX_MESSAGE_SIZE,
                SESSIONS_PER_COUNTERPARTIES,
                RevocationCheckMode.OFF,
                SESSION_REFRESH_THRESHOLD_KEY,
            ),
            mock(),
        )
        verify(pendingSessionMessageQueues, times(1)).destroyAllQueues()
        verify(outboundSessionPool.constructed().first(), times(1)).clearPool()
        publisherWithDominoLogicByClientId["session-manager"]!!.forEach { publisher ->
            verify(publisher).publish(sessionIds.map { Record(SESSION_OUT_PARTITIONS, it, null)}.toList())
        }
    }

    @Test
    fun `when session is established with a peer, it is returned when processing a new message for the same peer`() {
        val session = mock<Session> {
            on { sessionId } doReturn "sessionId"
        }
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties)).thenReturn(
            OutboundSessionPool.SessionPoolStatus.SessionActive(session)
        )
        whenever(outboundSessionPool.constructed().first().getSession(session.sessionId)).thenReturn(
            OutboundSessionPool.SessionType.ActiveSession(counterparties, session)
        )

        val newSessionState = sessionManager.processOutboundMessage(message)
        assertThat(newSessionState).isInstanceOfSatisfying(SessionManager.SessionState.SessionEstablished::class.java) {
            assertThat(it.session).isEqualTo(session)
        }
        assertThat(sessionManager.getSessionById(session.sessionId))
            .isInstanceOfSatisfying(SessionManager.SessionDirection.Outbound::class.java) {
                assertThat(it.session).isEqualTo(session)
            }
    }

    @Test
    fun `when no session exists for a session id, get session returns nothing`() {
        val sessionId = "some-session-id"
        whenever(outboundSessionPool.constructed().first().getSession(sessionId)).thenReturn(
            null
        )
        assertThat(sessionManager.getSessionById(sessionId)).isInstanceOf(SessionManager.SessionDirection.NoSession::class.java)
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
    fun `when an initiator hello is received, we do a lookup for the public key hash, using all the locally hosted identities`() {
        val sessionId = "some-session-id"
        val responderHello = mock<ResponderHelloMessage>()
        val carol = createTestHoldingIdentity("CN=Carol, O=Alice Corp, L=LDN, C=GB", GROUP_ID)
        val david = createTestHoldingIdentity("CN=David, O=Alice Corp, L=LDN, C=GB", GROUP_ID)
        whenever(linkManagerHostingMap.allLocallyHostedIdentities()).thenReturn(
            listOf(
                carol,
                david,
                OUR_PARTY,
            )
        )
        whenever(protocolResponder.generateResponderHello()).thenReturn(responderHello)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))

        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMsg))

        verify(members).getMemberInfo(eq(carol), any<ByteArray>())
        verify(members).getMemberInfo(eq(david), any<ByteArray>())
    }

    @Test
    fun `when an initiator hello is received, but peer's member info is missing from network map, then message is dropped`() {
        val initiatorKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val sessionId = "some-session-id"
        val responderHello = mock<ResponderHelloMessage>()
        whenever(protocolResponder.generateResponderHello()).thenReturn(responderHello)
        whenever(members.getMemberInfo(OUR_PARTY, initiatorKeyHash)).thenReturn(null)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorKeyHash), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMsg))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${InitiatorHelloMessage::class.java.simpleName} with sessionId ${sessionId}. " +
                "The received public key hash (${initiatorKeyHash.toBase64()}) corresponding " +
                "to one of the sender's holding identities is not in the members map. The message was discarded.")
    }

    @Test
    fun `when an initiator hello is received, but network type is missing from network map, then message is dropped`() {
        val initiatorKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val sessionId = "some-session-id"
        val responderHello = mock<ResponderHelloMessage>()
        whenever(protocolResponder.generateResponderHello()).thenReturn(responderHello)
        whenever(groupPolicyProvider.getGroupPolicy(OUR_PARTY)).thenReturn(null)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorKeyHash), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMsg))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("Could not find the group information in the GroupPolicyProvider")
        loggingInterceptor
            .assertSingleWarningContains("The ${InitiatorHelloMessage::class.java.simpleName} for sessionId $sessionId was discarded.")
    }

    @Test
    fun `when an initiator hello is received, but protocol modes is missing from network map, then message is dropped`() {
        val initiatorKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val sessionId = "some-session-id"
        val responderHello = mock<ResponderHelloMessage>()
        whenever(protocolResponder.generateResponderHello()).thenReturn(responderHello)
        whenever(groupPolicyProvider.getGroupPolicy(OUR_PARTY)).thenReturn(null)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorKeyHash), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMsg))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("Could not find the group information in the GroupPolicyProvider")
        loggingInterceptor
            .assertSingleWarningContains("The ${InitiatorHelloMessage::class.java.simpleName} for sessionId $sessionId was discarded.")
    }

    @Test
    fun `when initiator hello is received but no locally hosted identity in the same group as the initiator, message is dropped`() {
        val initiatorKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val sessionId = "some-session-id"
        whenever(linkManagerHostingMap.allLocallyHostedIdentities()).thenReturn(emptySet())

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorKeyHash), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMsg))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("There is no locally hosted identity in group $GROUP_ID.")
        loggingInterceptor
            .assertSingleWarningContains("The initiator message was discarded.")
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
        val sessionId = "some-session"
        whenever(outboundSessionPool.constructed().first().getSession(sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(initiatorHandshakeMsg)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage!!.payload).isEqualTo(initiatorHandshakeMsg)
        verify(sessionReplayer).removeMessageFromReplay(
            "${sessionId}_${InitiatorHelloMessage::class.java.simpleName}",
            counterparties
        )
        argumentCaptor<InMemorySessionReplayer.SessionMessageReplay> {
            verify(sessionReplayer).addMessageForReplay(
                eq("${sessionId}_${InitiatorHandshakeMessage::class.java.simpleName}"),
                this.capture(),
                eq(counterparties)
            )
            assertThat(this.allValues.size).isEqualTo(1)
            assertThat(this.firstValue.source).isEqualTo(OUR_PARTY)
            assertThat(this.firstValue.dest).isEqualTo(PEER_PARTY)
            assertThat(this.firstValue.message).isEqualTo(initiatorHandshakeMsg)
        }
    }

    @Test
    fun `when responder hello is received, but our member info is missing from network map, message is dropped`() {
        val sessionId = "some-session"
        whenever(outboundSessionPool.constructed().first().getSession(sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(initiatorHandshakeMsg)
        whenever(linkManagerHostingMap.getInfo(OUR_PARTY)).thenReturn(null)
        whenever(members.getMemberInfo(OUR_PARTY, OUR_PARTY)).thenReturn(null)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${ResponderHelloMessage::class.java.simpleName} with sessionId $sessionId but " +
                "cannot find public key for our identity $OUR_PARTY. The message was discarded.")
    }

    @Test
    fun `when responder hello is received, but peer's member info is missing from network map, message is dropped`() {
        val sessionId = "some-session"
        whenever(outboundSessionPool.constructed().first().getSession(sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(initiatorHandshakeMsg)
        whenever(members.getMemberInfo(OUR_PARTY, PEER_PARTY)).thenReturn(null)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${ResponderHelloMessage::class.java.simpleName} with sessionId $sessionId from " +
                "peer $PEER_PARTY which is not in the members map. The message was discarded.")
    }

    @Test
    fun `when responder hello is received, but private key cannot be found to sign, message is dropped`() {
        val sessionId = "some-session"
        whenever(outboundSessionPool.constructed().first().getSession(sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any()))
            .thenThrow(CordaRuntimeException("Nop"))
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("The ${ResponderHelloMessage::class.java.simpleName} with sessionId $sessionId was" +
                " discarded.")
    }

    @Test
    fun `when responder hello is received, but network type is missing from network map, message is dropped`() {
        val sessionId = "some-session"
        whenever(outboundSessionPool.constructed().first().getSession(sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(initiatorHandshakeMsg)
        whenever(groupPolicyProvider.getGroupPolicy(OUR_PARTY)).thenReturn(null)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("Could not find the group information in the GroupPolicyProvider for identity " +
            "$OUR_PARTY. The ${ResponderHelloMessage::class.java.simpleName} for sessionId ${sessionId} was discarded.")
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
                PEER_MEMBER_INFO.holdingIdentity.x500Name,
                PEER_KEY.public,
                SignatureSpec.ECDSA_SHA256,
            )
        ).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        val responderHandshakeMsg = mock<ResponderHandshakeMessage>()
        whenever(protocolResponder.generateOurHandshakeMessage(eq(OUR_KEY.public), eq(null), any())).thenReturn(responderHandshakeMsg)
        val session = mock<Session>()
        whenever(protocolResponder.getSession()).thenReturn(session)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage))

        assertThat(responseMessage!!.payload).isEqualTo(responderHandshakeMsg)
        assertThat(sessionManager.getSessionById(sessionId)).isInstanceOfSatisfying(SessionManager.SessionDirection.Inbound::class.java) {
            assertThat(it.session).isEqualTo(session)
        }
    }

    @Test
    fun `when initiator handshake is received, we do a lookup for the public key hash, using all the locally hosted identities`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val responderPublicKeyHash = messageDigest.hash(OUR_KEY.public.encoded)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())
        val carol = createTestHoldingIdentity("CN=Carol, O=Alice Corp, L=LDN, C=GB", GROUP_ID)
        val david = createTestHoldingIdentity("CN=David, O=Alice Corp, L=LDN, C=GB", GROUP_ID)
        whenever(linkManagerHostingMap.allLocallyHostedIdentities()).thenReturn(
            listOf(
                carol,
                david,
                OUR_PARTY,
            )
        )

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
                PEER_MEMBER_INFO.holdingIdentity.x500Name,
                PEER_KEY.public,
                SignatureSpec.ECDSA_SHA256,
            )
        ).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        val responderHandshakeMsg = mock<ResponderHandshakeMessage>()
        whenever(protocolResponder.generateOurHandshakeMessage(eq(OUR_KEY.public), eq(null), any())).thenReturn(responderHandshakeMsg)
        val session = mock<Session>()
        whenever(protocolResponder.getSession()).thenReturn(session)
        sessionManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage))

        verify(members, atLeast(1)).getMemberInfo(eq(carol), any<ByteArray>())
        verify(members, atLeast(1)).getMemberInfo(eq(david), any<ByteArray>())
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
                PEER_MEMBER_INFO.holdingIdentity.x500Name,
                PEER_KEY.public,
                SignatureSpec.ECDSA_SHA256,
            )
        ).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        val responderHandshakeMsg = mock<ResponderHandshakeMessage>()
        whenever(protocolResponder.generateOurHandshakeMessage(eq(OUR_KEY.public), eq(null), any())).thenReturn(responderHandshakeMsg)
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
        sessionManager.inboundSessionEstablished(sessionId)

        configHandler.applyNewConfiguration(
            SessionManagerImpl.SessionManagerConfig(
                MAX_MESSAGE_SIZE,
                SESSIONS_PER_COUNTERPARTIES,
                RevocationCheckMode.OFF,
                SESSION_REFRESH_THRESHOLD_KEY
            ),
            mock(),
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
    fun `when initiator handshake is received but no locally hosted identity in the same group as the initiator, message is dropped`() {
        val sessionId = "some-session-id"
        whenever(protocolResponder.generateResponderHello()).thenReturn(mock())

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHandshakeIdentity = InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID)
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, initiatorHandshakeIdentity)
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        whenever(linkManagerHostingMap.allLocallyHostedIdentities()).thenReturn(emptySet())
        whenever(protocolResponder.getInitiatorIdentity()).thenReturn(initiatorHandshakeIdentity)
        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)

        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("There is no locally hosted identity in group $GROUP_ID.")
        loggingInterceptor
            .assertSingleWarningContains("The initiator handshake message was discarded.")
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
        whenever(members.getMemberInfo(OUR_PARTY, initiatorPublicKeyHash)).thenReturn(null)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId " +
                "${sessionId}. The received public key hash (${initiatorPublicKeyHash.toBase64()}) corresponding " +
                "to one of the sender's holding identities is not in the members map. The message was discarded.")
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
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            PEER_MEMBER_INFO.holdingIdentity.x500Name,
            PEER_KEY.public,
            SignatureSpec.ECDSA_SHA256
        )).thenThrow(WrongPublicKeyHashException(initiatorPublicKeyHash.reversedArray(), initiatorPublicKeyHash))
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
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            PEER_MEMBER_INFO.holdingIdentity.x500Name,
            PEER_KEY.public,
            SignatureSpec.ECDSA_SHA256
        )).thenThrow(InvalidHandshakeMessageException())
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but validation fails due to InvalidPeerCertificate, the message is dropped`() {
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
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            PEER_MEMBER_INFO.holdingIdentity.x500Name,
            PEER_KEY.public,
            SignatureSpec.ECDSA_SHA256
        )).thenThrow(InvalidPeerCertificate("Invalid peer certificate"))
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
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            PEER_MEMBER_INFO.holdingIdentity.x500Name,
            PEER_KEY.public,
            SignatureSpec.ECDSA_SHA256
        )).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(linkManagerHostingMap.getInfo(responderPublicKeyHash, GROUP_ID)).thenReturn(null)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId " +
                "${sessionId}. The received public key hash (${responderPublicKeyHash.toBase64()}) corresponding " +
                "to one of our holding identities is not in the members map. The message was discarded.")
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
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            PEER_MEMBER_INFO.holdingIdentity.x500Name,
            PEER_KEY.public,
            SignatureSpec.ECDSA_SHA256
        )).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(groupPolicyProvider.getGroupPolicy(OUR_PARTY)).thenReturn(null)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("Could not find the group information in the GroupPolicyProvider")
        loggingInterceptor
            .assertSingleWarningContains("The ${InitiatorHandshakeMessage::class.java.simpleName} for sessionId $sessionId was discarded.")
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
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            PEER_MEMBER_INFO.holdingIdentity.x500Name,
            PEER_KEY.public,
            SignatureSpec.ECDSA_SHA256
        )).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(protocolResponder.generateOurHandshakeMessage(eq(OUR_KEY.public), eq(null), any()))
            .thenThrow(CordaRuntimeException("Nop"))
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
        whenever(linkManagerHostingMap.getInfo(responderPublicKeyHash, OUR_PARTY.groupId)).doReturn(null)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            PROTOCOL_MODES, InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            PEER_MEMBER_INFO.holdingIdentity.x500Name,
            PEER_KEY.public,
            SignatureSpec.ECDSA_SHA256
        )).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, no message is returned and session is established`() {
        val someSessionId = "some-session"
        whenever(outboundSessionPool.constructed().first().getSession(someSessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(mock())
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, someSessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        sessionManager.processSessionMessage(LinkInMessage(responderHello))

        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        val session = mock<Session> {
            on { sessionId } doReturn someSessionId
        }
        whenever(protocolInitiator.getSession()).thenReturn(session)
        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()

        verify(outboundSessionPool.constructed().first()).updateAfterSessionEstablished(session)
        verify(sessionReplayer).removeMessageFromReplay(
            "${someSessionId}_${InitiatorHandshakeMessage::class.java.simpleName}",
            SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY)
        )
        verify(pendingSessionMessageQueues)
            .sessionNegotiatedCallback(
                sessionManager,
                SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY),
                session,
                groupPolicyProvider,
                members
            )
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
        val sessionId = "some-session"
        whenever(outboundSessionPool.constructed().first().getSession(sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(members.getMemberInfo(OUR_PARTY, PEER_PARTY)).thenReturn(null)
        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()

        loggingInterceptor.assertSingleWarning("Received ${ResponderHandshakeMessage::class.java.simpleName} with sessionId $sessionId " +
                "from peer $PEER_PARTY which is not in the members map. The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, but validation fails due to invalid key hash, the message is dropped`() {
        val sessionId = "some-session"
        whenever(outboundSessionPool.constructed().first().getSession(sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(
            protocolInitiator.validatePeerHandshakeMessage(
                responderHandshakeMessage,
                PEER_MEMBER_INFO.holdingIdentity.x500Name,
                PEER_KEY.public,
                SignatureSpec.ECDSA_SHA256,
            )
        ).thenThrow(InvalidHandshakeResponderKeyHash())
        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()

        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, but validation fails due to InvalidPeerCertificate, the message is dropped`() {
        val sessionId = "some-session"
        whenever(outboundSessionPool.constructed().first().getSession(sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(
            protocolInitiator.validatePeerHandshakeMessage(
                responderHandshakeMessage,
                PEER_MEMBER_INFO.holdingIdentity.x500Name,
                PEER_KEY.public,
                SignatureSpec.ECDSA_SHA256,
            )
        ).thenThrow(InvalidPeerCertificate("Bad Cert"))
        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()

        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, but validation fails due to invalid handshake, the message is dropped`() {
        val sessionId = "some-session"
        whenever(outboundSessionPool.constructed().first().getSession(sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(
            protocolInitiator.validatePeerHandshakeMessage(
                responderHandshakeMessage,
                PEER_MEMBER_INFO.holdingIdentity.x500Name,
                PEER_KEY.public,
                SignatureSpec.ECDSA_SHA256,
            )
        ).thenThrow(InvalidHandshakeMessageException())
        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()

        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when responder hello is received, the session is pending, if no response is received, the session times out`() {
        val resourceHolder = ResourcesHolder()
        val sessionManager = SessionManagerImpl(
            groupPolicyProvider,
            members,
            cryptoOpsClient,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            mock {
                val dominoTile = mock<SimpleDominoTile> {
                    whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
                }
                on { it.dominoTile } doReturn dominoTile
            },
            linkManagerHostingMap,
            protocolFactory,
            mockTimeFacilitiesProvider.clock,
            sessionReplayer,
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
            setRunning()
            configHandler.applyNewConfiguration(
                SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE, 1, RevocationCheckMode.OFF, SESSION_REFRESH_THRESHOLD_KEY),
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, resourceHolder)
        }
        sessionManager.start()

        val sessionId = "some-session"
        whenever(outboundSessionPool.constructed().last().getSession(sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )
        whenever(outboundSessionPool.constructed().last().getNextSession(counterparties)).thenReturn(
            OutboundSessionPool.SessionPoolStatus.SessionPending
        )

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(initiatorHandshakeMsg)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)
        sessionManager.processSessionMessage(LinkInMessage(responderHello))
        assertTrue(sessionManager.processOutboundMessage(message) is SessionManager.SessionState.SessionAlreadyPending)
        mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.sessionTimeout.plus(5.millis))
        verify(outboundSessionPool.constructed().last()).replaceSession(sessionId, protocolInitiator)

        sessionManager.stop()
        resourceHolder.close()
    }

    @Test
    fun `when responder handshake is received, the session is established, if no message is sent, the session times out`() {
        val resourceHolder = ResourcesHolder()
        val sessionManager = SessionManagerImpl(
            groupPolicyProvider, members,
            cryptoOpsClient,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            mock {
                val dominoTile = mock<SimpleDominoTile> {
                    whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
                }
                on { it.dominoTile } doReturn dominoTile
            },
            linkManagerHostingMap,
            protocolFactory,
            mockTimeFacilitiesProvider.clock,
            sessionReplayer,
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
            setRunning()
            configHandler.applyNewConfiguration(
                SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE, 1, RevocationCheckMode.OFF, SESSION_REFRESH_THRESHOLD_KEY),
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, resourceHolder)
        }
        sessionManager.start()

        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)

        whenever(outboundSessionPool.constructed().last().getNextSession(counterparties)).thenReturn(
            OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded
        )
        sessionManager.processOutboundMessage(message)
        whenever(outboundSessionPool.constructed().last().getSession(protocolInitiator.sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )
        val header = CommonHeader(
            MessageType.RESPONDER_HANDSHAKE,
            1,
            protocolInitiator.sessionId,
            4,
            Instant.now().toEpochMilli()
        )
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        val session = mock<Session>()
        whenever(session.sessionId).doAnswer { protocolInitiator.sessionId }
        whenever(protocolInitiator.getSession()).thenReturn(session)
        sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))

        whenever(outboundSessionPool.constructed().last().replaceSession(eq(protocolInitiator.sessionId), any())).thenReturn(true)
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.sessionTimeout.plus(5.millis))
        verify(outboundSessionPool.constructed().last()).replaceSession(protocolInitiator.sessionId, secondProtocolInitiator)
        verify(publisherWithDominoLogicByClientId["session-manager"]!!.last())
            .publish(listOf(Record(SESSION_OUT_PARTITIONS, protocolInitiator.sessionId, null)))

        sessionManager.stop()
        resourceHolder.close()
    }

    @Test
    fun `when a responder handshake message is received, heartbeats are sent, if these are not acknowledged the session times out`() {
        val messages = mutableListOf<AuthenticatedDataMessage>()
        fun callback(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
            val record = records.single()
            assertEquals(LINK_OUT_TOPIC, record.topic)
            messages.add((record.value as LinkOutMessage).payload as AuthenticatedDataMessage)
            return listOf(CompletableFuture.completedFuture(Unit))
        }

        val resourcesHolder = ResourcesHolder()
        val sessionManager = SessionManagerImpl(
            groupPolicyProvider, members,
            cryptoOpsClient,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            mock {
                val dominoTile = mock<SimpleDominoTile> {
                    whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
                }
                on { it.dominoTile } doReturn dominoTile
            },
            linkManagerHostingMap,
            protocolFactory,
            mockTimeFacilitiesProvider.clock,
            sessionReplayer,
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
            setRunning()
            configHandler.applyNewConfiguration(
                SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE, 1, RevocationCheckMode.OFF, SESSION_REFRESH_THRESHOLD_KEY),
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, resourcesHolder)
        }
        @Suppress("UNCHECKED_CAST")
        publisherWithDominoLogicByClientId[SessionManagerImpl.HeartbeatManager.HEARTBEAT_MANAGER_CLIENT_ID]!!.forEach {
            whenever(it.publish(any())).doAnswer { invocation ->
                callback(invocation.arguments.first() as List<Record<*, *>>)
            }
        }
        sessionManager.start()
        startSendingHeartbeats(sessionManager)

        whenever(outboundSessionPool.constructed().last().replaceSession(eq(protocolInitiator.sessionId), any())).thenReturn(true)
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(mock())
        mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.sessionTimeout.plus(5.millis))
        verify(outboundSessionPool.constructed().last()).replaceSession(protocolInitiator.sessionId, secondProtocolInitiator)
        verify(publisherWithDominoLogicByClientId["session-manager"]!!.last())
            .publish(listOf(Record(SESSION_OUT_PARTITIONS, protocolInitiator.sessionId, null)))
        sessionManager.stop()
        resourcesHolder.close()

        assertThat(messages.size).isGreaterThanOrEqualTo(1)
        for (message in messages) {
            val heartbeatMessage = DataMessagePayload.fromByteBuffer(message.payload)
            assertThat(heartbeatMessage.message).isInstanceOf(HeartbeatMessage::class.java)
        }
    }

    @Test
    fun `when a responder handshake message is received, heartbeats are sent, this continues if the heartbeat manager gets a new config`() {
        val messages = Collections.synchronizedList(mutableListOf<AuthenticatedDataMessage>())

        fun callback(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
            val record = records.single()
            val message = (record.value as LinkOutMessage).payload as AuthenticatedDataMessage
            messages.add(message)
            return listOf(CompletableFuture.completedFuture(Unit))
        }

        val resourcesHolder = ResourcesHolder()
        val sessionManager = SessionManagerImpl(
            groupPolicyProvider, members,
            cryptoOpsClient,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            mock {
                val dominoTile = mock<SimpleDominoTile> {
                    whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
                }
                on { it.dominoTile } doReturn dominoTile
            },
            linkManagerHostingMap,
            protocolFactory,
            mockTimeFacilitiesProvider.clock,
            sessionReplayer,
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
            setRunning()
            configHandler.applyNewConfiguration(
                SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE, 1, RevocationCheckMode.OFF, SESSION_REFRESH_THRESHOLD_KEY),
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, mock())
        }
        @Suppress("UNCHECKED_CAST")
        publisherWithDominoLogicByClientId[SessionManagerImpl.HeartbeatManager.HEARTBEAT_MANAGER_CLIENT_ID]!!.forEach {
            whenever(it.publish(any())).doAnswer { invocation ->
                callback(invocation.arguments.first() as List<Record<*, *>>)
            }
        }
        sessionManager.start()
        startSendingHeartbeats(sessionManager)

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
    fun `when a responder handshake message is received, heartbeats are sent, this stops if the session manager gets a new config`() {
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
            groupPolicyProvider, members,
            cryptoOpsClient,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            mock {
                val dominoTile = mock<SimpleDominoTile> {
                    whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
                }
                on { it.dominoTile } doReturn dominoTile
            },
            linkManagerHostingMap,
            protocolFactory,
            mockTimeFacilitiesProvider.clock,
            sessionReplayer,
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
            setRunning()
            configHandler.applyNewConfiguration(
                SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE, 1, RevocationCheckMode.OFF, SESSION_REFRESH_THRESHOLD_KEY),
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, resourcesHolder)
        }
        @Suppress("UNCHECKED_CAST")
        publisherWithDominoLogicByClientId[SessionManagerImpl.HeartbeatManager.HEARTBEAT_MANAGER_CLIENT_ID]!!.forEach {
            whenever(it.publish(any())).doAnswer { invocation ->
                callback(invocation.arguments.first() as List<Record<*, *>>)
            }
        }
        sessionManager.start()

        whenever(outboundSessionPool.constructed().last().replaceSession(eq(protocolInitiator.sessionId), any())).thenReturn(true)
        whenever(outboundSessionPool.constructed().last().getAllSessionIds()).thenAnswer { (listOf(protocolInitiator.sessionId)) }
        startSendingHeartbeats(sessionManager)

        repeat(2) { mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.heartbeatPeriod.plus(5.millis)) }
        assertThat(linkOutMessages).isEqualTo(2)

        configHandler.applyNewConfiguration(
            SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE, 1, RevocationCheckMode.OFF, SESSION_REFRESH_THRESHOLD_KEY),
            SessionManagerImpl.SessionManagerConfig(2 * MAX_MESSAGE_SIZE, 1, RevocationCheckMode.OFF, SESSION_REFRESH_THRESHOLD_KEY),
            resourcesHolder,
        )

        mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.heartbeatPeriod.plus(5.millis))
        assertThat(linkOutMessages).isEqualTo(2)
        verify(publisherWithDominoLogicByClientId["session-manager"]!!.last())
            .publish(listOf(Record(SESSION_OUT_PARTITIONS, authenticatedSession.sessionId, null)))

        resourcesHolder.close()
        sessionManager.stop()
    }

    @Test
    fun `when a responder handshake message is received, heartbeats are sent, if these are acknowledged the session does not time out`() {
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
            groupPolicyProvider, members,
            cryptoOpsClient,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            mock {
                val dominoTile = mock<SimpleDominoTile> {
                    whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
                }
                on { it.dominoTile } doReturn dominoTile
            },
            linkManagerHostingMap,
            protocolFactory,
            mockTimeFacilitiesProvider.clock,
            sessionReplayer,
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
            setRunning()
            configHandler.applyNewConfiguration(
                SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE, 1, RevocationCheckMode.OFF, SESSION_REFRESH_THRESHOLD_KEY),
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, resourcesHolder)
        }
        publisherWithDominoLogicByClientId[SessionManagerImpl.HeartbeatManager.HEARTBEAT_MANAGER_CLIENT_ID]!!.forEach {
            whenever(it.publish(any())).doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                callback(invocation.arguments.first() as List<Record<*, *>>)
            }
        }
        sessionManager.start()

        whenever(outboundSessionPool.constructed().last().replaceSession(eq(protocolInitiator.sessionId), any())).thenReturn(true)
        whenever(outboundSessionPool.constructed().last().getAllSessionIds()).thenAnswer { (listOf(protocolInitiator.sessionId)) }
        startSendingHeartbeats(sessionManager)

        // sum of heartbeats extending over the session timeout
        val numberOfHeartbeats = configWithHeartbeat.let {
            (2 * (it.sessionTimeout.toMillis() / it.heartbeatPeriod.toMillis())).toInt()
        }
        repeat(numberOfHeartbeats) {
            mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.heartbeatPeriod.plus(5.millis))
            sessionManager.messageAcknowledged(protocolInitiator.sessionId)
        }
        assertThat(messages).hasSize(numberOfHeartbeats)
        for (message in messages) {
            val heartbeatMessage = DataMessagePayload.fromByteBuffer(message.payload)
            assertThat(heartbeatMessage.message).isInstanceOf(HeartbeatMessage::class.java)
        }

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
            groupPolicyProvider, members,
            cryptoOpsClient,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            mock {
                val dominoTile = mock<SimpleDominoTile> {
                    whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
                }
                on { it.dominoTile } doReturn dominoTile
            },
            linkManagerHostingMap,
            protocolFactory,
            mockTimeFacilitiesProvider.clock,
            sessionReplayer,
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
            setRunning()
            configHandler.applyNewConfiguration(
                SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE, 1, RevocationCheckMode.OFF, SESSION_REFRESH_THRESHOLD_KEY),
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, resourcesHolder)
        }
        publisherWithDominoLogicByClientId[SessionManagerImpl.HeartbeatManager.HEARTBEAT_MANAGER_CLIENT_ID]!!.forEach {
            whenever(it.publish(any())).doAnswer { publish() }
        }
        sessionManager.start()
        whenever(outboundSessionPool.constructed().last().replaceSession(eq(protocolInitiator.sessionId), any())).thenReturn(true)
        whenever(outboundSessionPool.constructed().last().getAllSessionIds()).thenAnswer { (listOf(protocolInitiator.sessionId)) }

        startSendingHeartbeats(sessionManager)

        repeat(3) { mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.heartbeatPeriod.plus(5.millis)) }
        assertThat(sentHeartbeats).isEqualTo(3)
        loggingInterceptor.assertSingleWarningContains("An exception was thrown when sending a heartbeat message.")
        sessionManager.stop()
        resourcesHolder.close()
    }

    @Test
    fun `sessions are refreshed after 5 days`() {
        whenever(outboundSessionPool.constructed().first().getSession(protocolInitiator.sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(mock())

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, protocolInitiator.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)

        sessionManager.processSessionMessage(LinkInMessage(responderHello))
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        val session = mock<Session>()

        whenever(session.sessionId).doAnswer { protocolInitiator.sessionId }
        whenever(protocolInitiator.getSession()).thenReturn(session)
        whenever(outboundSessionPool.constructed().last().replaceSession(eq(protocolInitiator.sessionId), any())).thenReturn(true)
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())

        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()
        mockTimeFacilitiesProvider.advanceTime(5.days + 1.minutes)

        loggingInterceptor.assertInfoContains("Outbound session sessionId" +
                " (local=HoldingIdentity(x500Name=CN=Alice, O=Alice Corp, L=LDN, C=GB, groupId=myGroup)," +
                " remote=HoldingIdentity(x500Name=CN=Bob, O=Bob Corp, L=LDN, C=GB, groupId=myGroup))" +
                " timed out to refresh ephemeral keys and it will be cleaned up."
        )

        verify(sessionReplayer, times(2)).removeMessageFromReplay(
            "${protocolInitiator.sessionId}_${InitiatorHandshakeMessage::class.java.simpleName}",
            SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY)
        )

        verify(sessionReplayer, times(2)).removeMessageFromReplay(
            "${protocolInitiator.sessionId}_${InitiatorHelloMessage::class.java.simpleName}",
            SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY)
        )

        verify(outboundSessionPool.constructed().last()).replaceSession(protocolInitiator.sessionId, protocolInitiator)
        verify(publisherWithDominoLogicByClientId["session-manager"]!!.last())
            .publish(listOf(Record(SESSION_OUT_PARTITIONS, protocolInitiator.sessionId, null))
        )
    }

    @Test
    fun `sessions that have been refreshed are not tracked by the heartbeat manager`() {
        val longTimePeriodConfigWithHeartbeat = SessionManagerImpl.HeartbeatManager.HeartbeatManagerConfig(
            Duration.ofDays(1),
            Duration.ofDays(10)
        )
        val messages = Collections.synchronizedList(mutableListOf<AuthenticatedDataMessage>())

        fun callback(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
            val record = records.single()
            assertEquals(LINK_OUT_TOPIC, record.topic)
            val message = (record.value as LinkOutMessage).payload as AuthenticatedDataMessage
            messages.add(message)
            return listOf(CompletableFuture.completedFuture(Unit))
        }

        val resourcesHolder = ResourcesHolder()
        val sessionManager = SessionManagerImpl(
            groupPolicyProvider, members,
            cryptoOpsClient,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            mock {
                val dominoTile = mock<SimpleDominoTile> {
                    whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
                }
                on { it.dominoTile } doReturn dominoTile
            },
            linkManagerHostingMap,
            protocolFactory,
            mockTimeFacilitiesProvider.clock,
            sessionReplayer,
        ) { mockTimeFacilitiesProvider.mockScheduledExecutor }.apply {
            setRunning()
            configHandler.applyNewConfiguration(
                SessionManagerImpl.SessionManagerConfig(MAX_MESSAGE_SIZE, 1, RevocationCheckMode.OFF, SESSION_REFRESH_THRESHOLD_KEY),
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(longTimePeriodConfigWithHeartbeat, null, mock())
        }
        @Suppress("UNCHECKED_CAST")
        publisherWithDominoLogicByClientId[SessionManagerImpl.HeartbeatManager.HEARTBEAT_MANAGER_CLIENT_ID]!!.forEach {
            whenever(it.publish(any())).doAnswer { invocation ->
                callback(invocation.arguments.first() as List<Record<*, *>>)
            }
        }

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, protocolInitiator.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)

        sessionManager.processSessionMessage(LinkInMessage(responderHello))
        startSendingHeartbeats(sessionManager)


        fun advanceTimeAndAcknowledgeMessages() {
            mockTimeFacilitiesProvider.advanceTime(longTimePeriodConfigWithHeartbeat.heartbeatPeriod.plus(5.millis))
            sessionManager.messageAcknowledged(protocolInitiator.sessionId)
        }

        val heartbeatsExpected = 4

        repeat(heartbeatsExpected) {
            advanceTimeAndAcknowledgeMessages()
        }

        //trigger session expiry
        advanceTimeAndAcknowledgeMessages()

        assertThat(heartbeatsExpected).isEqualTo(messages.size)

        for (message in messages) {
            val heartbeatMessage = DataMessagePayload.fromByteBuffer(message.payload)
            assertThat(heartbeatMessage.message).isInstanceOf(HeartbeatMessage::class.java)
        }

        loggingInterceptor.assertInfoContains("Outbound session sessionId" +
                " (local=HoldingIdentity(x500Name=CN=Alice, O=Alice Corp, L=LDN, C=GB, groupId=myGroup)," +
                " remote=HoldingIdentity(x500Name=CN=Bob, O=Bob Corp, L=LDN, C=GB, groupId=myGroup))" +
                " timed out to refresh ephemeral keys and it will be cleaned up."
        )

        sessionManager.stop()
        resourcesHolder.close()
    }

    @Test
    fun `sessions are removed even if groupInfo is missing`() {
        whenever(outboundSessionPool.constructed().first().getSession(protocolInitiator.sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(mock())

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, protocolInitiator.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded), ProtocolMode.AUTHENTICATED_ENCRYPTION)

        sessionManager.processSessionMessage(LinkInMessage(responderHello))
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        val session = mock<Session>()

        whenever(session.sessionId).doAnswer{protocolInitiator.sessionId}
        whenever(protocolInitiator.getSession()).thenReturn(session)
        whenever(outboundSessionPool.constructed().last().replaceSession(eq(protocolInitiator.sessionId), any())).thenReturn(true)
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        whenever(groupPolicyProvider.getGroupPolicy(OUR_PARTY)).thenReturn(null)

        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()
        mockTimeFacilitiesProvider.advanceTime(5.days + 1.minutes)

        loggingInterceptor.assertInfoContains("Outbound session sessionId" +
                " (local=HoldingIdentity(x500Name=CN=Alice, O=Alice Corp, L=LDN, C=GB, groupId=myGroup)," +
                " remote=HoldingIdentity(x500Name=CN=Bob, O=Bob Corp, L=LDN, C=GB, groupId=myGroup))" +
                " timed out to refresh ephemeral keys and it will be cleaned up."
        )

        verify(sessionReplayer, times(2)).removeMessageFromReplay(
            "${protocolInitiator.sessionId}_${InitiatorHandshakeMessage::class.java.simpleName}",
            SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY)
        )

        verify(sessionReplayer, times(2)).removeMessageFromReplay(
            "${protocolInitiator.sessionId}_${InitiatorHelloMessage::class.java.simpleName}",
            SessionManager.SessionCounterparties(OUR_PARTY, PEER_PARTY)
        )

        verify(outboundSessionPool.constructed().last()).removeSessions(counterparties)

    }

}
