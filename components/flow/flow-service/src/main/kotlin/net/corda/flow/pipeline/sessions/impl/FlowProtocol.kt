package net.corda.flow.pipeline.sessions.impl

/**
 * Represents the protocol supported by a particular initiator or responder flow.
 *
 * @property protocol The protocol name
 * @property version The protocol version
 */
data class FlowProtocol(
    val protocol: String,
    val version: Int
)
