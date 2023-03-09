package net.corda.membership.lib

import net.corda.data.KeyValuePairList
import net.corda.v5.membership.GroupParameters
import net.corda.data.membership.SignedGroupParameters as AvroGroupParameters

/**
 * GroupParametersFactory is a factory for building [GroupParameters] objects. [GroupParameters] is a set
 * of parameters that all members of the group agree to abide by.
 *
 * This service can create signed and unsigned instances from avro types.
 */
interface GroupParametersFactory {
    /**
     * The [create] method allows you to create an instance of [SignedGroupParameters].
     *
     * @param parameters The group parameters as the avro type [AvroGroupParameters].
     */
    fun create(parameters: AvroGroupParameters): SignedGroupParameters

    /**
     * The [create] method allows you to create an instance of [GroupParameters].
     *
     * @param parameters The list of group parameters as [KeyValuePairList].
     */
    fun create(parameters: KeyValuePairList): GroupParameters
}
