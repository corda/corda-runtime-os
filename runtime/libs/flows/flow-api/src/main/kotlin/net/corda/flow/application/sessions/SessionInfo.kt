package net.corda.flow.application.sessions

import net.corda.v5.base.types.MemberX500Name

/**
 * Information about a session
 * @property sessionId Id of the session
 * @property counterparty x500 name of the counterparty
 * @property contextUserProperties user context properties used in session initiation only
 * @property contextPlatformProperties platform context properties used in session initiation only
 */
data class SessionInfo(
    val sessionId: String,
    val counterparty: MemberX500Name,
    val contextUserProperties: Map<String, String> = emptyMap(),
    val contextPlatformProperties: Map<String, String> = emptyMap()
) {
    override fun toString() = "SessionInfo(sessionId=$sessionId, counterparty=$counterparty) "
}