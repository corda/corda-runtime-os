package net.corda.libs.permissions.storage.reader

import net.corda.lifecycle.Lifecycle

/**
 * The [PermissionStorageReader] reads permission data from storage and pushes them into a compacted topic in the message bus.
 *
 * At startup the [PermissionStorageReader] publishes data to the message bus.
 *
 * By calling the publish methods, other services can manually request publication of changed data.
 */
interface PermissionStorageReader : Lifecycle {

    /**
     * Reads updated users based on the ids passed into this method and publishes them to the message bus.
     *
     * @param ids The ids of updated users to publish.
     */
    fun publishUsers(ids: List<String>)

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
}