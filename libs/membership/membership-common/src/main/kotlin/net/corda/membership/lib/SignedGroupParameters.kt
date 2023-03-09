package net.corda.membership.lib

import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.membership.GroupParameters

/**
 * Extension of [GroupParameters] which exposes additional values related to signing for internal consumption.
 */
interface SignedGroupParameters : InternalGroupParameters {
    /**
     * The MGM's signature over the AVRO serialised group parameters stored as [bytes].
     */
    val signature: DigitalSignature.WithKey
}