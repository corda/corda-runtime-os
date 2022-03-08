package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Stores outbound [AuthenticationProtocolInitiator] during session negotiation and [Session] once negotiation is
 * complete.
 * [calculateWeightForSession] - Used to calculate a weight per session between 0 and 1. Sessions with a large weight
 * are favoured over sessions with a small weight.
 */
class OutboundSessionPool(
    private val calculateWeightForSession: (sessionId: String) -> Double?,
    private val genRandomNumber: () -> Float = { Random.nextFloat() }
) {

    companion object {
        val logger = contextLogger()
        //Sessions with a weight smaller than this are treated as having a weight of 0. This is done to prevent numerical issues.
        const val WEIGHT_CUT_OFF = 1E-6F
    }

    private val outboundSessions = ConcurrentHashMap<SessionManager.SessionCounterparties, ConcurrentHashMap<String, SessionType>>()
    private val counterpartiesForSessionId = ConcurrentHashMap<String, SessionManager.SessionCounterparties>()

    sealed class SessionType {
        data class PendingSession(
            val sessionCounterparties: SessionManager.SessionCounterparties,
            val protocol: AuthenticationProtocolInitiator
        ) : SessionType()

        data class ActiveSession(val sessionCounterparties: SessionManager.SessionCounterparties, val session: Session) : SessionType()
    }

    sealed class SessionPoolStatus {
        object NewSessionsNeeded : SessionPoolStatus()
        object SessionPending : SessionPoolStatus()
        data class SessionActive(val session: Session) : SessionPoolStatus()
    }

    /**
     * If session negotiation is completed (for any session) then select a random [Session] for the set of [sessionCounterparties],
     * weighted by calculateWeightForSession. If session negotiation is started, but is not completed, then
     * [SessionPoolStatus.SessionPending] is returned, otherwise [SessionPoolStatus.NewSessionsNeeded] is returned.
     */
    fun getNextSession(sessionCounterparties: SessionManager.SessionCounterparties): SessionPoolStatus {
        val outboundSessionsForCounterparties = outboundSessions[sessionCounterparties] ?: return SessionPoolStatus.NewSessionsNeeded

        val activeSessions = outboundSessionsForCounterparties.mapNotNull { (_, session) ->
            session as? SessionType.ActiveSession
        }.associateBy { it.session.sessionId }
        if (activeSessions.isEmpty()) return SessionPoolStatus.SessionPending

        val weights = mutableListOf<Pair<SessionType.ActiveSession, Double>>()
        var totalWeight = 0.0
        activeSessions.forEach {
           val weight = calculateWeightForSession(it.key) ?: 0.0
           //Avoid division by small float problems
           if (weight > WEIGHT_CUT_OFF) {
               weights.add(it.value to weight)
               totalWeight += weight
           }
        }

        var totalProb = 0.0
        val randomNumber = genRandomNumber()
        val selectedSession = weights.find {
            totalProb += it.second / totalWeight
            randomNumber <= totalProb
        }?.first

        selectedSession?.let { return SessionPoolStatus.SessionActive(it.session) } ?: return SessionPoolStatus.SessionPending
    }

    /**
     * get a specific session by [sessionId]
     */
    fun getSession(sessionId: String): SessionType? {
        val counterparties = counterpartiesForSessionId[sessionId] ?: return null
        return outboundSessions[counterparties]?.get(sessionId)
    }

    /**
     * update the session pool once session negotiation is complete
     */
    fun updateAfterSessionEstablished(session: Session) {
        val counterparties = counterpartiesForSessionId[session.sessionId] ?: return
        outboundSessions.computeIfPresent(counterparties) { _, sessions ->
            sessions[session.sessionId] = SessionType.ActiveSession(counterparties, session)
            sessions
        }
    }

    /**
     * Add a set of [AuthenticationProtocolInitiator] for a set of [sessionCounterparties]. This replaces all
     * existing entries in the pool.
     */
    fun addPendingSessions(
        sessionCounterparties: SessionManager.SessionCounterparties,
        authenticationProtocols: List<AuthenticationProtocolInitiator>
    ) {
        outboundSessions.compute(sessionCounterparties) { _, _ ->
            val sessionsMap = ConcurrentHashMap<String, SessionType>()
            authenticationProtocols.mapNotNull { sessionsMap[it.sessionId] = SessionType.PendingSession(sessionCounterparties, it) }
            sessionsMap
        }
        authenticationProtocols.forEach{ counterpartiesForSessionId[it.sessionId] = sessionCounterparties }
    }

    /**
     * Remove a single [AuthenticationProtocolInitiator] or [Session] in the pool and replace it
     * with a [AuthenticationProtocolInitiator].
     */
    fun replaceSession(timedOutSessionId: String,
                       newPendingSession: AuthenticationProtocolInitiator
    ): Boolean {
        var removed = false
        val counterparties = counterpartiesForSessionId[timedOutSessionId] ?: return removed
        outboundSessions.computeIfPresent(counterparties) { _, sessions ->
            sessions.remove(timedOutSessionId) ?: return@computeIfPresent sessions
            removed = true
            sessions[newPendingSession.sessionId] = SessionType.PendingSession(counterparties, newPendingSession)
            sessions
        }
        counterpartiesForSessionId.remove(timedOutSessionId)
        if (removed) counterpartiesForSessionId[newPendingSession.sessionId] = counterparties
        return removed
    }

    /**
     * Remove all the Sessions in the pool for a set of [sessionCounterparties]
     */
    fun removeSessions(sessionCounterparties: SessionManager.SessionCounterparties) {
        val removedSessions = outboundSessions.remove(sessionCounterparties)
        if (removedSessions != null) {
            for (sessionId in removedSessions.keys()) {
                counterpartiesForSessionId.remove(sessionId)
            }
        }
    }

    /**
     * Get all the sessionId's in the pool.
     */
    fun getAllSessionIds(): List<String> {
        return counterpartiesForSessionId.keys.toList()
    }

    /**
     * Remove all sessions in the pool.
     */
    fun clearPool() {
        outboundSessions.clear()
    }
}