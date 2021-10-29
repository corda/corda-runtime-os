package net.corda.libs.permissions.storage.reader

import net.corda.lifecycle.Lifecycle

/**
 * The [PermissionStorageReader] reads permission data from storage and pushes them into a compacted topic in the message bus.
 *
 * At startup the [PermissionStorageReader] publishes data to the message bus.
 *
 * By calling [publish] other services can manually request publication of changed data.
 */
interface PermissionStorageReader : Lifecycle {

    /**
     * Reads updated permission data for users, groups and roles based on the ids passed into this method and publishes them to the message
     * bus.
     *
     * @param userIds The ids of updated users to publish.
     * @param groupIds The ids of updated groups to publish.
     * @param rolesIds The ids of updates roles to publish.
     */
    fun publish(userIds: List<String>, groupIds: List<String>, rolesIds: List<String>)
}