package net.corda.libs.permissions.storage.reader.repository

import net.corda.permissions.model.Group
import net.corda.permissions.model.Role
import net.corda.permissions.model.User

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
}