package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.utils.certPathToString
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.security.cert.X509Certificate

/**
 * Thrown when the max message size proposed by our peer was invalid.
 */
class InvalidMaxMessageSizeProposedError(msg: String) : CordaRuntimeException(msg)

/**
 * Throw when the peers certificate is invalid.
 */
class InvalidPeerCertificate private constructor(msg: String?, certChain: String) : CordaRuntimeException((msg ?: "") + certChain) {
    constructor(msg: String?, certChain: Array<X509Certificate?>?) : this(msg, "\nCertificate chain: \n" + certPathToString(certChain))
    constructor(msg: String, cert: X509Certificate) : this(msg, "\nCertificate: \n" + certPathToString(arrayOf(cert)))
    constructor(msg: String) : this(msg, "")
}
