package net.corda.membership.lib

import net.corda.data.KeyValuePairList
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.GroupParameters

/**
 * Internal representation of the group parameters which exposes additional metadata about the group parameters to
 * internal corda services.
 */
interface InternalGroupParameters : GroupParameters {
    /**
     * The AVRO serialized group parameters.
     * This byte array can be deserialized as a [KeyValuePairList].
     * The serialized form of the group parameters is always the source of truth over deserialized params to support
     * signing.
     */
    val groupParameters: ByteArray

    /**
     * Returns the [SecureHash] of the group parameters. The group parameters hash is a hash over the group parameters
     * serialised byte array available as [groupParameters].
     */
    val hash: SecureHash

    /**
     * Transforms [InternalGroupParameters] into [Map].
     */
    fun toMap() = entries.associate { it.key to it.value }
}
