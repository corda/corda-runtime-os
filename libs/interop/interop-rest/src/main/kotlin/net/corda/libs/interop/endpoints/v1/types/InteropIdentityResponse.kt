package net.corda.libs.interop.endpoints.v1.types

import net.corda.v5.application.interop.facade.FacadeId
import java.util.UUID

data class InteropIdentityResponse(
    val x500Name: String,
    val groupId: UUID,
    val owningVirtualNodeShortHash: String,
    val facadeIds: List<FacadeId>,
    val applicationName: String,
    val endpointUrl: String,
    val endpointProtocol: String,
    val enabled: Boolean
)