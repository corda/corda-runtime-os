package net.corda.membership.lib

import net.corda.data.KeyValuePairList
import net.corda.v5.membership.GroupParameters

/**
 * Internal representation of the group parameters which exposes additional metadata about the group parameters to
 * internal corda services.
 */
interface InternalGroupParameters: GroupParameters {
    /**
     * The AVRO serialised bytes that the MGM signed over during distribution.
     * This byte array can be deserialized as a [KeyValuePairList].
     */
    val bytes: ByteArray
}