package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.Session
import java.lang.Integer.min
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class OutboundSessionPool(originalPoolSize: Int) {

    private val nextPoolIndexPerSessionCounterparties = ConcurrentHashMap<SessionManager.SessionCounterparties, AtomicInteger>()
    private val outboundSessions = ConcurrentHashMap<SessionKey, SessionType>()
    private val sessionIdToSessionKey = ConcurrentHashMap<String, SessionKey>()
    private val poolResizeLock = ReentrantReadWriteLock()
    @Volatile
    private var poolSize: Int = originalPoolSize

    private data class SessionKey(val poolIndex: Int, val sessionCounterparties: SessionManager.SessionCounterparties)

    sealed class SessionType {
        data class PendingSession(val protocol: AuthenticationProtocolInitiator): SessionType()
        data class ActiveSession(val session: Session): SessionType()
    }

    sealed class SessionPoolStatus {
        object NewSessionNeeded: SessionPoolStatus()
        object SessionPending: SessionPoolStatus()
        data class SessionActive(val session: Session): SessionPoolStatus()
    }

    fun getNextSession(sessionCounterparties: SessionManager.SessionCounterparties): SessionPoolStatus {
        poolResizeLock.read {
            var poolIndex = nextPoolIndexPerSessionCounterparties[sessionCounterparties]?.getAndUpdate { index ->
                val nextIndex = index + 1
                if (nextIndex >= poolSize) {
                    0
                } else {
                    nextIndex
                }
            } ?: return SessionPoolStatus.NewSessionNeeded
            if (poolIndex >= poolSize) poolIndex = 0
            val roundRobinedSession = outboundSessions[SessionKey(poolIndex, sessionCounterparties)]
                ?: return SessionPoolStatus.NewSessionNeeded
            return when (roundRobinedSession) {
                is SessionType.ActiveSession -> {
                    SessionPoolStatus.SessionActive(roundRobinedSession.session)
                }
                is SessionType.PendingSession -> {
                    getSessionNoRoundRobin(poolIndex + 1, sessionCounterparties)
                }
            }
        }
    }

    fun getSession(sessionId: String): SessionType? {
        val key = sessionIdToSessionKey[sessionId]
        return outboundSessions[key]
    }

    fun addSession(session: Session) {
        poolResizeLock.read {
            sessionIdToSessionKey[session.sessionId]?.let {
                outboundSessions.compute(it) { _, _ ->
                    SessionType.ActiveSession(session)
                }
            }
        }
    }

    fun addPendingSessions(
        sessionCounterparties: SessionManager.SessionCounterparties,
        authenticationProtocols: List<AuthenticationProtocolInitiator>
    ) {
        poolResizeLock.read {
            nextPoolIndexPerSessionCounterparties.computeIfAbsent(sessionCounterparties) {
                AtomicInteger(0)
            }
            val totalSessionsToAdd = min(poolSize, authenticationProtocols.size)
            for (index in 0 until totalSessionsToAdd) {
                val sessionKey = SessionKey(index, sessionCounterparties)
                outboundSessions.computeIfAbsent(sessionKey) {
                    sessionIdToSessionKey[authenticationProtocols[index].sessionId] = sessionKey
                    SessionType.PendingSession(authenticationProtocols[index])
                }
            }
        }
    }

    fun timeoutSession(timedOutSessionId: String, newSessionId: String, newPendingSession: AuthenticationProtocolInitiator) {
        poolResizeLock.read {
            val sessionKey = sessionIdToSessionKey.remove(timedOutSessionId) ?: return
            outboundSessions.compute(sessionKey) { _, _ ->
                sessionIdToSessionKey[newSessionId] = sessionKey
                SessionType.PendingSession(newPendingSession)
            }
        }
    }

    //To do: Grow pool with Lambda
    fun resizePool(newPoolSize: Int) {
        poolResizeLock.write {
            val oldPoolSize = poolSize
            poolSize = newPoolSize
            if (newPoolSize < oldPoolSize) {
                for ((sessionId, sessionKey) in sessionIdToSessionKey.entries) {
                    if (sessionKey.poolIndex >= poolSize) {
                        outboundSessions.compute(sessionKey) { _, _ ->
                            sessionIdToSessionKey.remove(sessionId)
                            null
                        }
                    }
                }
            }
        }
    }

    /**
     * We can't atomically update the pool index in this case as another thread might do that at the same time.
     * This could cause us to never find the session. In this case we live with imperfect load balancing.
     */
    private fun getSessionNoRoundRobin(startIdx: Int, sessionCounterparties: SessionManager.SessionCounterparties): SessionPoolStatus {
        poolResizeLock.read {
            for (i in 0..poolSize) {
                var index = startIdx + i
                if (index >= poolSize) {
                    index = startIdx + i - poolSize
                }
                val key = SessionKey(index, sessionCounterparties)
                val session = outboundSessions[key]
                if (session is SessionType.ActiveSession) return SessionPoolStatus.SessionActive(session.session)
            }
            return SessionPoolStatus.SessionPending
        }
    }
}