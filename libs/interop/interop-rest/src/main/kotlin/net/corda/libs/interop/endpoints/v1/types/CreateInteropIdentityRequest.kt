package net.corda.libs.interop.endpoints.v1.types

import java.util.UUID

data class CreateInteropIdentityRequest (
    val x500Name: String,
    val groupId: UUID
)