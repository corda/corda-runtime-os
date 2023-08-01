package net.corda.interop.core

import net.corda.data.interop.PersistentInteropIdentity


data class InteropIdentity(
    val x500Name: String,
    val groupId: String,
    val owningVirtualNodeShortHash: String,
    val facadeIds: List<String>,
    val applicationName: String,
    val endpointUrl: String,
    val endpointProtocol: String
) {
    val shortHash = Utils.computeShortHash(x500Name, groupId)

    companion object {
        fun of(virtualNodeShortHash: String, interopIdentity: PersistentInteropIdentity): InteropIdentity {
            return InteropIdentity(
                interopIdentity.x500Name,
                interopIdentity.groupId,
                virtualNodeShortHash,
                interopIdentity.facadeIds,
                interopIdentity.applicationName,
                interopIdentity.endpointUrl,
                interopIdentity.endpointProtocol
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InteropIdentity

        if (x500Name != other.x500Name) return false
        if (groupId != other.groupId) return false
        return shortHash == other.shortHash
    }

    override fun hashCode(): Int {
        var result = x500Name.hashCode()
        result = 31 * result + groupId.hashCode()
        result = 31 * result + owningVirtualNodeShortHash.hashCode()
        result = 31 * result + shortHash.hashCode()
        return result
    }
}
