package net.corda.p2p.crypto.protocol.api

/**
 * A marker interface supposed to be implemented by the different types of sessions supported by the authentication protocol.
 */
interface Session {
    val sessionId: String
}