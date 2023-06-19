package net.corda.libs.interop.endpoints.v1.types

import java.util.UUID

data class InteropIdentityResponseType(
    /**
     * The x500 name of the identity
     */
    val x500Name: String,

    /**
     * The id of the interop group
     */
    val groupId: UUID
)