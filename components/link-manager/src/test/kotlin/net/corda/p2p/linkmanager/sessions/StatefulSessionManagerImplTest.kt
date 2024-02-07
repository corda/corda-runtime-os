package net.corda.p2p.linkmanager.sessions

import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutHeader
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.utilities.time.Clock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class StatefulSessionManagerImplTest {
    private val cordinator = mock<LifecycleCoordinator>()
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn cordinator
    }
    private val stateManager = mock<StateManager>()
    private val sessionManagerImplDominoTile = mock<ComplexDominoTile> {
        on { coordinatorName } doReturn mock()
    }
    private val sessionManagerImpl = mock<SessionManagerImpl> {
        on { dominoTile } doReturn sessionManagerImplDominoTile
    }
    private val stateConvertor = mock<StateConvertor>()
    private val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider>()
    private val now = Instant.ofEpochMilli(333L)
    private val clock = mock<Clock> {
        on { instant() } doReturn now
    }

    private val manager = StatefulSessionManagerImpl(
        coordinatorFactory,
        stateManager,
        sessionManagerImpl,
        stateConvertor,
        clock,
        membershipGroupReaderProvider,
    )

    private data class Wrapper<T>(
        val value: T,
    )

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
            val savedStates = sessionIds.associateWith { sessionId ->
                val state = mock<State> {
                    on { value } doReturn sessionId.toByteArray()
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
                val serialisableSessionData = mock<AuthenticatedSession>()
                val sessionState = mock<SessionState> {
                    on { sessionData } doReturn serialisableSessionData
                }
                whenever(stateConvertor.toCordaSessionState(same(state), any())).doReturn(sessionState)
                state
            }
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
            val state = mock<State> {
                on { metadata } doReturn Metadata(
                    mapOf(
                        "sourceVnode" to "O=Alice, L=London, C=GB",
                        "destinationVnode" to "O=Bob, L=London, C=GB",
                        "groupId" to "group ID",
                        "lastSendTimestamp" to 50L,
                        "status" to "SentResponderHello",
                        "expiry" to 20000000L,
                    ),
                )

                on { key } doReturn "stateKey"
            }
            val sessionIdentity = "id"
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
    }

    @Nested
    inner class ProcessInboundSessionMessagesTest {
        @Test
        fun `processInitiatorHello return the correct data`() {
            val state = mock<State> {
                on { metadata } doReturn Metadata(
                    mapOf(
                        "sourceVnode" to "O=Alice, L=London, C=GB",
                        "destinationVnode" to "O=Bob, L=London, C=GB",
                        "groupId" to "group ID",
                        "lastSendTimestamp" to 50L,
                        "status" to "SentResponderHello",
                        "expiry" to 20000000L,
                    ),
                )
                on { key } doReturn "stateKey"
            }
            val sessionIdentity = "id"
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
}
