package net.corda.p2p.linkmanager.delivery

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.CertificateCheckMode
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.linkmanager.utilities.mockMembers
import net.corda.p2p.linkmanager.utilities.mockMembersAndGroups
import net.corda.schema.Schemas.P2P.LINK_OUT_TOPIC
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.MockTimeFacilitiesProvider
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.util.UUID

class InMemorySessionReplayerTest {

    private companion object {
        const val GROUP_ID = "myGroup"
        val US = createTestHoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB",GROUP_ID)
        val COUNTER_PARTY = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", GROUP_ID)
        const val SERIAL = 1L
        val SESSION_COUNTERPARTIES = SessionManager.SessionCounterparties(
            US, COUNTER_PARTY, MembershipStatusFilter.ACTIVE, SERIAL
        )
        val id = UUID.randomUUID().toString()
        const val MAX_MESSAGE_SIZE = 100000
        lateinit var loggingInterceptor: LoggingInterceptor

        val KEY_PAIR = KeyPairGenerator.getInstance("EC", BouncyCastleProvider()).genKeyPair()

        @BeforeAll
        @JvmStatic
        fun setup() {
            loggingInterceptor = LoggingInterceptor.setupLogging()
        }
    }

    @AfterEach
    fun cleanUp() {
        dominoTile.close()
        publisherWithDominoLogic.close()
        replayScheduler.close()
        loggingInterceptor.reset()
    }

    private val dominoTile = Mockito.mockConstruction(ComplexDominoTile::class.java) { mock, _ ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
    }
    private val publisherWithDominoLogic = Mockito.mockConstruction(PublisherWithDominoLogic::class.java) { mock, _ ->
        val mockDominoTile = mock<DominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        whenever(mock.dominoTile).thenReturn(mockDominoTile)
    }

    private lateinit var replayCallback: (message: InMemorySessionReplayer.SessionMessageReplay) -> Unit
    private val replayScheduler = Mockito.mockConstruction(ReplayScheduler::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        replayCallback = context.arguments()[3] as (message: InMemorySessionReplayer.SessionMessageReplay) -> Unit
        val mockDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        whenever(mock.dominoTile).thenReturn(mockDominoTile)
    }

    private val groupsAndMembers = mockMembersAndGroups(US, COUNTER_PARTY)

    private val mockTimeFacilitiesProvider = MockTimeFacilitiesProvider()

    @Test
    fun `The InMemorySessionReplacer adds a message to be replayed (by the replayScheduler) when addMessageForReplay`() {
        val replayer = InMemorySessionReplayer(mock(), mock(), mock(), mock(), groupsAndMembers.second, groupsAndMembers.first,
            mockTimeFacilitiesProvider.clock)
        val helloMessage = AuthenticationProtocolInitiator(
            id,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID,
            CertificateCheckMode.NoCertificate,
        ).generateInitiatorHello()

        setRunning()
        val messageReplay = InMemorySessionReplayer.SessionMessageReplay(helloMessage, id, SESSION_COUNTERPARTIES) { _,_ -> }
        replayer.addMessageForReplay(id, messageReplay, SESSION_COUNTERPARTIES)
        @Suppress("UNCHECKED_CAST")
        verify(replayScheduler.constructed().last()
                as ReplayScheduler<SessionManager.SessionCounterparties, InMemorySessionReplayer.SessionMessageReplay>)
            .addForReplay(any(), eq(id), eq(messageReplay), eq(SESSION_COUNTERPARTIES))
    }

    @Test
    fun `The InMemorySessionReplacer removes a message from the replayScheduler when removeMessageFromReplay`() {
        val replayer = InMemorySessionReplayer(mock(), mock(), mock(), mock(), groupsAndMembers.second, groupsAndMembers.first,
            mockTimeFacilitiesProvider.clock)
        setRunning()
        replayer.removeMessageFromReplay(id, SESSION_COUNTERPARTIES)
        @Suppress("UNCHECKED_CAST")
        verify(replayScheduler.constructed().last()
                as ReplayScheduler<SessionManager.SessionCounterparties, InMemorySessionReplayer.SessionMessageReplay>)
            .removeFromReplay(id, SESSION_COUNTERPARTIES)
    }

    @Test
    fun `The InMemorySessionReplacer removes a message from the replayScheduler when removeAllMessageFromReplay`() {
        val replayer = InMemorySessionReplayer(mock(), mock(), mock(), mock(), groupsAndMembers.second, groupsAndMembers.first,
            mockTimeFacilitiesProvider.clock)

        setRunning()
        replayer.removeAllMessagesFromReplay()
        @Suppress("UNCHECKED_CAST")
        verify(replayScheduler.constructed().last()
                as ReplayScheduler<SessionManager.SessionCounterparties, InMemorySessionReplayer.SessionMessageReplay>)
            .removeAllMessagesFromReplay()
    }

    @Test
    fun `The replaySchedular callback publishes the session message`() {
        InMemorySessionReplayer(mock(), mock(), mock(), mock(), groupsAndMembers.second, groupsAndMembers.first,
            mockTimeFacilitiesProvider.clock)
        val helloMessage = AuthenticationProtocolInitiator(
            id,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID,
            CertificateCheckMode.NoCertificate,
        ).generateInitiatorHello()

        setRunning()
        var sessionId: String? = null
        var counterparties: SessionManager.SessionCounterparties? = null
        val messageReplay = InMemorySessionReplayer.SessionMessageReplay(helloMessage, id, SESSION_COUNTERPARTIES) { key, callbackId  ->
            counterparties = key
            sessionId = callbackId
        }
        replayCallback(messageReplay)

        val recordsCapture = argumentCaptor<List<Record<*, *>>>()
        verify(publisherWithDominoLogic.constructed().last()).publish(recordsCapture.capture())
        val record = recordsCapture.allValues.single().single()
        assertThat(record.topic).isEqualTo(LINK_OUT_TOPIC)
        assertThat((record.value as? LinkOutMessage)?.payload).isEqualTo(helloMessage)
        assertThat(counterparties!!.ourId).isEqualTo(US)
        assertThat(counterparties!!.counterpartyId).isEqualTo(COUNTER_PARTY)
        assertThat(sessionId).isEqualTo(id)
    }

    @Test
    fun `The replaySchedular callback logs a warning when our network type is not in the network map`() {
        val parameters = mock<GroupPolicy.P2PParameters> {
            on { tlsPki } doReturn GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode.STANDARD
        }
        val groupPolicy = mock<GroupPolicy> {
            on { p2pParameters } doReturn parameters
        }
        val groups = mock<GroupPolicyProvider> {
            on {getGroupPolicy(any()) } doReturnConsecutively listOf(null, groupPolicy)
        }

        InMemorySessionReplayer(mock(), mock(), mock(), mock(), groups, groupsAndMembers.first, mockTimeFacilitiesProvider.clock)
        val helloMessage = AuthenticationProtocolInitiator(
            id,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID,
            CertificateCheckMode.NoCertificate
        ).generateInitiatorHello()

        setRunning()
        val messageReplay = InMemorySessionReplayer.SessionMessageReplay(helloMessage, id, SESSION_COUNTERPARTIES) { _,_ -> }
        replayCallback(messageReplay)

        loggingInterceptor.assertSingleWarning("Attempted to replay a session negotiation message (type " +
            "${InitiatorHelloMessage::class.java.simpleName}) but could not find the network type in the GroupPolicyProvider for" +
            " $US. The message was not replayed.")
    }

    @Test
    fun `The replaySchedular callback logs a warning when the responder is not in the network map`() {
        val memberInfo = groupsAndMembers.first.getGroupReader(US).lookup(COUNTER_PARTY.x500Name)
        val membershipGroupReader = mock<MembershipGroupReader> {
            on { lookup(COUNTER_PARTY.x500Name) } doReturnConsecutively listOf(null, memberInfo)
        }
        val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider> {
            on { getGroupReader(US) } doReturn membershipGroupReader
        }
        InMemorySessionReplayer(
            mock(),
            mock(),
            mock(),
            mock(),
            groupsAndMembers.second,
            membershipGroupReaderProvider,
            mockTimeFacilitiesProvider.clock,
        )
        val helloMessage = AuthenticationProtocolInitiator(
            id,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID,
            CertificateCheckMode.NoCertificate
        ).generateInitiatorHello()

        setRunning()
        val messageReplay = InMemorySessionReplayer.SessionMessageReplay(helloMessage, id, SESSION_COUNTERPARTIES) { _,_ -> }
        replayCallback(messageReplay)

        loggingInterceptor.assertSingleWarning("Attempted to replay a session negotiation message (type " +
            "${InitiatorHelloMessage::class.java.simpleName}) with peer $COUNTER_PARTY with serial $SERIAL which is not" +
                " in the members map. The message was not replayed.")
    }

    @Test
    fun `The replaySchedular callback logs a warning when the responder is not in the network map with the required serial`() {
        val membershipGroupReaderProvider = mockMembers(listOf(COUNTER_PARTY), 2)
        InMemorySessionReplayer(
            mock(),
            mock(),
            mock(),
            mock(),
            groupsAndMembers.second,
            membershipGroupReaderProvider,
            mockTimeFacilitiesProvider.clock,
        )
        val helloMessage = AuthenticationProtocolInitiator(
            id,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID,
            CertificateCheckMode.NoCertificate
        ).generateInitiatorHello()

        setRunning()
        val messageReplay = InMemorySessionReplayer.SessionMessageReplay(helloMessage, id, SESSION_COUNTERPARTIES) { _,_ -> }
        replayCallback(messageReplay)

        loggingInterceptor.assertSingleWarning("Attempted to replay a session negotiation message (type " +
                "${InitiatorHelloMessage::class.java.simpleName}) with peer $COUNTER_PARTY with serial $SERIAL which " +
                "is not in the members map. The message was not replayed.")
    }

    @Test
    fun `The InMemorySessionReplayer will not replay before start`() {
        val helloMessage = AuthenticationProtocolInitiator(
            "",
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID,
            CertificateCheckMode.NoCertificate
        ).generateInitiatorHello()
        val replayer = InMemorySessionReplayer(mock(), mock(), mock(), mock(),
            groupsAndMembers.second, groupsAndMembers.first, mockTimeFacilitiesProvider.clock)
        assertThrows<IllegalStateException> {
            replayer.addMessageForReplay(
                "",
                InMemorySessionReplayer.SessionMessageReplay(helloMessage, "", SESSION_COUNTERPARTIES) {_, _->},
                SESSION_COUNTERPARTIES
            )
        }
    }

    private fun setRunning() {
        whenever(dominoTile.constructed().first().isRunning).doReturn(true)
    }
}
