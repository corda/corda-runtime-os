package net.corda.p2p.crypto.protocol.api

import net.corda.v5.base.exceptions.CordaRuntimeException
import java.util.*

class WrongPublicKeyHashException(expectedHash: ByteArray?, actualHashes: Collection<ByteArray>) :
    CordaRuntimeException(
        "Expected the SHA-256 hash of the public key, used to validate the InitiatorHandshakeMessage, to be " +
            "${expectedHash.toBase64()} but was ${actualHashes.map { it.toBase64() }}.",
    )

private fun ByteArray?.toBase64(): String {
    return Base64.getEncoder().encodeToString(this)
}
