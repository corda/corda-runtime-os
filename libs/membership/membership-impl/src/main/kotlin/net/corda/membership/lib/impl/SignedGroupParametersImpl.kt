package net.corda.membership.lib.impl

import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.membership.GroupParameters
import java.util.Objects

class SignedGroupParametersImpl(
    override val bytes: ByteArray,
    override val signature: DigitalSignature.WithKey,
    private val deserializer: (serialisedParams: ByteArray) -> LayeredPropertyMap
) : SignedGroupParameters, GroupParameters by UnsignedGroupParametersImpl(bytes, deserializer) {
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is SignedGroupParametersImpl) return false
        if (this === other) return true
        return bytes.contentEquals(other.bytes) &&
                signature == other.signature
    }

    override fun hashCode(): Int = Objects.hash(bytes, signature)
}
