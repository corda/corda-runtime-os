package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.Session
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class OutboundSessionPoolTest {

    companion object {
        const val POOL_SIZE = 5
    }

    @Test
    fun `can add a pending sessions to the session pool`() {
        val pool = OutboundSessionPool(POOL_SIZE)
        val sessionCounterparties = mock<SessionManager.SessionCounterparties>()

        val authenticationProtocols = mutableListOf<AuthenticationProtocolInitiator>()
        for (i in 0 until POOL_SIZE) {
            val mockAuthenticationProtocol = mock<AuthenticationProtocolInitiator> {
                on { sessionId } doReturn "session$i"
            }
            authenticationProtocols.add(mockAuthenticationProtocol)
        }
        pool.addPendingSessions(sessionCounterparties, authenticationProtocols)

        assertThat(pool.getNextSession(sessionCounterparties)).isEqualTo(OutboundSessionPool.SessionPoolStatus.SessionPending)
    }

    @Test
    fun `can get a pending session from the session pool by sessionId`() {
        val pool = OutboundSessionPool(POOL_SIZE)
        val sessionCounterparties = mock<SessionManager.SessionCounterparties>()

        val authenticationProtocols = mutableListOf<AuthenticationProtocolInitiator>()
        for (i in 0 until POOL_SIZE) {
            val mockAuthenticationProtocol = mock<AuthenticationProtocolInitiator> {
                on { sessionId } doReturn "session$i"
            }
            authenticationProtocols.add(mockAuthenticationProtocol)
        }
        pool.addPendingSessions(sessionCounterparties, authenticationProtocols)

        for (i in 0 until POOL_SIZE) {
            val gotSession = pool.getSession("session$i")
            assertThat((gotSession as OutboundSessionPool.SessionType.PendingSession).protocol).isEqualTo(authenticationProtocols[i])
        }
    }

    @Test
    fun `can add a sessions to the session pool`() {
        val pool = OutboundSessionPool(POOL_SIZE)
        val sessionCounterparties = mock<SessionManager.SessionCounterparties>()

        val authenticationProtocols = mutableListOf<AuthenticationProtocolInitiator>()
        for (i in 0 until POOL_SIZE) {
            val mockAuthenticationProtocol = mock<AuthenticationProtocolInitiator> {
                on { sessionId } doReturn "session$i"
            }
            authenticationProtocols.add(mockAuthenticationProtocol)
        }

        val mockSession = mock<Session> {
            on { sessionId } doReturn "session2"
        }

        pool.addPendingSessions(sessionCounterparties, authenticationProtocols)
        pool.addSession(mockSession)

        for (i in 0 until 2 * POOL_SIZE) {
            val gotSession = pool.getNextSession(sessionCounterparties)
            assertThat((gotSession as OutboundSessionPool.SessionPoolStatus.SessionActive).session).isEqualTo(mockSession)
        }
    }

    @Test
    fun `can get a session from the session pool by sessionId`() {
        val pool = OutboundSessionPool(POOL_SIZE)
        val sessionCounterparties = mock<SessionManager.SessionCounterparties>()

        val authenticationProtocols = mutableListOf<AuthenticationProtocolInitiator>()
        for (i in 0 until POOL_SIZE) {
            val mockAuthenticationProtocol = mock<AuthenticationProtocolInitiator> {
                on { sessionId } doReturn "session$i"
            }
            authenticationProtocols.add(mockAuthenticationProtocol)
        }

        val negotiatedSessionId = "session2"
        val mockSession = mock<Session> {
            on { sessionId } doReturn negotiatedSessionId
        }

        pool.addPendingSessions(sessionCounterparties, authenticationProtocols)
        pool.addSession(mockSession)

        val gotSession = pool.getSession(negotiatedSessionId)
        assertThat((gotSession as OutboundSessionPool.SessionType.ActiveSession).session).isEqualTo(mockSession)
    }

    @Test
    fun `getNextSession load balances perfectly if all sessions are added`() {
        val pool = OutboundSessionPool(POOL_SIZE)
        val sessionCounterparties = mock<SessionManager.SessionCounterparties>()

        val authenticationProtocols = mutableListOf<AuthenticationProtocolInitiator>()
        for (i in 0 until POOL_SIZE) {
            val mockAuthenticationProtocol = mock<AuthenticationProtocolInitiator> {
                on { sessionId } doReturn "session$i"
            }
            authenticationProtocols.add(mockAuthenticationProtocol)
        }
        pool.addPendingSessions(sessionCounterparties, authenticationProtocols)

        val mockSessions = mutableListOf<Session>()
        for (i in 0 until POOL_SIZE) {
            val mockSession = mock<Session> {
                on { sessionId } doReturn "session$i"
            }
            mockSessions.add(mockSession)
            pool.addSession(mockSession)
        }

        val gotSessions = mutableListOf<Session>()
        for (i in 0 until POOL_SIZE) {
            gotSessions.add((pool.getNextSession(sessionCounterparties) as OutboundSessionPool.SessionPoolStatus.SessionActive).session)
        }
        assertThat(gotSessions).containsExactlyInAnyOrderElementsOf(mockSessions)
    }

    @Test
    fun `getNextSession load balances imperfectly if not all sessions are added yet`() {
        val pool = OutboundSessionPool(POOL_SIZE)
        val sessionCounterparties = mock<SessionManager.SessionCounterparties>()

        val authenticationProtocols = mutableListOf<AuthenticationProtocolInitiator>()
        for (i in 0 until POOL_SIZE) {
            val mockAuthenticationProtocol = mock<AuthenticationProtocolInitiator> {
                on { sessionId } doReturn "session$i"
            }
            authenticationProtocols.add(mockAuthenticationProtocol)
        }
        pool.addPendingSessions(sessionCounterparties, authenticationProtocols)

        val mockSessions = mutableListOf<Session>()
        for (i in listOf(0, 1, 4)) {
            val mockSession = mock<Session> {
                on { sessionId } doReturn "session$i"
            }
            mockSessions.add(mockSession)
            pool.addSession(mockSession)
        }

        val gotSessions = mutableListOf<Session>()
        for (i in 0 until 2 * POOL_SIZE) {
            gotSessions.add((pool.getNextSession(sessionCounterparties) as OutboundSessionPool.SessionPoolStatus.SessionActive).session)
        }
        assertThat(gotSessions).containsOnlyElementsOf(mockSessions)
    }

    @Test
    fun `timed out sessions are removed from the pool`() {
        val pool = OutboundSessionPool(POOL_SIZE)
        val sessionCounterparties = mock<SessionManager.SessionCounterparties>()

        val authenticationProtocols = mutableListOf<AuthenticationProtocolInitiator>()
        for (i in 0 until POOL_SIZE) {
            val mockAuthenticationProtocol = mock<AuthenticationProtocolInitiator> {
                on { sessionId } doReturn "session$i"
            }
            authenticationProtocols.add(mockAuthenticationProtocol)
        }
        pool.addPendingSessions(sessionCounterparties, authenticationProtocols)

        val mockSessions = mutableListOf<Session>()
        for (i in 0 until POOL_SIZE) {
            val mockSession = mock<Session> {
                on { sessionId } doReturn "session$i"
            }
            if (i != 2) mockSessions.add(mockSession)
            pool.addSession(mockSession)
        }
        val timedOutSessionId = "session2"
        pool.timeoutSession(timedOutSessionId, "newSession", mock())

        val gotSessions = mutableListOf<Session>()
        for (i in 0 until POOL_SIZE) {
            gotSessions.add((pool.getNextSession(sessionCounterparties) as OutboundSessionPool.SessionPoolStatus.SessionActive).session)
        }
        assertThat(gotSessions).containsOnlyElementsOf(mockSessions)
    }

    @Test
    fun `a new session can be added to the pool after timeout`() {
        val pool = OutboundSessionPool(POOL_SIZE)
        val sessionCounterparties = mock<SessionManager.SessionCounterparties>()

        val authenticationProtocols = mutableListOf<AuthenticationProtocolInitiator>()
        for (i in 0 until POOL_SIZE) {
            val mockAuthenticationProtocol = mock<AuthenticationProtocolInitiator> {
                on { sessionId } doReturn "session$i"
            }
            authenticationProtocols.add(mockAuthenticationProtocol)
        }
        pool.addPendingSessions(sessionCounterparties, authenticationProtocols)

        val mockSessions = mutableListOf<Session>()
        for (i in 0 until POOL_SIZE) {
            val mockSession = mock<Session> {
                on { sessionId } doReturn "session$i"
            }
            if (i != 2) mockSessions.add(mockSession)
            pool.addSession(mockSession)
        }
        val timedOutSessionId = "session2"
        pool.timeoutSession(timedOutSessionId, "newSession", mock())

        val mockSession = mock<Session> {
            on { sessionId } doReturn "newSession"
        }
        pool.addSession(mockSession)
        mockSessions.add(mockSession)

        val gotSessions = mutableListOf<Session>()
        for (i in 0 until POOL_SIZE) {
            gotSessions.add((pool.getNextSession(sessionCounterparties) as OutboundSessionPool.SessionPoolStatus.SessionActive).session)
        }
        assertThat(gotSessions).containsExactlyInAnyOrderElementsOf(mockSessions)
    }
}