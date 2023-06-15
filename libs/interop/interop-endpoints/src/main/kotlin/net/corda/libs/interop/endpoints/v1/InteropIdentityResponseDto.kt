package net.corda.libs.interop.endpoints.v1

import java.util.*

data class InteropIdentityResponseDto(
    val x500Name: String,
    val groupId: UUID
)
