package net.corda.libs.permissions.endpoints.v1.permission.types

import java.time.Instant

/**
 * Response type representing a Permission association to be returned to the caller.
 */
data class PermissionAssociationResponseType(

    /**
     * Id of the Permission.
     */
    val id: String,

    /**
     * Time when the Permission association was created.
     */
    val createdTimestamp: Instant
)