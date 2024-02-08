package net.corda.p2p.linkmanager.sessions

import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutHeader
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.ReEstablishSessionMessage
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.linkmanager.sessions.StatefulSessionManagerImpl.Companion.LINK_MANAGER_SUBSYSTEM
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.schema.Schemas
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.utilities.time.Clock
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.mockito.Mockito

class StatefulSessionManagerImplTest {
    private val coordinator = mock<LifecycleCoordinator>()
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }
    private val stateManager = mock<StateManager>()
    private val sessionManagerImplDominoTile = mock<ComplexDominoTile> {
        on { coordinatorName } doReturn mock()
    }
    private val published = argumentCaptor<List<Record<String, Any>>>()
    private val mockPublisher = mock<PublisherWithDominoLogic> {
        on { publish(published.capture()) } doReturn mock()
    }
    private val sessionManagerImpl = mock<SessionManagerImpl> {
        on { dominoTile } doReturn sessionManagerImplDominoTile
        on { publisher } doReturn mockPublisher
    }
    private val stateConvertor = mock<StateConvertor>()
    private val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider>()
    private val now = Instant.ofEpochMilli(333L)
    private val clock = mock<Clock> {
        on { instant() } doReturn now
    }
    private val publisherWithDominoLogic = Mockito.mockConstruction(PublisherWithDominoLogic::class.java) { mock, _ ->
        val mockDominoTile = mock<ComplexDominoTile> {
            whenever(it.toNamedLifecycle()).thenReturn(mock())
        }
        whenever(mock.dominoTile).thenReturn(mockDominoTile)
    }
    private val serialized = argumentCaptor<ReEstablishSessionMessage>()
    private val schemaRegistry = mock<AvroSchemaRegistry> {
        on { serialize(serialized.capture()) } doReturn ByteBuffer.wrap(byteArrayOf(0))
        on {
            deserialize(
                any(), eq(ReEstablishSessionMessage::class.java), eq(null)
            )
        } doReturn ReEstablishSessionMessage("test")
    }

    private val manager = StatefulSessionManagerImpl(
        mock(),
        mock(),
        mock(),
        coordinatorFactory,
        stateManager,
        sessionManagerImpl,
        stateConvertor,
        clock,
        membershipGroupReaderProvider,
        schemaRegistry,
    )

    private data class Wrapper<T>(
        val value: T,
    )

    @AfterEach
    fun cleanUp() {
        publisherWithDominoLogic.close()
    }
    private fun mockState(id: String): State {
        val state = mock<State> {
            on { value } doReturn id.toByteArray()
            on { metadata } doReturn Metadata(
                mapOf(
                    "sourceVnode" to "O=Alice, L=London, C=GB",
                    "destinationVnode" to "O=Bob, L=London, C=GB",
                    "groupId" to "group ID",
                    "lastSendTimestamp" to 50L,
                    "encryptionKeyId" to "encryptionKeyId",
                    "encryptionTenant" to "encryptionTenant",
                    "status" to "SentResponderHello",
                    "expiry" to 20000000L,
                ),
            )
            on { key } doReturn "stateKey"
        }
        val serialisableSessionData = mock<AuthenticatedSession> {
            on { sessionId } doReturn id
        }
        val sessionState = mock<SessionState> {
            on { sessionData } doReturn serialisableSessionData
        }
        whenever(stateConvertor.toCordaSessionState(same(state), any())).doReturn(sessionState)
        return state
    }

    @Nested
    inner class GetSessionsByIdTests {
        @Test
        fun `it returned sessions from the state manager`() {
            val sessionIds = (1..4).map {
                "session-$it"
            }
            val sessionIdsContainers = sessionIds.map {
                Wrapper(it)
            }
            val savedStates = sessionIds.associateWith { mockState(it) }
            whenever(stateManager.get(argThat { containsAll(sessionIds) })).doReturn(savedStates)

            val sessions = manager.getSessionsById(sessionIdsContainers) {
                it.value
            }
            assertSoftly {
                assertThat(sessions.map { it.first.value }).containsExactlyInAnyOrderElementsOf(sessionIds)
                assertThat(sessions.mapNotNull { it.second as? SessionManager.SessionDirection.Inbound }).hasSize(4)
            }
        }

        @Test
        fun `it will avoid going to the state manager if the state is cached`() {
            val sessionIdentity = "id"
            val state = mockState(sessionIdentity)
            whenever(stateManager.get(argThat { contains(sessionIdentity) })).doReturn(
                mapOf(
                    sessionIdentity to state,
                ),
            )
            val handshakeMessageHeader = mock<CommonHeader> {
                on { sessionId } doReturn sessionIdentity
            }
            val handshakeMessage = mock<InitiatorHandshakeMessage> {
                on { header } doReturn handshakeMessageHeader
            }
            val message = mock<LinkInMessage> {
                on { payload } doReturn handshakeMessage
            }
            val messages = listOf(Wrapper(message))
            val session = mock<AuthenticatedSession> {
                on { sessionId } doReturn sessionIdentity
            }
            val responder = mock<AuthenticationProtocolResponder> {
                on { getSession() } doReturn session
            }
            val sessionState = mock<SessionState> {
                on { sessionData } doReturn responder
            }
            whenever(stateConvertor.toCordaSessionState(same(state), any())).doReturn(sessionState)
            val responseHeaders = mock<LinkOutHeader> {
                on { sourceIdentity } doReturn HoldingIdentity(
                    "O=Alice, L=London, C=GB",
                    "Group",
                )
                on { destinationIdentity } doReturn HoldingIdentity(
                    "O=Bob, L=London, C=GB",
                    "Group",
                )
            }
            val responseMessage = mock<LinkOutMessage> {
                on { header } doReturn responseHeaders
            }
            val rawData = byteArrayOf(3, 4, 5)
            whenever(
                sessionManagerImpl.processInitiatorHandshake(
                    responder,
                    handshakeMessage,
                ),
            ).doReturn(responseMessage)
            whenever(stateConvertor.toStateByteArray(SessionState(responseMessage, session))).doReturn(rawData)
            val statesUpdates = argumentCaptor<Collection<State>>()
            whenever(stateManager.update(statesUpdates.capture())).doReturn(emptyMap())
            manager.processSessionMessages(messages) {
                it.value
            }
            val sessionIdsContainers = listOf(
                Wrapper(
                    sessionIdentity,
                ),
            )

            manager.getSessionsById(
                sessionIdsContainers,
            ) {
                it.value
            }

            verify(stateManager, times(1)).get(any())
        }

        @Test
        fun `it will publish correct message if session state cannot be decrypted`() {
            val testSessionId = "test-session"
            val state = mockState(testSessionId)
            whenever(stateConvertor.toCordaSessionState(same(state), any())).doReturn(null)
            whenever(stateManager.get(setOf(testSessionId))).doReturn(
                mapOf(
                    testSessionId to state,
                ),
            )

            manager.getSessionsById(
                setOf(Wrapper(testSessionId))
            ) { it.value }

            val publishedRecord = published.firstValue.single()
            assertThat(publishedRecord.topic).isEqualTo(Schemas.P2P.P2P_OUT_TOPIC)
            val publishedMessageHeader = ((publishedRecord.value as AppMessage).message as AuthenticatedMessage).header
            assertThat(publishedMessageHeader.subsystem).isEqualTo(LINK_MANAGER_SUBSYSTEM)
            assertThat(serialized.firstValue.sessionId).isEqualTo(testSessionId)
        }
    }

    @Nested
    inner class ProcessInboundSessionMessagesTest {
        @Test
        fun `processInitiatorHello return the correct data`() {
            val sessionIdentity = "id"
            val state = mockState(sessionIdentity)
            whenever(stateManager.get(any())).doReturn(
                mapOf(
                    sessionIdentity to state,
                ),
            )
            val handshakeMessageHeader = mock<CommonHeader> {
                on { sessionId } doReturn sessionIdentity
            }
            val handshakeMessage = mock<InitiatorHandshakeMessage> {
                on { header } doReturn handshakeMessageHeader
            }
            val message = mock<LinkInMessage> {
                on { payload } doReturn handshakeMessage
            }
            val messages = listOf(Wrapper(message))
            val session = mock<AuthenticatedSession> {
                on { sessionId } doReturn sessionIdentity
            }
            val responder = mock<AuthenticationProtocolResponder> {
                on { getSession() } doReturn session
            }
            val sessionState = mock<SessionState> {
                on { sessionData } doReturn responder
            }
            whenever(stateConvertor.toCordaSessionState(same(state), any())).doReturn(sessionState)
            val responseHeaders = mock<LinkOutHeader> {
                on { sourceIdentity } doReturn HoldingIdentity(
                    "O=Alice, L=London, C=GB",
                    "Group",
                )
                on { destinationIdentity } doReturn HoldingIdentity(
                    "O=Bob, L=London, C=GB",
                    "Group",
                )
            }
            val responseMessage = mock<LinkOutMessage> {
                on { header } doReturn responseHeaders
            }
            val rawData = byteArrayOf(3, 4, 5)
            whenever(
                sessionManagerImpl.processInitiatorHandshake(
                    responder,
                    handshakeMessage,
                ),
            ).doReturn(responseMessage)
            whenever(stateConvertor.toStateByteArray(SessionState(responseMessage, session))).doReturn(rawData)
            val statesUpdates = argumentCaptor<Collection<State>>()
            whenever(stateManager.update(statesUpdates.capture())).doReturn(emptyMap())

            val results = manager.processSessionMessages(messages) {
                it.value
            }

            assertSoftly {
                assertThat(results)
                    .anySatisfy {
                        assertThat(it.second).isEqualTo(responseMessage)
                    }.hasSize(1)
                assertThat(statesUpdates.firstValue.firstOrNull()?.value).isEqualTo(rawData)
                assertThat(statesUpdates.firstValue.firstOrNull()?.key).isEqualTo(sessionIdentity)
                assertThat(statesUpdates.firstValue.firstOrNull()?.version).isEqualTo(0)
                assertThat(statesUpdates.firstValue.firstOrNull()?.metadata).containsEntry(
                    "destinationVnode",
                    "O=Bob, L=London, C=GB",
                ).containsEntry(
                    "sourceVnode",
                    "O=Alice, L=London, C=GB",
                ).containsEntry(
                    "status",
                    "SentResponderHandshake",
                ).containsEntry(
                    "lastSendTimestamp",
                    now.toEpochMilli(),
                ).containsEntry(
                    "groupId",
                    "Group",
                )
            }
        }
    }

    @Nested
    inner class DeleteOutboundSessionTests {
        @Test
        fun `correct key is used to forget session state`() {
            val source = createTestHoldingIdentity("CN=PartyA, O=Corp, L=LDN, C=GB", "Group")
            val destination = createTestHoldingIdentity("CN=PartyC, O=Corp, L=LDN, C=GB", "Group")
            val knownStateKey = "K+IiD9+Kw7DiGTMPuAh2wAHqzzbHOrRiv1zRr0s4eto="
            val messageHeader = mock<AuthenticatedMessageHeader> {
                on { statusFilter } doReturn MembershipStatusFilter.ACTIVE
            }
            val message = mock<AuthenticatedMessage> {
                on { payload } doReturn mock()
                on { header } doReturn messageHeader
            }
            val mockMember = mock<MemberInfo> {
                on { serial } doReturn 1
            }
            val groupReader = mock<MembershipGroupReader> {
                on { lookup(any(), any()) } doReturn mockMember
            }
            whenever(membershipGroupReaderProvider.getGroupReader(any())).doReturn(groupReader)

            manager.deleteOutboundSession(
                SessionManager.Counterparties(source, destination),
                message
            )

            verify(stateManager).get(listOf(knownStateKey))
        }
    }
}
