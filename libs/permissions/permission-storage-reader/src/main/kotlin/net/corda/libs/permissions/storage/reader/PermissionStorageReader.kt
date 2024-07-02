package net.corda.libs.permissions.storage.reader

import net.corda.lifecycle.Resource
import net.corda.data.permissions.Permission as AvroPermission
import net.corda.data.permissions.Role as AvroRole
import net.corda.data.permissions.User as AvroUser
import net.corda.data.permissions.Group as AvroGroup

/**
 * The [PermissionStorageReader] reads permission data from storage and pushes them into a compacted topic in the message bus.
 *
 * At startup the [PermissionStorageReader] publishes data to the message bus.
 *
 * By calling the publish methods, other services can manually request publication of changed data.
 */
interface PermissionStorageReader : Resource {

    fun start()

    /**
     * Broadcasts a new user onto the messaging bus.
     *
     * @param user The user to be published.
     */
    fun publishNewUser(user: AvroUser)

    /**
     * Broadcasts an updated user onto the messaging bus.
     *
     * @param user The user to be published.
     */
    fun publishUpdatedUser(user: AvroUser)

    /**
     * Broadcasts the deleted user onto the messaging bus.
     *
     * @param loginName The login name of the user to be deleted.
     */
    fun publishDeletedUser(loginName: String)

    /**
     * Broadcasts a new role onto the messaging bus.
     *
     * @param role The role to be published.
     */
    fun publishNewRole(role: AvroRole)

    /**
     * Broadcasts an updated role onto the messaging bus.
     *
     * @param role The role to be published.
     */
    fun publishUpdatedRole(role: AvroRole)

    /**
     * Broadcasts a new permission onto the messaging bus.
     *
     * @param permission The permission to be published.
     */
    fun publishNewPermission(permission: AvroPermission)

    /**
     * Broadcasts a new group onto the messaging bus.
     *
     * @param group The group to be published.
     */
    fun publishNewGroup(group: AvroGroup)

    /**
     * Broadcasts an updated group onto the messaging bus.
     *
     * @param group The group to be published.
     */
    fun publishUpdatedGroup(group: AvroGroup)

    /**
     * Broadcasts the deleted group onto the messaging bus.
     *
     * @param id The id of the group to be deleted.
     */
    fun publishDeletedGroup(id: String)

    /**
     * Reads updated groups based on the ids passed into this method and publishes them to the message bus.
     *
     * @param ids The ids of updated groups to publish.
     */
    fun publishGroups(ids: List<String>)

    /**
     * Reads updated roles based on the ids passed into this method and publishes them to the message bus.
     *
     * @param ids The ids of updates roles to publish.
     */
    fun publishRoles(ids: List<String>)

    /**
     * Synchronize the permission summaries for all users between the database and the message bus.
     *
     * Use this overload when any user could be affected by a change to the permission system. For example, add/remove permission to/from
     * role, add/remove role to/from group, etc.
     */
    fun reconcilePermissionSummaries()
}
