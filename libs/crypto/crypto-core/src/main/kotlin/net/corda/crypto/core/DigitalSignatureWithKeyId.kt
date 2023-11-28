package net.corda.crypto.core

import net.corda.base.internal.OpaqueBytes
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash

class DigitalSignatureWithKeyId(
    private val by: SecureHash,
    bytes: ByteArray
) : DigitalSignature.WithKeyId, OpaqueBytes(bytes) {
    override fun getBy() = by
}
