package net.corda.p2p.linkmanager.sessions

import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.SecureHashImpl
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.DataMessagePayload
import net.corda.data.p2p.HeartbeatMessage
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.NetworkType
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.MessageType
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.data.p2p.crypto.ResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.data.p2p.crypto.internal.InitiatorHandshakeIdentity
import net.corda.data.p2p.crypto.protocol.RevocationCheckMode
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.SimpleDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.AuthenticationResult
import net.corda.p2p.crypto.protocol.api.HandshakeIdentityData
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeMessageException
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeResponderKeyHash
import net.corda.p2p.crypto.protocol.api.InvalidPeerCertificate
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.crypto.protocol.api.WrongPublicKeyHashException
import net.corda.p2p.linkmanager.delivery.InMemorySessionReplayer
import net.corda.p2p.linkmanager.hosting.HostingMapListener
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.NewSessionsNeeded
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.linkmanager.utilities.mockMemberInfo
import net.corda.schema.Schemas.P2P.LINK_OUT_TOPIC
import net.corda.schema.Schemas.P2P.P2P_OUT_MARKERS
import net.corda.schema.Schemas.P2P.SESSION_OUT_PARTITIONS
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.MockTimeFacilitiesProvider
import net.corda.utilities.days
import net.corda.utilities.millis
import net.corda.utilities.minutes
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.EncodingUtils.toBase64
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
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.Key
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.concurrent.CompletableFuture
import net.corda.p2p.crypto.protocol.api.CertificateCheckMode
import net.corda.p2p.crypto.protocol.api.InvalidSelectedModeError
import net.corda.p2p.crypto.protocol.api.NoCommonModeError
import net.corda.p2p.linkmanager.grouppolicy.protocolModes
import java.util.UUID
import java.util.concurrent.TimeUnit
import net.corda.membership.lib.exceptions.BadGroupPolicyException

class SessionManagerTest {

    private companion object {
        const val KEY = "KEY"
        const val GROUP_ID = "myGroup"
        const val MAX_MESSAGE_SIZE = 1024 * 1024
        const val SESSION_REFRESH_THRESHOLD_KEY = 432000
        const val SESSIONS_PER_COUNTERPARTIES_FOR_MEMBERS = 2
        const val SESSIONS_PER_COUNTERPARTIES_FOR_MGM = 1
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
        private const val OUR_ENDPOINT = "http://alice.com"
        private val OUR_MEMBER_INFO = mockMemberInfo(
            OUR_PARTY,
            OUR_ENDPOINT,
            OUR_KEY.public,
        )
        private const val PEER_ENDPOINT = "http://bob.com"
        private val PEER_PARTY = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", GROUP_ID)
        private val PEER_KEY = keyGenerator.genKeyPair()
        private val PEER_MEMBER_INFO = mockMemberInfo(
            PEER_PARTY,
            PEER_ENDPOINT,
            PEER_KEY.public,
        )

        private val mockResponderHelloMessage: ResponderHelloMessage = mock {
            on { responderPublicKey } doReturn ByteBuffer.wrap(OUR_KEY.public.encoded)
        }

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
    private val groupPolicyProvider = mock<GroupPolicyProvider> {
        on { getP2PParameters(OUR_PARTY) } doReturn parameters
    }
    private val membershipGroupReader = mock<MembershipGroupReader> {
        on { lookup(eq(OUR_PARTY.x500Name), any()) } doReturn OUR_MEMBER_INFO
        on {
            lookupBySessionKey(
                eq(SecureHashImpl("SHA-256", messageDigest.hash(OUR_KEY.public.encoded))),
                any()
            )
        } doReturn OUR_MEMBER_INFO
        on { lookup(eq(PEER_PARTY.x500Name), any()) } doReturn PEER_MEMBER_INFO
        on {
            lookupBySessionKey(
                eq(SecureHashImpl("SHA-256", messageDigest.hash(PEER_KEY.public.encoded))),
                any()
            )
        } doReturn PEER_MEMBER_INFO
    }
    private val otherMembershipGroupReader = mock<MembershipGroupReader>()
    private val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider> {
        on { getGroupReader(OUR_PARTY) } doReturn membershipGroupReader
        on { getGroupReader(argThat { this != OUR_PARTY }) } doReturn otherMembershipGroupReader
    }
    private val hostingIdentity = HostingMapListener.IdentityInfo(
        holdingIdentity = OUR_PARTY,
        tlsCertificates = emptyList(),
        tlsTenantId = "tlsId",
        HostingMapListener.SessionKeyAndCertificates(
            sessionPublicKey = OUR_KEY.public,
            sessionCertificateChain = null
        ),
        emptyList(),
    )

    private val counterparties = SessionManager.SessionCounterparties(
        ourId = OUR_PARTY,
        counterpartyId = PEER_PARTY,
        status = MembershipStatusFilter.ACTIVE,
        serial = 1L,
        communicationWithMgm = false,
    )
    private val linkManagerHostingMap = mock<LinkManagerHostingMap> {
        val hostingMapDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        on { getInfo(OUR_PARTY) } doReturn hostingIdentity
        on { getInfo(messageDigest.hash(OUR_KEY.public.encoded), OUR_PARTY.groupId) } doReturn hostingIdentity
        on { dominoTile } doReturn hostingMapDominoTile
        on { allLocallyHostedIdentities() } doReturn listOf(OUR_PARTY)
    }
    private val signature = mock<DigitalSignatureWithKey> {
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
    private val sessionId = "sessionId"
    private val protocolInitiator = mock<AuthenticationProtocolInitiator> {
        on { sessionId } doReturn sessionId
    }
    private val secondProtocolInitiator = mock<AuthenticationProtocolInitiator> {
        on { sessionId } doReturn "anotherSessionId"
    }
    private val protocolResponder = mock<AuthenticationProtocolResponder> {
        on { hash(any()) } doAnswer {
            messageDigest.hash(it.getArgument<Key>(0).encoded)
        }
    }
    private val protocolFactory = mock<ProtocolFactory> {
        on { createInitiator(any(), any(), any(), any(), any(), any()) } doReturn protocolInitiator doReturn secondProtocolInitiator
        on { createResponder(any(), any()) } doReturn protocolResponder
    }
    private val resources = ResourcesHolder()

    private val mockTimeFacilitiesProvider = MockTimeFacilitiesProvider()
    private val outboundSessionPool = Mockito.mockConstruction(OutboundSessionPool::class.java)
    private val config = SessionManagerImpl.SessionManagerConfig(
        MAX_MESSAGE_SIZE,
        SESSIONS_PER_COUNTERPARTIES_FOR_MEMBERS,
        SESSIONS_PER_COUNTERPARTIES_FOR_MGM,
        RevocationCheckMode.OFF,
        SESSION_REFRESH_THRESHOLD_KEY,
    )
    private val configWithOneSessionBetweenMembers = SessionManagerImpl.SessionManagerConfig(
        MAX_MESSAGE_SIZE,
        1,
        SESSIONS_PER_COUNTERPARTIES_FOR_MGM,
        RevocationCheckMode.OFF,
        SESSION_REFRESH_THRESHOLD_KEY,
    )

    private val sessionManager = SessionManagerImpl(
        groupPolicyProvider,
        membershipGroupReaderProvider,
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
            config,
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
                "", "system-1", MembershipStatusFilter.ACTIVE
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
            assertThat(this.allValues).extracting<SessionManager.SessionCounterparties> { it.sessionCounterparties }
                .containsOnly(counterparties)
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
        whenever(membershipGroupReader.lookup(PEER_PARTY.x500Name, MembershipStatusFilter.ACTIVE)).thenReturn(null)
        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.CannotEstablishSession::class.java)

        verify(outboundSessionPool.constructed().first(), never()).getNextSession(counterparties)
        loggingInterceptor.assertSingleWarningContains("Could not get session information from message sent from")
        loggingInterceptor.assertSingleWarningContains("Peer is not in the members map.")
    }

    @Test
    fun `when no session exists, config is used to determine how many sessions are needed - members only`() {
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties))
            .thenReturn(OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded)
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(mock())

        sessionManager.processOutboundMessage(message)
        verify(protocolFactory, times(SESSIONS_PER_COUNTERPARTIES_FOR_MEMBERS))
            .createInitiator(any(), any(), any(), eq(OUR_KEY.public), eq(OUR_PARTY.groupId), any())
    }

    @Test
    fun `when no session exists, config is used to determine how many sessions are needed - initiator is MGM`() {
        val counterparties = SessionManager.SessionCounterparties(
            ourId = OUR_PARTY,
            counterpartyId = PEER_PARTY,
            status = MembershipStatusFilter.ACTIVE,
            serial = 1L,
            communicationWithMgm = true,
        )
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties))
            .thenReturn(OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded)
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(mock())
        val mgmInfo = mockMemberInfo(
            OUR_PARTY, OUR_ENDPOINT, OUR_KEY.public, isMgm = true
        )
        whenever(membershipGroupReader.lookup(OUR_PARTY.x500Name, MembershipStatusFilter.ACTIVE_OR_SUSPENDED))
            .thenReturn(mgmInfo)

        sessionManager.processOutboundMessage(message)
        verify(protocolFactory, times(SESSIONS_PER_COUNTERPARTIES_FOR_MGM))
            .createInitiator(any(), any(), any(), eq(OUR_KEY.public), eq(OUR_PARTY.groupId), any())
    }

    @Test
    fun `when no session exists, config is used to determine how many sessions are needed - counterparty is MGM`() {
        val counterparties = SessionManager.SessionCounterparties(
            ourId = OUR_PARTY,
            counterpartyId = PEER_PARTY,
            status = MembershipStatusFilter.ACTIVE,
            serial = 1L,
            communicationWithMgm = true,
        )
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties))
            .thenReturn(OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded)
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(mock())
        val counterpartyInfo = mockMemberInfo(
            PEER_PARTY, PEER_ENDPOINT, PEER_KEY.public, isMgm = true
        )
        whenever(membershipGroupReader.lookup(OUR_PARTY.x500Name, MembershipStatusFilter.ACTIVE_OR_SUSPENDED))
            .thenReturn(counterpartyInfo)

        sessionManager.processOutboundMessage(message)
        verify(protocolFactory, times(SESSIONS_PER_COUNTERPARTIES_FOR_MGM))
            .createInitiator(any(), any(), any(), eq(OUR_KEY.public), eq(OUR_PARTY.groupId), any())
    }

    @Test
    fun `when no session exists, config is used to determine how many sessions are needed - initiator is missing its info`() {
        val counterparties = SessionManager.SessionCounterparties(
            ourId = OUR_PARTY,
            counterpartyId = PEER_PARTY,
            status = MembershipStatusFilter.ACTIVE,
            serial = 1L,
            communicationWithMgm = false,
        )
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties))
            .thenReturn(OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded)
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(mock())
        whenever(membershipGroupReader.lookup(OUR_PARTY.x500Name, MembershipStatusFilter.ACTIVE_OR_SUSPENDED))
            .thenReturn(null)

        sessionManager.processOutboundMessage(message)
        verify(protocolFactory, times(SESSIONS_PER_COUNTERPARTIES_FOR_MEMBERS))
            .createInitiator(any(), any(), any(), eq(OUR_KEY.public), eq(OUR_PARTY.groupId), any())
    }

    @Test
    fun `when no session exists, if destination member info is missing from network map on second lookup no message is sent`() {
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties))
            .thenReturn(OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded)
        whenever(membershipGroupReader.lookup(PEER_PARTY.x500Name, MembershipStatusFilter.ACTIVE))
            .thenReturn(PEER_MEMBER_INFO)
            .thenReturn(null)
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        val anotherInitiatorHello = mock<InitiatorHelloMessage>()
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(anotherInitiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.CannotEstablishSession::class.java)

        argumentCaptor<InMemorySessionReplayer.SessionMessageReplay> {
            verify(sessionReplayer, times(2)).addMessageForReplay(
                any(),
                this.capture(),
                eq(counterparties),
            )
            assertThat(this.allValues.size).isEqualTo(2)
            assertThat(this.allValues).extracting<HoldingIdentity> {
                it.sessionCounterparties.ourId
            }.containsOnly(OUR_PARTY)
            assertThat(this.allValues).extracting<HoldingIdentity> {
                it.sessionCounterparties.counterpartyId
            }.containsOnly(PEER_PARTY)
            assertThat(this.allValues).extracting<InitiatorHelloMessage> { it.message as InitiatorHelloMessage }
                .containsExactlyInAnyOrder(initiatorHello, anotherInitiatorHello)
        }

        loggingInterceptor.assertSingleWarning("Attempted to start session negotiation with peer $PEER_PARTY " +
                "which is not in ${OUR_PARTY}'s members map. Filter was ${MembershipStatusFilter.ACTIVE}. " +
                "The sessionInit message was not sent. Message will be retried.")
    }

    @Test
    fun `when no session exists, if network type is missing from group policy provider no message is sent`() {
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties))
            .thenReturn(OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded)
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        val anotherInitiatorHello = mock<InitiatorHelloMessage>()
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(anotherInitiatorHello)
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)).thenReturn(null)

        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.CannotEstablishSession::class.java)

        loggingInterceptor.assertSingleWarningContains("Could not find the p2p parameters in the GroupPolicyProvider")
        loggingInterceptor.assertSingleWarningContains("The sessionInit message was not sent.")
    }
    @Test
    fun `when no session exists, if group policy is missing from group policy provider, on second lookup no message is sent`() {
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties))
            .thenReturn(OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded)
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY))
            .thenReturn(parameters)
            .thenReturn(null)
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        val anotherInitiatorHello = mock<InitiatorHelloMessage>()
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(anotherInitiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(NewSessionsNeeded::class.java)

        argumentCaptor<InMemorySessionReplayer.SessionMessageReplay> {
            verify(sessionReplayer, times(2)).addMessageForReplay(
                any(),
                this.capture(),
                eq(counterparties),
            )
            assertThat(this.allValues.size).isEqualTo(2)
            assertThat(this.allValues).extracting<HoldingIdentity> {
                it.sessionCounterparties.ourId
            }.containsOnly(OUR_PARTY)
            assertThat(this.allValues).extracting<HoldingIdentity> {
                it.sessionCounterparties.counterpartyId
            }.containsOnly(PEER_PARTY)
            assertThat(this.allValues).extracting<InitiatorHelloMessage> { it.message as InitiatorHelloMessage }
                .containsExactlyInAnyOrder(initiatorHello, anotherInitiatorHello)
        }
        loggingInterceptor.assertSingleWarningContains("The sessionInit message was not sent.")
    }

    @Test
    fun `when no session exists, if BadGroupPolicyException is thrown on group policy lookup, no message is sent`() {
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties))
            .thenReturn(OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded)
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        val anotherInitiatorHello = mock<InitiatorHelloMessage>()
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(anotherInitiatorHello)
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)).thenThrow(BadGroupPolicyException("Bad group policy"))

        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(SessionManager.SessionState.CannotEstablishSession::class.java)

        loggingInterceptor.assertSingleWarningContains("Bad group policy")
        loggingInterceptor.assertSingleWarningContains("The sessionInit message was not sent.")
    }

    @Test
    fun `when no session exists, if BadGroupPolicyException is thrown on group policy lookup again, no message is sent`() {
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties))
            .thenReturn(OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded)
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY))
            .thenReturn(parameters)
            .thenThrow(BadGroupPolicyException("Bad group policy"))
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        val anotherInitiatorHello = mock<InitiatorHelloMessage>()
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(anotherInitiatorHello)

        val sessionState = sessionManager.processOutboundMessage(message)
        assertThat(sessionState).isInstanceOf(NewSessionsNeeded::class.java)

        argumentCaptor<InMemorySessionReplayer.SessionMessageReplay> {
            verify(sessionReplayer, times(2)).addMessageForReplay(
                any(),
                this.capture(),
                eq(counterparties),
            )
            assertThat(this.allValues.size).isEqualTo(2)
            assertThat(this.allValues).extracting<HoldingIdentity> {
                it.sessionCounterparties.ourId
            }.containsOnly(OUR_PARTY)
            assertThat(this.allValues).extracting<HoldingIdentity> {
                it.sessionCounterparties.counterpartyId
            }.containsOnly(PEER_PARTY)
            assertThat(this.allValues).extracting<InitiatorHelloMessage> { it.message as InitiatorHelloMessage }
                .containsExactlyInAnyOrder(initiatorHello, anotherInitiatorHello)
        }

        loggingInterceptor.assertSingleWarningContains("Bad group policy")
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
            config,
            config,
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
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMsg))

        assertThat(responseMessage!!.payload).isInstanceOf(ResponderHelloMessage::class.java)
        assertThat(responseMessage.header.address)
            .isEqualTo(PEER_ENDPOINT)
    }

    @Test
    fun `when there are multiple locally hosted identities, the member info with latest serial is used`() {
        val sessionId = "some-session-id"
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)
        val ourSecondParty = createTestHoldingIdentity("CN=Charlie, O=BigCorp, L=LDN, C=GB", GROUP_ID)
        val secondPeerEndpoint = "https://bob2.com"
        val secondPeerMemberInfo = mockMemberInfo(PEER_PARTY, secondPeerEndpoint, PEER_KEY.public, 2)
        whenever(linkManagerHostingMap.allLocallyHostedIdentities())
            .thenReturn(listOf(OUR_PARTY, ourSecondParty))
        val secondMembershipGroupReader = mock<MembershipGroupReader>()
        whenever(membershipGroupReader.lookupBySessionKey(
            eq(SecureHashImpl("SHA-256", messageDigest.hash(PEER_KEY.public.encoded))),
            eq(MembershipStatusFilter.ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING)
        )).thenReturn(secondPeerMemberInfo)
        whenever(membershipGroupReaderProvider.getGroupReader(ourSecondParty)).thenReturn(secondMembershipGroupReader)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMsg))

        assertThat(responseMessage!!.payload).isInstanceOf(ResponderHelloMessage::class.java)
        assertThat(responseMessage.header.address)
            .isEqualTo(secondPeerEndpoint)
    }

    @Test
    fun `when an initiator hello is received, we do a lookup for the public key hash, using all the locally hosted identities`() {
        val sessionId = "some-session-id"
        val carol = createTestHoldingIdentity("CN=Carol, O=Alice Corp, L=LDN, C=GB", GROUP_ID)
        val david = createTestHoldingIdentity("CN=David, O=Alice Corp, L=LDN, C=GB", GROUP_ID)
        whenever(linkManagerHostingMap.allLocallyHostedIdentities()).thenReturn(
            listOf(
                carol,
                david,
                OUR_PARTY,
            )
        )
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))

        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMsg))

        verify(otherMembershipGroupReader, times(2)).lookupBySessionKey(any(), any())
        verify(membershipGroupReaderProvider).getGroupReader(carol)
        verify(membershipGroupReaderProvider).getGroupReader(david)
    }

    @Test
    fun `when an initiator hello is received, but peer's member info is missing from network map, then message is dropped`() {
        val initiatorKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val sessionId = "some-session-id"
        val responderHello = mock<ResponderHelloMessage>()
        whenever(protocolResponder.generateResponderHello()).thenReturn(responderHello)
        whenever(
            membershipGroupReader.lookupBySessionKey(
                SecureHashImpl("SHA-256", initiatorKeyHash),
                MembershipStatusFilter.ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING
            )
        ).thenReturn(null)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorKeyHash), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMsg))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${InitiatorHelloMessage::class.java.simpleName} with sessionId ${sessionId}. " +
                "The received public key hash (${toBase64(initiatorKeyHash)}) corresponding " +
                "to one of the sender's holding identities is not in the members map. The message was discarded.")
    }

    @Test
    fun `when an initiator hello is received, if BadGroupPolicyException is thrown on group policy lookup, then the message is dropped`() {
        val initiatorKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val sessionId = "some-session-id"
        val responderHello = mock<ResponderHelloMessage>()
        whenever(protocolResponder.generateResponderHello()).thenReturn(responderHello)
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)).thenThrow(BadGroupPolicyException("Bad group policy"))

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorKeyHash), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMsg))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("Bad group policy")
        loggingInterceptor
            .assertSingleWarningContains("The ${InitiatorHelloMessage::class.java.simpleName} for sessionId $sessionId was discarded.")
    }

    @Test
    fun `when an initiator hello is received, but network type is missing from group policy provider, then message is dropped`() {
        val initiatorKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val sessionId = "some-session-id"
        val responderHello = mock<ResponderHelloMessage>()
        whenever(protocolResponder.generateResponderHello()).thenReturn(responderHello)
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)).thenReturn(null)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorKeyHash), GROUP_ID))
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
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)).thenReturn(null)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorKeyHash), GROUP_ID))
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
            InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorKeyHash), GROUP_ID))
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
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
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
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
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
            assertThat(this.allValues).extracting<SessionManager.SessionCounterparties> { it.sessionCounterparties }
                .containsOnly(counterparties)
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
        whenever(membershipGroupReader.lookup(OUR_PARTY.x500Name)).thenReturn(null)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
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
        whenever(membershipGroupReader.lookup(PEER_PARTY.x500Name)).thenReturn(null)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
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
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("The ${ResponderHelloMessage::class.java.simpleName} with sessionId $sessionId was" +
                " discarded.")
    }

    @Test
    fun `when responder hello is received, if BadGroupPolicyException is thrown on group policy lookup, message is dropped`() {
        val sessionId = "some-session"
        whenever(outboundSessionPool.constructed().first().getSession(sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(initiatorHandshakeMsg)
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)).thenThrow(BadGroupPolicyException("Bad group policy"))
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(responderHello))

        assertThat(responseMessage).isNull()
        loggingInterceptor
            .assertSingleWarningContains("Bad group policy")
        loggingInterceptor
            .assertSingleWarningContains("The ${ResponderHelloMessage::class.java.simpleName} for sessionId ${sessionId} was discarded.")
    }

    @Test
    fun `when responder hello is received, but p2p params are missing from group policy provider, message is dropped`() {
        val sessionId = "some-session"
        whenever(outboundSessionPool.constructed().first().getSession(sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(initiatorHandshakeMsg)
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)).thenReturn(null)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
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
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshakeMessage = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(
            protocolResponder.validatePeerHandshakeMessage(
                initiatorHandshakeMessage, listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256)
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
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)
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
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshakeMessage = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(
            protocolResponder.validatePeerHandshakeMessage(
                initiatorHandshakeMessage,
                listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
            )
        ).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        val responderHandshakeMsg = mock<ResponderHandshakeMessage>()
        whenever(protocolResponder.generateOurHandshakeMessage(eq(OUR_KEY.public), eq(null), any())).thenReturn(responderHandshakeMsg)
        val session = mock<Session>()
        whenever(protocolResponder.getSession()).thenReturn(session)
        sessionManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage))

        verify(otherMembershipGroupReader, atLeast(2)).lookupBySessionKey(any(), any())
        verify(membershipGroupReaderProvider, atLeastOnce()).getGroupReader(carol)
        verify(membershipGroupReaderProvider, atLeastOnce()).getGroupReader(david)
    }

    @Test
    fun `when applyNewConfiguration, after the inbound session is established, the session is removed`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val responderPublicKeyHash = messageDigest.hash(OUR_KEY.public.encoded)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(
            initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID)
        )
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshakeMessage = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(
            protocolResponder.validatePeerHandshakeMessage(
                initiatorHandshakeMessage,
                listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
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
            config,
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
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHandshakeIdentity = InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID)
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            initiatorHandshakeIdentity)
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
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(
            membershipGroupReader.lookupBySessionKey(
                SecureHashImpl("SHA-256", initiatorPublicKeyHash),
                MembershipStatusFilter.ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING
            )
        ).thenReturn(null)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId " +
                "${sessionId}. The received public key hash (${toBase64(initiatorPublicKeyHash)}) corresponding " +
                "to one of the sender's holding identities is not in the members map. The message was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but validation of the message fails due to invalid hash, the message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
        )).thenThrow(WrongPublicKeyHashException(initiatorPublicKeyHash.reversedArray(), listOf(initiatorPublicKeyHash)))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertErrorContains("The message was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but validation of the message fails due to invalid handshake, the message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
        )).thenThrow(InvalidHandshakeMessageException())
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but validation fails due to InvalidPeerCertificate, the message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        val responderPublicKeyHash = messageDigest.hash(OUR_KEY.public.encoded)
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
        )).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)?.protocolModes?.let {
            protocolResponder.validateEncryptedExtensions(
                CertificateCheckMode.NoCertificate,
                it,
                PEER_MEMBER_INFO.name
            )
        }).thenThrow(InvalidPeerCertificate("Invalid peer certificate"))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but validation fails due to NoCommonModeError, the message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        val responderPublicKeyHash = messageDigest.hash(OUR_KEY.public.encoded)
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
        )).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)?.protocolModes?.let {
            protocolResponder.validateEncryptedExtensions(
                CertificateCheckMode.NoCertificate,
                it,
                PEER_MEMBER_INFO.name
            )
        }).thenThrow(NoCommonModeError(setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION), setOf(ProtocolMode.AUTHENTICATION_ONLY)))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but our member info is missing from the network map, the message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val responderPublicKeyHash = messageDigest.hash(OUR_KEY.public.encoded)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
        )).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(linkManagerHostingMap.getInfo(responderPublicKeyHash, GROUP_ID)).thenReturn(null)
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId " +
                "${sessionId}. The received public key hash (${toBase64(responderPublicKeyHash)}) corresponding " +
                "to one of our holding identities is not in the members map. The message was discarded.")
    }

    @Test
    fun `when initiator handshake is received, if BadGroupPolicyException is thrown on group policy lookup, the message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val responderPublicKeyHash = messageDigest.hash(OUR_KEY.public.encoded)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
        )).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)).thenThrow(BadGroupPolicyException("Bad Group Policy"))
        val responseMessage = sessionManager.processSessionMessage(LinkInMessage(initiatorHandshake))

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("Bad Group Policy.")
        loggingInterceptor
            .assertSingleWarningContains("The ${InitiatorHandshakeMessage::class.java.simpleName} for sessionId $sessionId was discarded.")
    }

    @Test
    fun `when initiator handshake is received, but network type is missing from the group policy provider, the message is dropped`() {
        val sessionId = "some-session-id"
        val initiatorPublicKeyHash = messageDigest.hash(PEER_KEY.public.encoded)
        val responderPublicKeyHash = messageDigest.hash(OUR_KEY.public.encoded)
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
        )).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)).thenReturn(null)
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
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
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
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)
        whenever(linkManagerHostingMap.getInfo(responderPublicKeyHash, OUR_PARTY.groupId)).doReturn(null)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        sessionManager.processSessionMessage(LinkInMessage(initiatorHelloMessage))

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
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
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
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
            counterparties
        )
        verify(pendingSessionMessageQueues)
            .sessionNegotiatedCallback(
                sessionManager,
                counterparties,
                session,
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
        whenever(membershipGroupReader.lookup(PEER_PARTY.x500Name, MembershipStatusFilter.ACTIVE)).thenReturn(null)
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
                listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
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
                listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),

            )
        ).thenThrow(InvalidPeerCertificate("Bad Cert"))
        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()

        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, but validation fails due to InvalidSelectedModeError, the message is dropped`() {
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
                listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
            )
        ).thenThrow(InvalidSelectedModeError("Mode is invalid"))
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
                listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
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
            membershipGroupReaderProvider,
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
                configWithOneSessionBetweenMembers,
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
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
        sessionManager.processSessionMessage(LinkInMessage(responderHello))
        assertTrue(sessionManager.processOutboundMessage(message) is SessionManager.SessionState.SessionAlreadyPending)
        mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.sessionTimeout.plus(5.millis))
        verify(outboundSessionPool.constructed().last()).replaceSession(counterparties, sessionId, protocolInitiator)

        sessionManager.stop()
        resourceHolder.close()
    }

    @Test
    fun `when responder handshake is received, the session is established, if no message is sent, the session times out`() {
        val resourceHolder = ResourcesHolder()
        val sessionManager = SessionManagerImpl(
            groupPolicyProvider, membershipGroupReaderProvider,
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
                configWithOneSessionBetweenMembers,
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

        whenever(outboundSessionPool.constructed().last().replaceSession(
            eq(counterparties),
            eq(sessionId),
            any(),
        )).thenReturn(true)
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.sessionTimeout.plus(5.millis))
        verify(outboundSessionPool.constructed().last()).replaceSession(
            counterparties,
            protocolInitiator.sessionId,
            secondProtocolInitiator,
        )
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
            groupPolicyProvider, membershipGroupReaderProvider,
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
                configWithOneSessionBetweenMembers,
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

        whenever(outboundSessionPool.constructed().last().replaceSession(eq(counterparties), eq(sessionId), any())).thenReturn(true)
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(mock())
        mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.sessionTimeout.plus(5.millis))
        verify(
            outboundSessionPool.constructed().last())
            .replaceSession(
                counterparties,
                protocolInitiator.sessionId,
                secondProtocolInitiator,
            )
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
            groupPolicyProvider, membershipGroupReaderProvider,
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
                configWithOneSessionBetweenMembers,
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
            groupPolicyProvider, membershipGroupReaderProvider,
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
                configWithOneSessionBetweenMembers,
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

        whenever(outboundSessionPool.constructed().last().replaceSession(eq(counterparties), eq(sessionId), any())).thenReturn(true)
        whenever(outboundSessionPool.constructed().last().getAllSessionIds()).thenAnswer { (listOf(protocolInitiator.sessionId)) }
        startSendingHeartbeats(sessionManager)

        repeat(2) { mockTimeFacilitiesProvider.advanceTime(configWithHeartbeat.heartbeatPeriod.plus(5.millis)) }
        assertThat(linkOutMessages).isEqualTo(2)

        configHandler.applyNewConfiguration(
            configWithOneSessionBetweenMembers,
            SessionManagerImpl.SessionManagerConfig(
                2 * MAX_MESSAGE_SIZE,
                1,
                SESSIONS_PER_COUNTERPARTIES_FOR_MGM,
                RevocationCheckMode.OFF,
                SESSION_REFRESH_THRESHOLD_KEY,
            ),
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
            groupPolicyProvider, membershipGroupReaderProvider,
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
                configWithOneSessionBetweenMembers,
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

        whenever(outboundSessionPool.constructed().last().replaceSession(eq(counterparties), eq(sessionId), any())).thenReturn(true)
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
            groupPolicyProvider, membershipGroupReaderProvider,
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
                configWithOneSessionBetweenMembers,
                null,
                mock(),
            )
            heartbeatConfigHandler.applyNewConfiguration(configWithHeartbeat, null, resourcesHolder)
        }
        publisherWithDominoLogicByClientId[SessionManagerImpl.HeartbeatManager.HEARTBEAT_MANAGER_CLIENT_ID]!!.forEach {
            whenever(it.publish(any())).doAnswer { publish() }
        }
        sessionManager.start()
        whenever(outboundSessionPool.constructed().last().replaceSession(eq(counterparties), eq(sessionId), any())).thenReturn(true)
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
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))

        sessionManager.processSessionMessage(LinkInMessage(responderHello))
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        val session = mock<Session>()

        whenever(session.sessionId).doAnswer { protocolInitiator.sessionId }
        whenever(protocolInitiator.getSession()).thenReturn(session)
        whenever(outboundSessionPool.constructed().last().replaceSession(eq(counterparties), eq(sessionId), any())).thenReturn(true)
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
            counterparties
        )

        verify(sessionReplayer, times(2)).removeMessageFromReplay(
            "${protocolInitiator.sessionId}_${InitiatorHelloMessage::class.java.simpleName}",
            counterparties
        )

        verify(outboundSessionPool.constructed().last()).replaceSession(counterparties, protocolInitiator.sessionId, protocolInitiator)
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
            groupPolicyProvider, membershipGroupReaderProvider,
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
                configWithOneSessionBetweenMembers,
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
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))

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
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))

        sessionManager.processSessionMessage(LinkInMessage(responderHello))
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        val session = mock<Session>()

        whenever(session.sessionId).doAnswer{protocolInitiator.sessionId}
        whenever(protocolInitiator.getSession()).thenReturn(session)
        whenever(outboundSessionPool.constructed().last().replaceSession(eq(counterparties), eq(sessionId), any())).thenReturn(true)
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)).thenReturn(null)

        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()
        mockTimeFacilitiesProvider.advanceTime(5.days + 1.minutes)

        loggingInterceptor.assertInfoContains("Outbound session sessionId" +
                " (local=HoldingIdentity(x500Name=CN=Alice, O=Alice Corp, L=LDN, C=GB, groupId=myGroup)," +
                " remote=HoldingIdentity(x500Name=CN=Bob, O=Bob Corp, L=LDN, C=GB, groupId=myGroup))" +
                " timed out to refresh ephemeral keys and it will be cleaned up."
        )

        verify(sessionReplayer, times(2)).removeMessageFromReplay(
            "${protocolInitiator.sessionId}_${InitiatorHandshakeMessage::class.java.simpleName}",
            counterparties
        )

        verify(sessionReplayer, times(2)).removeMessageFromReplay(
            "${protocolInitiator.sessionId}_${InitiatorHelloMessage::class.java.simpleName}",
            counterparties
        )

        verify(outboundSessionPool.constructed().last()).removeSessions(counterparties)

    }

    @Test
    fun `sessions are removed even if BadGroupPolicyException is thrown on group policy lookup`() {
        whenever(outboundSessionPool.constructed().first().getSession(protocolInitiator.sessionId)).thenReturn(
            OutboundSessionPool.SessionType.PendingSession(counterparties, protocolInitiator)
        )

        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(mock())

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, protocolInitiator.sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))

        sessionManager.processSessionMessage(LinkInMessage(responderHello))
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        val session = mock<Session>()

        whenever(session.sessionId).doAnswer{protocolInitiator.sessionId}
        whenever(protocolInitiator.getSession()).thenReturn(session)
        whenever(outboundSessionPool.constructed().last().replaceSession(eq(counterparties), eq(sessionId), any())).thenReturn(true)
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(mock())
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)).thenThrow(BadGroupPolicyException("Bad group policy"))

        assertThat(sessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage))).isNull()
        mockTimeFacilitiesProvider.advanceTime(5.days + 1.minutes)

        loggingInterceptor.assertInfoContains("Outbound session sessionId" +
                " (local=HoldingIdentity(x500Name=CN=Alice, O=Alice Corp, L=LDN, C=GB, groupId=myGroup)," +
                " remote=HoldingIdentity(x500Name=CN=Bob, O=Bob Corp, L=LDN, C=GB, groupId=myGroup))" +
                " timed out to refresh ephemeral keys and it will be cleaned up."
        )

        verify(sessionReplayer, times(2)).removeMessageFromReplay(
            "${protocolInitiator.sessionId}_${InitiatorHandshakeMessage::class.java.simpleName}",
            counterparties
        )

        verify(sessionReplayer, times(2)).removeMessageFromReplay(
            "${protocolInitiator.sessionId}_${InitiatorHelloMessage::class.java.simpleName}",
            counterparties
        )

        verify(outboundSessionPool.constructed().last()).removeSessions(counterparties)

    }

    @Test
    fun `recordsForSessionEstablished returns empty list if the message convertor can not create the link out message`() {
        val session = mock<Session> {
            on { sessionId } doReturn "sessionId"
        }
        val initiatorHello = mock<InitiatorHelloMessage>()
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties)).thenReturn(
            OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded
        )
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        sessionManager.processOutboundMessage(message)

        val records = sessionManager.recordsForSessionEstablished(session, message, 1L)

        assertThat(records).isEmpty()
    }

    @Test
    fun `recordsForSessionEstablished returns marker and message`() {
        whenever(authenticatedSession.sessionId).doReturn("sessionId")
        val initiatorHello = mock<InitiatorHelloMessage>()
        val header = CommonHeader(
            MessageType.RESPONDER_HANDSHAKE,
            1,
            "sessionId",
            4,
            300,
        )
        whenever(protocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        whenever(outboundSessionPool.constructed().first().getNextSession(counterparties)).thenReturn(
            OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded
        )
        whenever(secondProtocolInitiator.generateInitiatorHello()).thenReturn(initiatorHello)
        whenever(authenticatedSession.createMac(any())).doReturn(
            AuthenticationResult(
                header,
                byteArrayOf(1, 4),
            )
        )
        sessionManager.processOutboundMessage(message)

        val records = sessionManager.recordsForSessionEstablished(authenticatedSession, message, 1L)

        assertThat(records)
            .hasSize(2)
            .anySatisfy { record ->
                assertThat(record.topic).isEqualTo(LINK_OUT_TOPIC)
                assertThat(record.value).isInstanceOf(LinkOutMessage::class.java)
                val msg = record.value as? LinkOutMessage

                assertThat(msg?.payload).isInstanceOf(AuthenticatedDataMessage::class.java)
                val payload = msg?.payload as? AuthenticatedDataMessage
                assertThat(payload?.header).isEqualTo(header)
                assertThat(payload?.authTag?.array()).isEqualTo(byteArrayOf(1, 4))

                assertThat(msg?.header?.destinationIdentity).isEqualTo(PEER_PARTY.toAvro())
                assertThat(msg?.header?.sourceIdentity).isEqualTo(OUR_PARTY.toAvro())
                assertThat(msg?.header?.destinationNetworkType).isEqualTo(NetworkType.CORDA_5)
                assertThat(msg?.header?.address).isEqualTo("http://bob.com")
            }.anySatisfy { record ->
                assertThat(record.topic).isEqualTo(P2P_OUT_MARKERS)
                assertThat(record.key).isEqualTo("messageId")
                assertThat(record.value).isInstanceOf(AppMessageMarker::class.java)
            }
    }

    @Test
    fun `first dataMessageReceived call schedules a timeout on the inbound session`() {
        val sessionId = UUID(0, 1).toString()
        sessionManager.dataMessageReceived(sessionId, PEER_PARTY, OUR_PARTY)

        verify(mockTimeFacilitiesProvider.mockScheduledExecutor).schedule(
            any(),
            eq(sixDaysInMillis),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `dataMessageReceived calls after the first do not schedule a timeout on the inbound session`() {
        val sessionId = UUID(0, 1).toString()

        // call twice
        sessionManager.dataMessageReceived(sessionId, PEER_PARTY, OUR_PARTY)
        sessionManager.dataMessageReceived(sessionId, PEER_PARTY, OUR_PARTY)

        // verify schedule called only once
        verify(mockTimeFacilitiesProvider.mockScheduledExecutor).schedule(
            any(),
            eq(sixDaysInMillis),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `inbound session timeout is not rescheduled if session has timed out after dataMessageReceived`() {
        val sessionId = UUID(0, 1).toString()
        sessionManager.dataMessageReceived(sessionId, PEER_PARTY, OUR_PARTY)

        mockTimeFacilitiesProvider.advanceTime(Duration.ofMillis(sixDaysInMillis))

        loggingInterceptor.assertInfoContains("Inbound session $sessionId has not received any messages for the " +
                "configured timeout threshold ($sixDaysInMillis ms) so it will be cleaned up.")

        // Check timeout was not rescheduled (only scheduled once initially but not again after session timeout)
        verify(mockTimeFacilitiesProvider.mockScheduledExecutor).schedule(
            any(),
            eq(sixDaysInMillis),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `inbound session timeout is rescheduled if session has not timed out after dataMessageReceived`() {
        val sessionId = UUID(0, 1).toString()
        sessionManager.dataMessageReceived(sessionId, PEER_PARTY, OUR_PARTY)

        mockTimeFacilitiesProvider.advanceTime(Duration.ofMillis(sixDaysInMillis / 2))

        sessionManager.dataMessageReceived(sessionId, PEER_PARTY, OUR_PARTY)

        mockTimeFacilitiesProvider.advanceTime(Duration.ofMillis(sixDaysInMillis / 2))

        // Check timeout was rescheduled
        verify(mockTimeFacilitiesProvider.mockScheduledExecutor).schedule(
            any(),
            eq(sixDaysInMillis / 2),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `first sessionMessageReceived call schedules a timeout on the inbound session`() {
        val sessionId = UUID(0, 1).toString()
        sessionManager.sessionMessageReceived(sessionId, PEER_PARTY, OUR_PARTY)

        verify(mockTimeFacilitiesProvider.mockScheduledExecutor).schedule(
            any(),
            eq(sixDaysInMillis),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `sessionMessageReceived calls after the first do not schedule a timeout on the inbound session`() {
        val sessionId = UUID(0, 1).toString()

        // call twice
        sessionManager.sessionMessageReceived(sessionId, PEER_PARTY, OUR_PARTY)
        sessionManager.sessionMessageReceived(sessionId, PEER_PARTY, OUR_PARTY)

        // verify schedule called only once
        verify(mockTimeFacilitiesProvider.mockScheduledExecutor).schedule(
            any(),
            eq(sixDaysInMillis),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `inbound session timeout is not rescheduled if session has timed out after sessionMessageReceived`() {
        val sessionId = UUID(0, 1).toString()
        sessionManager.sessionMessageReceived(sessionId, PEER_PARTY, OUR_PARTY)

        mockTimeFacilitiesProvider.advanceTime(Duration.ofMillis(sixDaysInMillis))

        loggingInterceptor.assertInfoContains("Inbound session $sessionId has not received any messages for the " +
                "configured timeout threshold ($sixDaysInMillis ms) so it will be cleaned up.")

        // Check timeout was not rescheduled (only scheduled once initially but not again after session timeout)
        verify(mockTimeFacilitiesProvider.mockScheduledExecutor).schedule(
            any(),
            eq(sixDaysInMillis),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `inbound session timeout is rescheduled if session has not timed out after sessionMessageReceived`() {
        val sessionId = UUID(0, 1).toString()
        sessionManager.sessionMessageReceived(sessionId, PEER_PARTY, OUR_PARTY)

        mockTimeFacilitiesProvider.advanceTime(Duration.ofMillis(sixDaysInMillis / 2))

        sessionManager.sessionMessageReceived(sessionId, PEER_PARTY, OUR_PARTY)

        mockTimeFacilitiesProvider.advanceTime(Duration.ofMillis(sixDaysInMillis / 2))

        // Check timeout was rescheduled
        verify(mockTimeFacilitiesProvider.mockScheduledExecutor).schedule(
            any(),
            eq(sixDaysInMillis / 2),
            eq(TimeUnit.MILLISECONDS)
        )
    }
}
