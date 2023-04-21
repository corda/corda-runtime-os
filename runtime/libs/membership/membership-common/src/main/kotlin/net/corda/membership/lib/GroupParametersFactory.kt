package net.corda.membership.lib

import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.data.KeyValuePairList
import net.corda.data.membership.SignedGroupParameters
import net.corda.v5.crypto.SignatureSpec
import net.corda.membership.lib.SignedGroupParameters as CordaSignedGroupParameters

/**
 * GroupParametersFactory is a factory for building [InternalGroupParameters] objects. [InternalGroupParameters] is a
 * set of parameters that all members of the group agree to abide by.
 *
 * This service can create signed and unsigned instances from avro types.
 */
interface GroupParametersFactory {
    /**
     * The [create] method allows you to create an instance of [InternalGroupParameters].
     *
     * @param parameters The group parameters as the avro type [SignedGroupParameters].
     */
    fun create(parameters: SignedGroupParameters): InternalGroupParameters

    /**
     * The [create] method allows you to create an instance of [SignedGroupParameters] from parameter components.
     *
     * @param bytes The group parameters.
     * @param signature MGM signature of the group parameters.
     * @param signatureSpec The signature's spec.
     */
    fun create(bytes: ByteArray, signature: DigitalSignatureWithKey, signatureSpec: SignatureSpec): CordaSignedGroupParameters

    /**
     * The [create] method allows you to create an instance of [UnsignedGroupParameters].
     *
     * @param parameters The list of group parameters as [KeyValuePairList].
     */
    fun create(parameters: KeyValuePairList): UnsignedGroupParameters
}
