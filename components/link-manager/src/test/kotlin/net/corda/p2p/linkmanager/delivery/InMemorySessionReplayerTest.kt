package net.corda.p2p.linkmanager.delivery

import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.records.Record
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.linkmanager.GroupPolicyListener
import net.corda.p2p.linkmanager.LinkManagerGroupPolicyProvider
import net.corda.p2p.linkmanager.LinkManagerInternalTypes
import net.corda.p2p.linkmanager.LinkManagerMembershipGroupReader
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.linkmanager.utilities.mockMembersAndGroups
import net.corda.schema.Schemas.P2P.Companion.LINK_OUT_TOPIC
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
import java.util.*

class InMemorySessionReplayerTest {

    companion object {
        private const val GROUP_ID = "myGroup"
        private val US = LinkManagerInternalTypes.HoldingIdentity("Us",GROUP_ID)
        private val COUNTER_PARTY = LinkManagerInternalTypes.HoldingIdentity("CounterParty", GROUP_ID)
        private val SESSION_COUNTERPARTIES = SessionManager.SessionCounterparties(US, COUNTER_PARTY)
        private const val MAX_MESSAGE_SIZE = 100000
        lateinit var loggingInterceptor: LoggingInterceptor

        private val KEY_PAIR = KeyPairGenerator.getInstance("EC", BouncyCastleProvider()).genKeyPair()

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
    }
    private val publisherWithDominoLogic = Mockito.mockConstruction(PublisherWithDominoLogic::class.java)

    private lateinit var replayCallback: (message: InMemorySessionReplayer.SessionMessageReplay) -> Unit
    private val replayScheduler = Mockito.mockConstruction(ReplayScheduler::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        replayCallback = context.arguments()[4] as (message: InMemorySessionReplayer.SessionMessageReplay) -> Unit
    }

    private val groupsAndMembers = mockMembersAndGroups(US, COUNTER_PARTY)

    @Test
    fun `The InMemorySessionReplacer adds a message to be replayed (by the replayScheduler) when addMessageForReplay`() {
        val replayer = InMemorySessionReplayer(mock(), mock(), mock(), mock(), groupsAndMembers.second, groupsAndMembers.first)

        val id = UUID.randomUUID().toString()
        val helloMessage = AuthenticationProtocolInitiator(
            id,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID
        ).generateInitiatorHello()

        setRunning()
        val messageReplay = InMemorySessionReplayer.SessionMessageReplay(helloMessage, id, US, COUNTER_PARTY) { _,_ -> }
        replayer.addMessageForReplay(id, messageReplay, SESSION_COUNTERPARTIES)
        @Suppress("UNCHECKED_CAST")
        verify(replayScheduler.constructed().last() as ReplayScheduler<InMemorySessionReplayer.SessionMessageReplay>)
            .addForReplay(any(), eq(id), eq(messageReplay), eq(SESSION_COUNTERPARTIES))
    }

    @Test
    fun `The InMemorySessionReplacer removes a message from the replayScheduler when removeMessageFromReplay`() {
        val replayer = InMemorySessionReplayer(mock(), mock(), mock(), mock(), groupsAndMembers.second, groupsAndMembers.first)

        val id = UUID.randomUUID().toString()
        setRunning()
        replayer.removeMessageFromReplay(id, SESSION_COUNTERPARTIES)
        @Suppress("UNCHECKED_CAST")
        verify(replayScheduler.constructed().last() as ReplayScheduler<InMemorySessionReplayer.SessionMessageReplay>)
            .removeFromReplay(id, SESSION_COUNTERPARTIES)
    }

    @Test
    fun `The InMemorySessionReplacer removes a message from the replayScheduler when removeAllMessageFromReplay`() {
        val replayer = InMemorySessionReplayer(mock(), mock(), mock(), mock(), groupsAndMembers.second, groupsAndMembers.first)

        setRunning()
        replayer.removeAllMessagesFromReplay()
        @Suppress("UNCHECKED_CAST")
        verify(replayScheduler.constructed().last() as ReplayScheduler<InMemorySessionReplayer.SessionMessageReplay>)
            .removeAllMessagesFromReplay()
    }

    @Test
    fun `The replaySchedular callback publishes the session message`() {
        InMemorySessionReplayer(mock(), mock(), mock(), mock(), groupsAndMembers.second, groupsAndMembers.first)
        val id = UUID.randomUUID().toString()
        val helloMessage = AuthenticationProtocolInitiator(
            id,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID
        ).generateInitiatorHello()

        setRunning()
        var sessionId: String? = null
        var counterparties: SessionManager.SessionCounterparties? = null
        val messageReplay = InMemorySessionReplayer.SessionMessageReplay(helloMessage, id, US, COUNTER_PARTY) { key, callbackId  ->
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
        val groupInfo = mock<GroupPolicyListener.GroupInfo> {
            on { networkType } doReturn NetworkType.CORDA_5
        }
        val groups = mock<LinkManagerGroupPolicyProvider> {
            on { getGroupInfo(any()) } doReturnConsecutively listOf(null, groupInfo)
        }

        InMemorySessionReplayer(mock(), mock(), mock(), mock(), groups, groupsAndMembers.first)
        val id = UUID.randomUUID().toString()
        val helloMessage = AuthenticationProtocolInitiator(
            id,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID
        ).generateInitiatorHello()

        setRunning()
        val messageReplay = InMemorySessionReplayer.SessionMessageReplay(helloMessage, id, US, COUNTER_PARTY) { _,_ -> }
        replayCallback(messageReplay)

        loggingInterceptor.assertSingleWarning("Attempted to replay a session negotiation message (type " +
            "${InitiatorHelloMessage::class.java.simpleName}) but could not find the network type in the GroupPolicyProvider for group" +
            " $GROUP_ID. The message was not replayed.")
    }

    @Test
    fun `The replaySchedular callback logs a warning when the responder is not in the network map`() {
        val members = mock<LinkManagerMembershipGroupReader> {
            on { getMemberInfo(COUNTER_PARTY) } doReturnConsecutively listOf(null, groupsAndMembers.first.getMemberInfo(COUNTER_PARTY))
        }
        InMemorySessionReplayer(mock(), mock(), mock(), mock(), groupsAndMembers.second, members)
        val id = UUID.randomUUID().toString()
        val helloMessage = AuthenticationProtocolInitiator(
            id,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID
        ).generateInitiatorHello()

        setRunning()
        val messageReplay = InMemorySessionReplayer.SessionMessageReplay(helloMessage, id, US, COUNTER_PARTY) { _,_ -> }
        replayCallback(messageReplay)

        loggingInterceptor.assertSingleWarning("Attempted to replay a session negotiation message (type " +
            "${InitiatorHelloMessage::class.java.simpleName}) with peer $COUNTER_PARTY which is not in the members" +
            " map. The message was not replayed.")
    }

    @Test
    fun `The InMemorySessionReplayer will not replay before start`() {
        val helloMessage = AuthenticationProtocolInitiator(
            "",
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            KEY_PAIR.public,
            GROUP_ID
        ).generateInitiatorHello()
        val replayer = InMemorySessionReplayer(mock(), mock(), mock(), mock(), mock(), mock())
        assertThrows<IllegalStateException> {
            replayer.addMessageForReplay(
                "",
                InMemorySessionReplayer.SessionMessageReplay(helloMessage, "", US, COUNTER_PARTY) {_, _->},
                SESSION_COUNTERPARTIES
            )
        }
    }

    private fun setRunning() {
        whenever(dominoTile.constructed().first().isRunning).doReturn(true)
    }
}
