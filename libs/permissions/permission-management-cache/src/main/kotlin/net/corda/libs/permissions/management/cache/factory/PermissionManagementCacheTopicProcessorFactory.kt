package net.corda.libs.permissions.management.cache.factory

import net.corda.data.permissions.Group
import net.corda.data.permissions.Permission
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.libs.permissions.cache.processor.PermissionCacheTopicProcessor
import java.util.concurrent.ConcurrentHashMap

/**
 * Factory for creating topic processors for the permission management cache.
 */
interface PermissionManagementCacheTopicProcessorFactory {
    /**
     * Create a topic processor for Users.
     *
     * @param userData the instance of a ConcurrentHashMap holding the User data.
     * @param onSnapshotCallback the callback invoked after snapshot has been received.
     */
    fun createUserTopicProcessor(
        userData: ConcurrentHashMap<String, User>,
        onSnapshotCallback: () -> Unit,
    ): PermissionCacheTopicProcessor<String, User>

    /**
     * Create a topic processor for Groups.
     *
     * @param groupData the instance of a ConcurrentHashMap holding the Group data.
     * @param onSnapshotCallback the callback invoked after snapshot has been received.
     */
    fun createGroupTopicProcessor(
        groupData: ConcurrentHashMap<String, Group>,
        onSnapshotCallback: () -> Unit,
    ): PermissionCacheTopicProcessor<String, Group>

    /**
     * Create a topic processor for Roles.
     *
     * @param roleData the instance of a ConcurrentHashMap holding the Role data.
     * @param onSnapshotCallback the callback invoked after snapshot has been received.
     */
    fun createRoleTopicProcessor(
        roleData: ConcurrentHashMap<String, Role>,
        onSnapshotCallback: () -> Unit,
    ): PermissionCacheTopicProcessor<String, Role>

    /**
     * Create a topic processor for Permissions.
     *
     * @param permissionData the instance of a ConcurrentHashMap holding the Permission data.
     * @param onSnapshotCallback the callback invoked after snapshot has been received.
     */
    fun createPermissionTopicProcessor(
        permissionData: ConcurrentHashMap<String, Permission>,
        onSnapshotCallback: () -> Unit,
    ): PermissionCacheTopicProcessor<String, Permission>
}
