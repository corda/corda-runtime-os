package net.corda.crypto.core

import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash

class DigitalSignatureWithKeyId(
    private val by: SecureHash,
    bytes: ByteArray
    ) : DigitalSignature.WithKeyId, OpaqueBytes(bytes) {
    // TODO Not sure why Kotlin generated getter cannot override?
    override fun getBy() = by
}