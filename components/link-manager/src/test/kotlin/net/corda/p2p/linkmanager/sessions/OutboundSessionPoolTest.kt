package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class OutboundSessionPoolTest {

    companion object {
        const val POOL_SIZE = 5
        lateinit var loggingInterceptor: LoggingInterceptor

        @BeforeAll
        @JvmStatic
        fun setup() {
            loggingInterceptor = LoggingInterceptor.setupLogging()
        }
    }

    @Test
    fun `can add a pending sessions to the session pool`() {
        val pool = OutboundSessionPool({1}, {0})
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
        val pool = OutboundSessionPool({1}, {0})
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
        val pool = OutboundSessionPool({1}, {0})
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
        pool.updateAfterSessionEstablished(mockSession)

        for (i in 0 until 2 * POOL_SIZE) {
            val gotSession = pool.getNextSession(sessionCounterparties)
            assertThat((gotSession as OutboundSessionPool.SessionPoolStatus.SessionActive).session).isEqualTo(mockSession)
        }
    }

    @Test
    fun `can get a session from the session pool by sessionId`() {
        val pool = OutboundSessionPool({1}, {0})
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
        pool.updateAfterSessionEstablished(mockSession)

        val gotSession = pool.getSession(negotiatedSessionId)
        assertThat((gotSession as OutboundSessionPool.SessionType.ActiveSession).session).isEqualTo(mockSession)
    }

    @Test
    fun `getNextSession load balances if all sessions are added`() {
        var invocations = 0L
        fun fakeRand(until: Long): Long {
            return if (invocations >= until) {
                invocations = 0
                0
            } else {
                invocations++
            }
        }
        val pool = OutboundSessionPool({1}, ::fakeRand)
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
            pool.updateAfterSessionEstablished(mockSession)
        }

        val gotSessions = mutableListOf<Session>()
        // If all sessions have weight 1 then the total weight is POOL_SIZE. The sum of (total weight - weight) for each session is
        // (POOL_SIZE - 1) * total weight. Which is (POOL_SIZE - 1) * POOL_SIZE.
        for (i in 0 until (POOL_SIZE - 1) * POOL_SIZE) {
            gotSessions.add((pool.getNextSession(sessionCounterparties) as OutboundSessionPool.SessionPoolStatus.SessionActive).session)
        }
        assertThat(gotSessions).containsOnlyElementsOf(mockSessions)
    }

    @Test
    fun `getNextSession load balances if all sessions are added and have weight 0`() {
        var invocations = 0L
        fun fakeRand(until: Long): Long {
            return if (invocations >= until) {
                invocations = 0
                0
            } else {
                invocations++
            }
        }
        val pool = OutboundSessionPool({0}, ::fakeRand)
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
            pool.updateAfterSessionEstablished(mockSession)
        }

        val gotSessions = mutableListOf<Session>()
        // If all sessions have weight 1 then the total weight is POOL_SIZE. The sum of (total weight - weight) for each session is
        // (POOL_SIZE - 1) * total weight. Which is (POOL_SIZE - 1) * POOL_SIZE.
        for (i in 0 until (POOL_SIZE - 1) * POOL_SIZE) {
            gotSessions.add((pool.getNextSession(sessionCounterparties) as OutboundSessionPool.SessionPoolStatus.SessionActive).session)
        }
        assertThat(gotSessions).containsOnlyElementsOf(mockSessions)
    }

    @Test
    fun `getNextSession load balances if not all sessions are added yet`() {
        var invocations = 0L
        fun fakeRand(until: Long): Long {
            return if (invocations >= until) {
                invocations = 0
                0
            } else {
                invocations++
            }
        }

        val pool = OutboundSessionPool({1L}, ::fakeRand)
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
            pool.updateAfterSessionEstablished(mockSession)
        }

        val gotSessions = mutableListOf<Session>()
        for (i in 0 until (POOL_SIZE - 1) * POOL_SIZE) {
            gotSessions.add((pool.getNextSession(sessionCounterparties) as OutboundSessionPool.SessionPoolStatus.SessionActive).session)
        }
        assertThat(gotSessions).containsOnlyElementsOf(mockSessions)
    }

    @Test
    fun `timed out sessions are removed from the pool`() {
        var invocations = 0L
        fun fakeRand(until: Long): Long {
            return if (invocations >= until) {
                invocations = 0
                0
            } else {
                invocations++
            }
        }

        val pool = OutboundSessionPool({1L}, ::fakeRand)
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
            pool.updateAfterSessionEstablished(mockSession)
        }
        val timedOutSessionId = "session2"
        val newPendingSession = mock<AuthenticationProtocolInitiator> {
            on { sessionId } doReturn "newSession"
        }

        pool.replaceSession(timedOutSessionId, newPendingSession)

        val gotSessions = mutableListOf<Session>()
        for (i in 0 until (POOL_SIZE - 1) * POOL_SIZE) {
            gotSessions.add((pool.getNextSession(sessionCounterparties) as OutboundSessionPool.SessionPoolStatus.SessionActive).session)
        }
        assertThat(gotSessions).containsOnlyElementsOf(mockSessions)
    }

    @Test
    fun `a new session can be added to the pool after timeout`() {
        var invocations = 0L
        fun fakeRand(until: Long): Long {
            return if (invocations >= until) {
                invocations = 0
                0
            } else {
                invocations++
            }
        }

        val pool = OutboundSessionPool({1L}, ::fakeRand)
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
            pool.updateAfterSessionEstablished(mockSession)
        }
        val timedOutSessionId = "session2"
        val newPendingSession = mock<AuthenticationProtocolInitiator> {
            on { sessionId } doReturn "newSession"
        }
        pool.replaceSession(timedOutSessionId, newPendingSession)

        val mockSession = mock<Session> {
            on { sessionId } doReturn "newSession"
        }
        pool.updateAfterSessionEstablished(mockSession)
        mockSessions.add(mockSession)

        val gotSessions = mutableListOf<Session>()
        for (i in 0 until (POOL_SIZE - 1) * POOL_SIZE) {
            gotSessions.add((pool.getNextSession(sessionCounterparties) as OutboundSessionPool.SessionPoolStatus.SessionActive).session)
        }
        assertThat(gotSessions).containsOnlyElementsOf(mockSessions)
    }

    @Test
    fun `sessions are selected with the correct weight`() {
        var invocations = 0L
        fun fakeRand(until: Long): Long {
            return if (invocations >= until) {
                invocations = 0
                0
            } else {
                invocations++
            }
        }

        fun getWeightForSession(id: String): Long {
            //Session 0 has weight 0, Session 1 has weight 1 etc.
            return id.substringAfter("session").toLong()
        }

        val pool = OutboundSessionPool(::getWeightForSession, ::fakeRand)
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
            pool.updateAfterSessionEstablished(mockSession)
        }

        val gotSessions = mutableListOf<Session>()
        // If Session 0 has weight 0, Session 1 has weight 1 etc. Then we have total weight is: POOL_SIZE * (POOL_SIZE - 1) / 2.
        // The sum of (total weight - weight) for each session is (POOL_SIZE - 1) * total weight. Which is
        // (POOL_SIZE - 1) * POOL_SIZE * (POOL_SIZE - 1) / 2.
        val numberOfSessionsToGet = (POOL_SIZE - 1) * POOL_SIZE * (POOL_SIZE - 1) / 2
        for (i in 0 until numberOfSessionsToGet ) {
            gotSessions.add((pool.getNextSession(sessionCounterparties) as OutboundSessionPool.SessionPoolStatus.SessionActive).session)
        }

        assertThat(gotSessions.filter { it.sessionId == "session0" }).hasSize(10)
        assertThat(gotSessions.filter { it.sessionId == "session1" }).hasSize(9)
        assertThat(gotSessions.filter { it.sessionId == "session2" }).hasSize(8)
        assertThat(gotSessions.filter { it.sessionId == "session3" }).hasSize(7)
        assertThat(gotSessions.filter { it.sessionId == "session4" }).hasSize(6)
    }
}
