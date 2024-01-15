package net.corda.p2p.linkmanager.sessions

import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.utilities.time.Clock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.whenever

class StatefulSessionManagerImplTest {
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val stateManager = mock<StateManager>()
    private val sessionManagerImpl = mock<SessionManagerImpl>()
    private val stateConvertor = mock<StateConvertor>()
    private val clock = mock<Clock>()

    private val manager = StatefulSessionManagerImpl(
        coordinatorFactory,
        stateManager,
        sessionManagerImpl,
        stateConvertor,
        clock,
    )

    private data class SessionContainer(
        val sessionId: String,
    )

    @Nested
    inner class GetSessionsByIdTests {
        @Test
        fun `it returned sessions from the state manager`() {
            val sessionIds = (1..4).map {
                "session-$it"
            }
            val sessionIdsContainers = sessionIds.map {
                SessionContainer(it)
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
                            "expiry" to 1000L,
                        ),
                    )
                }
                val serialisableSessionData = mock<AuthenticatedSession>()
                val sessionState = mock<SessionState> {
                    on { sessionData } doReturn serialisableSessionData
                }
                whenever(stateConvertor.toCordaSessionState(same(state), any())).doReturn(sessionState)
                state
            }
            whenever(stateManager.get(sessionIds)).doReturn(savedStates)

            val sessions = manager.getSessionsById(sessionIdsContainers) {
                it.sessionId
            }
            assertSoftly {
                assertThat(sessions.map { it.first.sessionId }).containsExactlyInAnyOrderElementsOf(sessionIds)
                assertThat(sessions.mapNotNull { it.second as? SessionManager.SessionDirection.Inbound }).hasSize(4)
            }
        }
    }
}
