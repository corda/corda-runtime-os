package net.corda.libs.interop.endpoints.v1.types

import java.util.UUID

data class RestInteropIdentity(
    val x500Name: String,
    val groupId: UUID,
    val shortHash: String?,
    val facadeIds: List<String>,
    val applicationName: String,
    val endpointUrl: String,
    val endpointProtocol: String

)
