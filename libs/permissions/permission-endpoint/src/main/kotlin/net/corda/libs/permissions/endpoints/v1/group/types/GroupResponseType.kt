package net.corda.libs.permissions.endpoints.v1.group.types
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
     * Human-readable name of a group.
     */
    val name: String,

    /**
     * The group to which the Group belongs.
     */
    val parentGroupId: String?
)