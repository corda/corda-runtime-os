package net.corda.interop.core

import net.corda.data.interop.PersistentInteropIdentity


/**
 * This class must have a valid equals() method.
 * Currently, the kotlin data class implementation takes care of this.
 */
data class InteropIdentity(
    val x500Name: String,
    val groupId: String,
    val holdingIdentityShortHash: String,
    val facadeIds: List<String>,
    val applicationName: String,
    val endpointUrl: String,
    val endpointProtocol: String

) {
    val shortHash = Utils.computeShortHash(x500Name, groupId)

    companion object {
        fun of(holdingIdentityShortHash: String, interopIdentity: PersistentInteropIdentity): InteropIdentity {
            return InteropIdentity(
                interopIdentity.x500Name,
                interopIdentity.groupId,
                holdingIdentityShortHash,
                interopIdentity.facadeIds,
                interopIdentity.applicationName,
                interopIdentity.endpointUrl,
                interopIdentity.endpointProtocol
            )
        }
    }
}
