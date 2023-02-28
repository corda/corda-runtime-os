package net.corda.membership.lib

import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.membership.GroupParameters

/**
 * Extension of [GroupParameters] which exposes additional values related to signing for internal consumption.
 */
interface SignedGroupParameters : GroupParameters {
    /**
     * The serialised bytes that the MGM signed over during distribution.
     */
    val bytes: ByteArray

    /**
     * The MGM's signature over the group parameters.
     */
    val signature: DigitalSignature.WithKey
}