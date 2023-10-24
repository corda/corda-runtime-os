package net.corda.interop.core

import java.util.*
import net.corda.crypto.core.ShortHash
import net.corda.data.interop.PersistentInteropIdentity
import net.corda.v5.application.interop.facade.FacadeId


data class InteropIdentity(
    val x500Name: String,
    val groupId: UUID,
    val owningVirtualNodeShortHash: ShortHash,
    val facadeIds: List<FacadeId>,
    val applicationName: String,
    val endpointUrl: String,
    val endpointProtocol: String,
    var enabled: Boolean
) {
    val shortHash = Utils.computeShortHash(x500Name, groupId)

    companion object {
        fun of(interopIdentity: PersistentInteropIdentity): InteropIdentity = InteropIdentity(
            interopIdentity.x500Name,
            UUID.fromString(interopIdentity.groupId),
            ShortHash.of(interopIdentity.virtualNodeShortHash),
            interopIdentity.facadeIds.map { FacadeId.of(it) },
            interopIdentity.applicationName,
            interopIdentity.endpointUrl,
            interopIdentity.endpointProtocol,
            interopIdentity.enabled
        )
    }
}
