package net.corda.p2p.linkmanager.sessions

import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.SecureHashImpl
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.MessageType
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.data.p2p.crypto.ResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.data.p2p.crypto.internal.InitiatorHandshakeIdentity
import net.corda.data.p2p.crypto.protocol.RevocationCheckMode
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.HandshakeIdentityData
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeMessageException
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeResponderKeyHash
import net.corda.p2p.crypto.protocol.api.InvalidPeerCertificate
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.crypto.protocol.api.WrongPublicKeyHashException
import net.corda.p2p.linkmanager.hosting.HostingMapListener
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.linkmanager.utilities.mockMemberInfo
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.EncodingUtils.toBase64
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.Key
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.time.Instant
import net.corda.p2p.crypto.protocol.api.CertificateCheckMode
import net.corda.p2p.crypto.protocol.api.InvalidSelectedModeError
import net.corda.p2p.crypto.protocol.api.NoCommonModeError
import net.corda.p2p.linkmanager.grouppolicy.protocolModes
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl.SessionManagerConfigChangeHandler

class SessionManagerTest {

    private companion object {
        const val GROUP_ID = "myGroup"
        const val MAX_MESSAGE_SIZE = 1024 * 1024
        val RANDOM_BYTES = ByteBuffer.wrap("some-random-data".toByteArray())

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
    }

    private lateinit var configHandler: SessionManagerConfigChangeHandler

    @Suppress("UNCHECKED_CAST")
    private val dominoTile = Mockito.mockConstruction(ComplexDominoTile::class.java) { mock, context ->
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        whenever(mock.withLifecycleWriteLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        when(val seventhArg = context.arguments()[6]) {
            is SessionManagerConfigChangeHandler -> configHandler = seventhArg
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

    private val config = SessionManagerImpl.SessionManagerConfig(
        MAX_MESSAGE_SIZE,
        RevocationCheckMode.OFF,
    )

    private val sessionManager = createSessionManager(mock())

    private fun createSessionManager(
        sessionManagerConfig: SessionManagerImpl.SessionManagerConfig,
    ): SessionManagerImpl {
        return SessionManagerImpl(
            groupPolicyProvider,
            membershipGroupReaderProvider,
            cryptoOpsClient,
            pendingSessionMessageQueues,
            mock(),
            mock(),
            mock(),
            mock(),
            linkManagerHostingMap,
            protocolFactory,
            mock(),
        ).apply {
            setRunning()
            configHandler.applyNewConfiguration(
                sessionManagerConfig,
                null,
                mock(),
            )
        }
    }

    private fun MessageDigest.hash(data: ByteArray): ByteArray {
        this.reset()
        this.update(data)
        return digest()
    }

    private fun setRunning() {
        for (tile in dominoTile.constructed()) {
            whenever(tile.isRunning).doReturn(true)
        }
    }

    @Test
    fun `when we applyNewConfiguration, then all queues are destroyed and the outbound session pool is cleared`() {
        configHandler.applyNewConfiguration(
            config,
            config,
            mock(),
        )
        verify(pendingSessionMessageQueues, times(1)).destroyAllQueues()
    }

    @Test
    fun `when an initiator hello is received, a responder hello is returned`() {
        val sessionId = "some-session-id"
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val header = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHelloMsg = InitiatorHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded),
            InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID))
        val responseMessage = sessionManager.processInitiatorHello(initiatorHelloMsg)?.first

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
        val responseMessage = sessionManager.processInitiatorHello(initiatorHelloMsg)?.first

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

        sessionManager.processInitiatorHello(initiatorHelloMsg)

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
        val responseMessage =
            sessionManager.processInitiatorHello(initiatorHelloMsg)?.first

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
        val responseMessage = sessionManager.processInitiatorHello(initiatorHelloMsg)?.first

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
        val responseMessage = sessionManager.processInitiatorHello(initiatorHelloMsg)?.first

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
        val responseMessage =
            sessionManager.processInitiatorHello(initiatorHelloMsg)?.first

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
        val responseMessage =
            sessionManager.processInitiatorHello(initiatorHelloMsg)?.first

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("There is no locally hosted identity in group $GROUP_ID.")
        loggingInterceptor
            .assertSingleWarningContains("The initiator message was discarded.")
    }


    @Test
    fun `when responder hello is received with an existing session, an initiator handshake is returned`() {
        val sessionId = "some-session"
        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(initiatorHandshakeMsg)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
        val responseMessage =
            sessionManager.processResponderHello(
                counterparties,
                protocolInitiator,
                responderHello,
            )?.first

        assertThat(responseMessage!!.payload).isEqualTo(initiatorHandshakeMsg)
    }

    @Test
    fun `when responder hello is received, but our member info is missing from network map, message is dropped`() {
        val sessionId = "some-session"

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(initiatorHandshakeMsg)
        whenever(linkManagerHostingMap.getInfo(OUR_PARTY)).thenReturn(null)
        whenever(membershipGroupReader.lookup(OUR_PARTY.x500Name)).thenReturn(null)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
        val responseMessage =
            sessionManager.processResponderHello(
                counterparties,
                protocolInitiator,
                responderHello,
            )?.first

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${ResponderHelloMessage::class.java.simpleName} with sessionId $sessionId but " +
                "cannot find public key for our identity $OUR_PARTY. The message was discarded.")
    }

    @Test
    fun `when responder hello is received, but peer's member info is missing from network map, message is dropped`() {
        val sessionId = "some-session"

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(initiatorHandshakeMsg)
        whenever(membershipGroupReader.lookup(PEER_PARTY.x500Name)).thenReturn(null)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
        val responseMessage =
            sessionManager.processResponderHello(
                counterparties,
                protocolInitiator,
                responderHello,
            )?.first

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarning("Received ${ResponderHelloMessage::class.java.simpleName} with sessionId $sessionId from " +
                "peer $PEER_PARTY which is not in the members map. The message was discarded.")
    }

    @Test
    fun `when responder hello is received, but private key cannot be found to sign, message is dropped`() {
        val sessionId = "some-session"

        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any()))
            .thenThrow(CordaRuntimeException("Nop"))
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
        val responseMessage =
            sessionManager.processResponderHello(
                counterparties,
                protocolInitiator,
                responderHello,
            )?.first

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("The ${ResponderHelloMessage::class.java.simpleName} with sessionId $sessionId was" +
                " discarded.")
    }

    @Test
    fun `when responder hello is received, if BadGroupPolicyException is thrown on group policy lookup, message is dropped`() {
        val sessionId = "some-session"

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(initiatorHandshakeMsg)
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)).thenThrow(BadGroupPolicyException("Bad group policy"))
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
        val responseMessage =
            sessionManager.processResponderHello(
                counterparties,
                protocolInitiator,
                responderHello,
            )?.first

        assertThat(responseMessage).isNull()
        loggingInterceptor
            .assertSingleWarningContains("Bad group policy")
        loggingInterceptor
            .assertSingleWarningContains("The ${ResponderHelloMessage::class.java.simpleName} for sessionId ${sessionId} was discarded.")
    }

    @Test
    fun `when responder hello is received, but p2p params are missing from group policy provider, message is dropped`() {
        val sessionId = "some-session"

        val initiatorHandshakeMsg = mock<InitiatorHandshakeMessage>()
        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(initiatorHandshakeMsg)
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)).thenReturn(null)
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
        val responseMessage =
            sessionManager.processResponderHello(
                counterparties,
                protocolInitiator,
                responderHello,
            )?.first

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
        sessionManager.processInitiatorHello(initiatorHelloMessage)

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
        val responseMessage = 
            sessionManager.processInitiatorHandshake(
                protocolResponder,
                initiatorHandshakeMessage,
            )

        assertThat(responseMessage!!.payload).isEqualTo(responderHandshakeMsg)
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
        sessionManager.processInitiatorHello(initiatorHelloMessage)

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
        sessionManager.processInitiatorHandshake(
            protocolResponder,
            initiatorHandshakeMessage,
        )

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
        sessionManager.processInitiatorHello(initiatorHelloMessage)

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
        val responseMessage =
            sessionManager.processInitiatorHandshake(
                protocolResponder,
                initiatorHandshakeMessage,
            )

        assertThat(responseMessage!!.payload).isEqualTo(responderHandshakeMsg)
    }

    @Test
    fun `when initiator handshake is received but no locally hosted identity in the same group as the initiator, message is dropped`() {
        val sessionId = "some-session-id"
        whenever(protocolResponder.generateResponderHello()).thenReturn(mockResponderHelloMessage)

        val initiatorHelloHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, sessionId, 1, Instant.now().toEpochMilli())
        val initiatorHandshakeIdentity = InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(PEER_KEY.public.encoded)), GROUP_ID)
        val initiatorHelloMessage = InitiatorHelloMessage(initiatorHelloHeader, ByteBuffer.wrap(PEER_KEY.public.encoded),
            initiatorHandshakeIdentity)
        sessionManager.processInitiatorHello(
            initiatorHelloMessage,
        )

        whenever(linkManagerHostingMap.allLocallyHostedIdentities()).thenReturn(emptySet())
        whenever(protocolResponder.getInitiatorIdentity()).thenReturn(initiatorHandshakeIdentity)
        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)

        val responseMessage = sessionManager.processInitiatorHandshake(
            protocolResponder,
            initiatorHandshake,
        )

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
        sessionManager.processInitiatorHello(initiatorHelloMessage)

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
        val responseMessage = sessionManager.processInitiatorHandshake(
            protocolResponder,
            initiatorHandshake,
        )

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
        sessionManager.processInitiatorHello(initiatorHelloMessage)

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
        )).thenThrow(WrongPublicKeyHashException(initiatorPublicKeyHash.reversedArray(), listOf(initiatorPublicKeyHash)))
        val responseMessage = sessionManager.processInitiatorHandshake(
            protocolResponder,
            initiatorHandshake,
        )

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
        sessionManager.processInitiatorHello(initiatorHelloMessage)

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
        )).thenThrow(InvalidHandshakeMessageException())
        val responseMessage = sessionManager.processInitiatorHandshake(
            protocolResponder,
            initiatorHandshake,
        )

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
        sessionManager.processInitiatorHello(initiatorHelloMessage)

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
        val responseMessage = sessionManager.processInitiatorHandshake(protocolResponder, initiatorHandshake)

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
        sessionManager.processInitiatorHello(initiatorHelloMessage)

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
        val responseMessage = sessionManager.processInitiatorHandshake(
            protocolResponder,
            initiatorHandshake
        )

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
        sessionManager.processInitiatorHello(initiatorHelloMessage)

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
        )).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(linkManagerHostingMap.getInfo(responderPublicKeyHash, GROUP_ID)).thenReturn(null)
        val responseMessage = sessionManager.processInitiatorHandshake(
            protocolResponder,
            initiatorHandshake,
        )

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
        sessionManager.processInitiatorHello(initiatorHelloMessage)

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
        )).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)).thenThrow(BadGroupPolicyException("Bad Group Policy"))
        
        val responseMessage = sessionManager.processInitiatorHandshake(protocolResponder, initiatorHandshake)

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
        sessionManager.processInitiatorHello(initiatorHelloMessage)

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
        )).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        whenever(groupPolicyProvider.getP2PParameters(OUR_PARTY)).thenReturn(null)
        val responseMessage =
            sessionManager.processInitiatorHandshake(protocolResponder, initiatorHandshake)

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
        sessionManager.processInitiatorHello(initiatorHelloMessage)

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
        val responseMessage = sessionManager.processInitiatorHandshake(protocolResponder, initiatorHandshake)

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
        sessionManager.processInitiatorHello(initiatorHelloMessage)

        val initiatorHandshakeHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, 1, sessionId, 3, Instant.now().toEpochMilli())
        val initiatorHandshake = InitiatorHandshakeMessage(initiatorHandshakeHeader, RANDOM_BYTES, RANDOM_BYTES)
        whenever(protocolResponder.getInitiatorIdentity())
            .thenReturn(InitiatorHandshakeIdentity(ByteBuffer.wrap(initiatorPublicKeyHash), GROUP_ID))
        whenever(protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshake,
            listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
        )).thenReturn(HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, GROUP_ID))
        val responseMessage = sessionManager.processInitiatorHandshake(protocolResponder, initiatorHandshake)

        assertThat(responseMessage).isNull()
        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, no message is returned and session is established`() {
        val someSessionId = "some-session"

        whenever(protocolInitiator.generateOurHandshakeMessage(eq(PEER_KEY.public), eq(null), any())).thenReturn(mock())
        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, someSessionId, 4, Instant.now().toEpochMilli())
        val responderHello = ResponderHelloMessage(header, ByteBuffer.wrap(PEER_KEY.public.encoded))
        sessionManager.processResponderHello(
            counterparties,
            protocolInitiator,
            responderHello,
        )

        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        val session = mock<Session> {
            on { sessionId } doReturn someSessionId
        }
        whenever(protocolInitiator.getSession()).thenReturn(session)
        assertThat(sessionManager.processResponderHandshake(responderHandshakeMessage, counterparties, protocolInitiator)).isNotNull()
    }

    @Test
    fun `when responder handshake is received, but peer's member info is missing from network map, the message is dropped`() {
        val sessionId = "some-session"

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(membershipGroupReader.lookup(PEER_PARTY.x500Name, MembershipStatusFilter.ACTIVE)).thenReturn(null)
        assertThat(
            sessionManager.processResponderHandshake(
                responderHandshakeMessage,
                counterparties,
                protocolInitiator,
            )
        ).isNull()

        loggingInterceptor.assertSingleWarning("Received ${ResponderHandshakeMessage::class.java.simpleName} with sessionId $sessionId " +
                "from peer $PEER_PARTY which is not in the members map. The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, but validation fails due to invalid key hash, the message is dropped`() {
        val sessionId = "some-session"

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(
            protocolInitiator.validatePeerHandshakeMessage(
                responderHandshakeMessage,
                PEER_MEMBER_INFO.holdingIdentity.x500Name,
                listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
            )
        ).thenThrow(InvalidHandshakeResponderKeyHash())
        assertThat(
            sessionManager.processResponderHandshake(
                responderHandshakeMessage,
                counterparties,
                protocolInitiator,
            )
        ).isNull()

        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, but validation fails due to InvalidPeerCertificate, the message is dropped`() {
        val sessionId = "some-session"

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(
            protocolInitiator.validatePeerHandshakeMessage(
                responderHandshakeMessage,
                PEER_MEMBER_INFO.holdingIdentity.x500Name,
                listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),

            )
        ).thenThrow(InvalidPeerCertificate("Bad Cert"))
        assertThat(
            sessionManager.processResponderHandshake(
                responderHandshakeMessage,
                counterparties,
                protocolInitiator,
            )
        ).isNull()

        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, but validation fails due to InvalidSelectedModeError, the message is dropped`() {
        val sessionId = "some-session"

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(
            protocolInitiator.validatePeerHandshakeMessage(
                responderHandshakeMessage,
                PEER_MEMBER_INFO.holdingIdentity.x500Name,
                listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
            )
        ).thenThrow(InvalidSelectedModeError("Mode is invalid"))
        assertThat(
            sessionManager.processResponderHandshake(
                responderHandshakeMessage,
                counterparties,
                protocolInitiator,
            )
        ).isNull()

        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }

    @Test
    fun `when responder handshake is received, but validation fails due to invalid handshake, the message is dropped`() {
        val sessionId = "some-session"

        val header = CommonHeader(MessageType.RESPONDER_HANDSHAKE, 1, sessionId, 4, Instant.now().toEpochMilli())
        val responderHandshakeMessage = ResponderHandshakeMessage(header, RANDOM_BYTES, RANDOM_BYTES)
        whenever(
            protocolInitiator.validatePeerHandshakeMessage(
                responderHandshakeMessage,
                PEER_MEMBER_INFO.holdingIdentity.x500Name,
                listOf(PEER_KEY.public to SignatureSpecs.ECDSA_SHA256),
            )
        ).thenThrow(InvalidHandshakeMessageException())
        assertThat(
            sessionManager.processResponderHandshake(
                responderHandshakeMessage,
                counterparties,
                protocolInitiator,
            )
        ).isNull()

        loggingInterceptor.assertSingleWarningContains("The message was discarded.")
    }
}
