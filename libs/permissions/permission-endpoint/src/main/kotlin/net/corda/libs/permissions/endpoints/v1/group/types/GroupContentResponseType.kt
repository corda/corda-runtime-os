package net.corda.libs.permissions.endpoints.v1.group.types

/**
 * Response type representing the content of a Group.
 */
data class GroupContentResponseType(
    /**
     * Id of the Group.
     */
    val id: String,

    /**
     * Human-readable name of a group.
     */
    val name: String,

    /**
     * List of users in the group.
     */
    val users: List<String>,

    /**
     * List of roles associated with the group.
     */
    val roles: List<String>
)