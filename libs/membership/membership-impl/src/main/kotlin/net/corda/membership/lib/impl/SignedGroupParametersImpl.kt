package net.corda.membership.lib.impl

import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.crypto.SignatureSpec
import java.util.Objects

class SignedGroupParametersImpl(
    override val groupParameters: ByteArray,
    override val mgmSignature: DigitalSignatureWithKey,
    override val mgmSignatureSpec: SignatureSpec,
    private val deserializer: (serialisedParams: ByteArray) -> LayeredPropertyMap
) : SignedGroupParameters, InternalGroupParameters by UnsignedGroupParametersImpl(groupParameters, deserializer) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return (other is SignedGroupParametersImpl) &&
                groupParameters.contentEquals(other.groupParameters) &&
                mgmSignature == other.mgmSignature &&
                mgmSignatureSpec == other.mgmSignatureSpec
    }

    override fun hashCode(): Int = Objects.hash(groupParameters, mgmSignature, mgmSignatureSpec)
}
