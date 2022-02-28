package net.corda.libs.permissions.cache.factory

import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.Group
import net.corda.data.permissions.Permission
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.permissions.cache.processor.PermissionCacheTopicProcessor

/**
 * Factory for creating topic processors for the permission system.
 */
interface PermissionCacheTopicProcessorFactory {
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

    /**
     * Create a topic processor for Permission Summaries.
     *
     * @param permissionSummaryData the instance of a ConcurrentHashMap holding the Permission Summary data.
     * @param onSnapshotCallback the callback invoked after snapshot has been received.
     */
    fun createPermissionSummaryTopicProcessor(
        permissionSummaryData: ConcurrentHashMap<String, UserPermissionSummary>,
        onSnapshotCallback: () -> Unit,
    ): PermissionCacheTopicProcessor<String, UserPermissionSummary>
}