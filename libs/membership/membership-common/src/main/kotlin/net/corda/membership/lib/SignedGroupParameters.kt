package net.corda.membership.lib

import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.v5.crypto.SignatureSpec

/**
 * Extension of [InternalGroupParameters] which exposes additional values related to signing for internal consumption.
 */
interface SignedGroupParameters : InternalGroupParameters {
    /**
     * The MGM's signature over the AVRO serialised group parameters stored as [groupParameters].
     */
    val mgmSignature: DigitalSignatureWithKey

    /**
     * The signature spec for the MGM's signature.
     */
    val mgmSignatureSpec: SignatureSpec
}
