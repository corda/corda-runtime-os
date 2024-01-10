package net.corda.flow.application.sessions

import net.corda.v5.base.types.MemberX500Name
import java.time.Duration

/**
 * Information about a session
 * @property sessionId Id of the session
 * @property counterparty x500 name of the counterparty
 * @property requireClose True if the initiated party sends a close message when a session is closed.
 * @property sessionTimeout The duration that Corda waits when no message has been received from a counterparty before
 *                          causing the session to error. If set to `null`, value set in Corda Configuration is used.
 * @property contextUserProperties user context properties used in session initiation only
 * @property contextPlatformProperties platform context properties used in session initiation only
 */
data class SessionInfo(
    val sessionId: String,
    val counterparty: MemberX500Name,
    val requireClose: Boolean = true,
    val sessionTimeout: Duration? = null,
    val contextUserProperties: Map<String, String> = emptyMap(),
    val contextPlatformProperties: Map<String, String> = emptyMap()
) {
    override fun toString() = "SessionInfo(sessionId=$sessionId, counterparty=$counterparty) "
}