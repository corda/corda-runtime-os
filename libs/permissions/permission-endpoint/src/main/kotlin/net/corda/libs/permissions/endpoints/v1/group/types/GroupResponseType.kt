package net.corda.libs.permissions.endpoints.v1.group.types

import net.corda.libs.permissions.endpoints.v1.role.types.RoleAssociationResponseType
import net.corda.libs.permissions.endpoints.v1.user.types.PropertyResponseType
import java.time.Instant

/**
 * Response type representing a Group to be returned to the caller.
 */
data class GroupResponseType(
    /**
     * Id of the Group.
     */
    val id: String,

    /**
     * Time the Group was last updated.
     */
    val updateTimestamp: Instant,

    /**
     * Version of the Group.
     */
    val version: Int,

    /**
     * Human-readable name of a group.
     */
    val name: String,

    /**
     * The ID of the parent group.
     */
    val parentGroupId: String?,

    /**
     * Group properties.
     */
    val properties: List<PropertyResponseType>,

    /**
     * The Group's role associations.
     */
    val roleAssociations: List<RoleAssociationResponseType>
)
