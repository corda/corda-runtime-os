package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.v5.base.util.contextLogger
import java.lang.Integer.min
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class OutboundSessionPool(
    originalPoolSize: Int,
    private val calculateWeightForSession: (sessionId: String) -> Double?,
    private val genRandomNumber: () -> Float = { Random.nextFloat() }
) {

    companion object {
        val logger = contextLogger()
        const val PROB_CUT_OFF = 1E-6F
    }

    private val outboundSessions = ConcurrentHashMap<SessionKey, SessionType>()
    private val sessionIdToSessionKey = ConcurrentHashMap<String, SessionKey>()

    @Volatile
    private var poolSize: Int = originalPoolSize

    private data class SessionKey(val poolIndex: Int, val sessionCounterparties: SessionManager.SessionCounterparties)

    sealed class SessionType {
        data class PendingSession(
            val sessionCounterparties: SessionManager.SessionCounterparties,
            val protocol: AuthenticationProtocolInitiator
        ) : SessionType()

        data class ActiveSession(val sessionCounterparties: SessionManager.SessionCounterparties, val session: Session) : SessionType()
    }

    sealed class SessionPoolStatus {
        data class NewSessionsNeeded(val number: Int) : SessionPoolStatus()
        object SessionPending : SessionPoolStatus()
        data class SessionActive(val session: Session) : SessionPoolStatus()
    }

    @Suppress("ComplexMethod")
    fun getNextSession(sessionCounterparties: SessionManager.SessionCounterparties): SessionPoolStatus {
        val weights = mutableListOf<Pair<Int, Double>>()
        var totalWeight = 0.0
        for (i in 0 until poolSize) {
            val session = outboundSessions[SessionKey(i, sessionCounterparties)]
            if (session is SessionType.ActiveSession) {
                val weight = calculateWeightForSession(session.session.sessionId) ?: 0.0
                //Avoid division by small float problems
                if (weight > PROB_CUT_OFF) {
                    weights.add(i to weight)
                    totalWeight += weight
                }
            }
        }

        var foundPoolIndex = 0
        var totalProb = 0.0
        val randomNumber = genRandomNumber()
        for ((poolIndex, weight) in weights) {
            totalProb += weight / totalWeight
            logger.info("$poolIndex $totalProb")
            if (randomNumber <= totalProb) {
                foundPoolIndex = poolIndex
                break
            }
        }

        return when (val session = outboundSessions[SessionKey(foundPoolIndex, sessionCounterparties)]) {
            is SessionType.ActiveSession -> {
                SessionPoolStatus.SessionActive(session.session)
            }
            is SessionType.PendingSession -> {
                SessionPoolStatus.SessionPending
            }
            else -> {
                SessionPoolStatus.NewSessionsNeeded(poolSize)
            }
        }
    }

    fun getSession(sessionId: String): SessionType? {
        val key = sessionIdToSessionKey[sessionId]
        return outboundSessions[key]
    }

    fun addSession(session: Session) {
        sessionIdToSessionKey[session.sessionId]?.let {
            outboundSessions.compute(it) { _, _ ->
                SessionType.ActiveSession(it.sessionCounterparties, session)
            }
        }
    }

    fun addPendingSessions(
        sessionCounterparties: SessionManager.SessionCounterparties,
        authenticationProtocols: List<AuthenticationProtocolInitiator>
    ) {
        val totalSessionsToAdd = min(poolSize, authenticationProtocols.size)
        for (index in 0 until totalSessionsToAdd) {
            val sessionKey = SessionKey(index, sessionCounterparties)
            outboundSessions.computeIfAbsent(sessionKey) {
                sessionIdToSessionKey[authenticationProtocols[index].sessionId] = sessionKey
                SessionType.PendingSession(sessionCounterparties, authenticationProtocols[index])
            }
        }
    }

    fun timeoutSession(timedOutSessionId: String, newPendingSession: AuthenticationProtocolInitiator) {
        val sessionKey = sessionIdToSessionKey.remove(timedOutSessionId) ?: return
        outboundSessions.compute(sessionKey) { _, _ ->
            sessionIdToSessionKey[newPendingSession.sessionId] = sessionKey
            SessionType.PendingSession(sessionKey.sessionCounterparties, newPendingSession)
        }
    }

    fun getAllSessionIds(): List<String> {
        return sessionIdToSessionKey.keys().toList()
    }

    fun clearPool() {
        sessionIdToSessionKey.clear()
        outboundSessions.clear()
    }
}