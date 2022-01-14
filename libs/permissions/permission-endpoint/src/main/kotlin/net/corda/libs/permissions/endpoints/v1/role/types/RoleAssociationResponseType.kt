package net.corda.libs.permissions.endpoints.v1.role.types

import java.time.Instant

/**
 * Response type representing a Role association.
 */
data class RoleAssociationResponseType(

    /**
     * Id of the Role.
     */
    val roleId: String,

    /**
     * Time the Role association was created.
     */
    val createTimestamp: Instant
)