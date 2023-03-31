package net.corda.membership.lib.impl

import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.crypto.SignatureSpec
import java.util.Objects

class SignedGroupParametersImpl(
    override val bytes: ByteArray,
    override val signature: DigitalSignatureWithKey,
    override val signatureSpec: SignatureSpec,
    private val deserializer: (serialisedParams: ByteArray) -> LayeredPropertyMap
) : SignedGroupParameters, InternalGroupParameters by UnsignedGroupParametersImpl(bytes, deserializer) {
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is SignedGroupParametersImpl) return false
        if (this === other) return true
        return bytes.contentEquals(other.bytes) &&
                signature == other.signature &&
                signatureSpec == other.signatureSpec
    }

    override fun hashCode(): Int = Objects.hash(bytes, signature, signatureSpec)
}
