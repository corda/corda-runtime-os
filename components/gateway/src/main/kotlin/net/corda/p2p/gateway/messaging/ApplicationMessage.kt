package net.corda.p2p.gateway.messaging

import net.corda.v5.base.util.NetworkHostAndPort

/**
 * Interface for messages going through and from HTTP layer. Contains information about message payload and destination.
 * This is not a substitute for the message definitions used to communicate with upstream services (i.e. Link Manager).
 */
interface ApplicationMessage {
    var payload: ByteArray
    val source: NetworkHostAndPort
    val destination: NetworkHostAndPort

    /**
     * Releases the memory used by this message
     */
    fun release()
}