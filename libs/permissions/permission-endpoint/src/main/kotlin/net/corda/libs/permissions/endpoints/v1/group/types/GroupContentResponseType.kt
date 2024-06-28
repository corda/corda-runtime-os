package net.corda.libs.permissions.endpoints.v1.group.types

import net.corda.libs.permissions.endpoints.v1.role.types.RoleAssociationResponseType
import net.corda.libs.permissions.endpoints.v1.user.types.PropertyResponseType
import java.time.Instant

/**
 * Response type representing the content of a Group.
 */
data class GroupContentResponseType(
    /**
     * Id of the Group.
     */
    val id: String,

    /**
     * Time the Group was last updated.
     */
    val updateTimestamp: Instant,

    /**
     * Human-readable name of a group.
     */
    val name: String,

    /**
     * ID of the parent group.
     */
    val parentGroupId: String,

    /**
     * Group properties.
     */
    val properties: List<PropertyResponseType>,

    /**
     * The Group's role associations.
     */
    val roleAssociations: List<RoleAssociationResponseType>,

    /**
     * List of user IDs in the group.
     */
    val users: Set<String>,

    /**
     * List of subgroups associated with the group.
     */
    val subgroups: Set<String>
)
