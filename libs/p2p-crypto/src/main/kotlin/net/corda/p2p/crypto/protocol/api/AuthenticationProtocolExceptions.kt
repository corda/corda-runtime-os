package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.utils.certPathToString
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.security.cert.X509Certificate

/**
 * Thrown when the max message size proposed by our peer was invalid.
 */
class InvalidMaxMessageSizeProposedError(msg: String): CordaRuntimeException(msg)

/**
 * Throw when the peers certificate is invalid.
 */
class InvalidPeerCertificate(msg: String?, certChain: Array<X509Certificate?>?):
    CordaRuntimeException(("$msg" + certChain?.let { certPathToString(certChain) })) {
    constructor(msg: String?, cert: X509Certificate): this(msg, arrayOf(cert))
    constructor(msg: String?): this(msg, null)
}