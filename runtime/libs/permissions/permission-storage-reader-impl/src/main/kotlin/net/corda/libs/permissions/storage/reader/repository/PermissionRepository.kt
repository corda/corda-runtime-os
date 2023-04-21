package net.corda.libs.permissions.storage.reader.repository

import net.corda.libs.permissions.storage.reader.summary.InternalUserPermissionSummary
import net.corda.permissions.model.Group
import net.corda.permissions.model.Permission
import net.corda.permissions.model.Role
import net.corda.permissions.model.User

typealias UserLogin = String

/**
 * The [PermissionRepository] reads permission data from the database.
 */
interface PermissionRepository {

    /**
     * Find all users.
     *
     * @return The [User] entities that exist in the database.
     */
    fun findAllUsers(): List<User>

    /**
     * Find all groups.
     *
     * @return The [Group] entities that exist in the database.
     */
    fun findAllGroups(): List<Group>

    /**
     * Find all roles.
     *
     * @return The [Role] entities that exist in the database.
     */
    fun findAllRoles(): List<Role>

    /**
     * Find all permissions.
     *
     * @return The [Permission] entities that exist in the database.
     */
    fun findAllPermissions(): List<Permission>

    /**
     * Find all users with that match any of the passed in [ids] (uses an `IN`) clause.
     *
     * @param ids The ids to use within the SQL `IN` clause.
     *
     * @return The [User] entities that exist in the database.
     */
    fun findAllUsers(ids: List<String>): List<User>

    /**
     * Find all groups with that match any of the passed in [ids] (uses an `IN`) clause.
     *
     * @param ids The ids to use within the SQL `IN` clause.
     *
     * @return The [Group] entities that exist in the database.
     */
    fun findAllGroups(ids: List<String>): List<Group>

    /**
     * Find all roles with that match any of the passed in [ids] (uses an `IN`) clause.
     *
     * @param ids The ids to use within the SQL `IN` clause.
     *
     * @return The [Role] entities that exist in the database.
     */
    fun findAllRoles(ids: List<String>): List<Role>

    /**
     * Find all permissions for all users and return a summary of the permissions for each user in a list of [InternalUserPermissionSummary]
     * objects.
     *
     * @return list of permission summaries for each user.
     */
    fun findAllPermissionSummaries(): Map<UserLogin, InternalUserPermissionSummary>
}