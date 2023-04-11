package net.corda.ledger.common.data.transaction

import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import java.util.Objects

@CordaSerializable
data class SignedGroupParametersContainer(
    val hash: SecureHash,
    val bytes: ByteArray,
    val signature: DigitalSignatureWithKey,
    val signatureSpec: SignatureSpec
) {
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is SignedGroupParametersContainer) return false
        if (this === other) return true
        return hash == other.hash &&
                bytes.contentEquals(other.bytes) &&
                signature == other.signature &&
                signatureSpec == other.signatureSpec
    }

    override fun hashCode(): Int = Objects.hash(hash, bytes, signature, signatureSpec)
}