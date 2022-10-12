package net.corda.p2p.crypto.protocol.api

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Thrown when the max message size proposed by our peer was invalid.
 */
class InvalidMaxMessageSizeProposedError(msg: String): CordaRuntimeException(msg)

/**
 * Throw when the peers certificate is invalid.
 */
class InvalidPeerCertificate(msg: String?): CordaRuntimeException(msg)