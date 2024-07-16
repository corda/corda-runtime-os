package net.corda.libs.permissions.storage.reader.impl.repository

import net.corda.libs.permissions.storage.reader.repository.PermissionRepository
import net.corda.libs.permissions.storage.reader.repository.UserLogin
import net.corda.libs.permissions.storage.reader.summary.InternalUserPermissionSummary
import net.corda.orm.utils.transaction
import net.corda.permissions.model.Group
import net.corda.permissions.model.Permission
import net.corda.permissions.model.Role
import net.corda.permissions.model.User
import net.corda.permissions.query.dto.InternalPermissionQueryDto
import net.corda.permissions.query.dto.InternalUserEnabledQueryDto
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

@Suppress("TooManyFunctions")
class PermissionRepositoryImpl(private val entityManagerFactory: EntityManagerFactory) : PermissionRepository {

    companion object {
        // Query to get Permission Ids for a specific group(not including parent group permissions)
        const val permissionIdsOfGroupQuery =
            """
                SELECT DISTINCT p.id
                FROM Group g
                JOIN RoleGroupAssociation rga ON rga.group.id = :groupId
                JOIN Role r ON rga.role.id = r.id
                JOIN RolePermissionAssociation rpa ON rpa.role.id = r.id
                JOIN Permission p ON rpa.permission.id = p.id
            """

        // Query to get Permission Dto with specified Parent Group Id and Permission Id
        const val userGroupPermissionsQuery =
            """
                SELECT DISTINCT NEW net.corda.permissions.query.dto.InternalPermissionQueryDto(
                    u.loginName,
                    p.id,
                    p.groupVisibility.id, 
                    p.virtualNode, 
                    p.permissionString, 
                    p.permissionType
                )
                FROM User u, Permission p
                WHERE u.parentGroup.id = :groupId AND p.id = :permissionId
            """
    }

    override fun findAllUsers(): List<User> {
        return findAll("SELECT u from User u")
    }

    override fun findAllGroups(): List<Group> {
        return findAll("SELECT g from Group g")
    }

    override fun findAllRoles(): List<Role> {
        return findAll("SELECT r from Role r")
    }

    override fun findAllPermissions(): List<Permission> {
        return findAll("SELECT p from Permission p")
    }

    override fun findAllUsers(ids: List<String>): List<User> {
        return findAll("SELECT u from User u WHERE u.id IN :ids", ids)
    }

    override fun findAllGroups(ids: List<String>): List<Group> {
        return findAll("SELECT g from Group g WHERE g.id IN :ids", ids)
    }

    override fun findAllRoles(ids: List<String>): List<Role> {
        return findAll("SELECT r from Role r WHERE r.id IN :ids", ids)
    }

    override fun findAllPermissionSummaries(): Map<UserLogin, InternalUserPermissionSummary> {
        entityManagerFactory.transaction { entityManager ->
            val timeOfPermissionSummary = Instant.now()
            val userLoginsWithEnabledFlag = findAllUserLoginsAndEnabledFlags(entityManager)
            val userPermissionsFromRoles = findPermissionsForUsersFromRoleAssignment(entityManager)
            val userPermissionsFromGroups = findPermissionsForUsersFromGroupRoleAssignment(entityManager)

            return aggregatePermissionSummariesForUsers(
                userLoginsWithEnabledFlag,
                userPermissionsFromRoles,
                userPermissionsFromGroups,
                timeOfPermissionSummary
            )
        }
    }

    private inline fun <reified T> findAll(qlString: String): List<T> {
        return entityManagerFactory.transaction { entityManager ->
            entityManager.createQuery(qlString, T::class.java).resultList
        }
    }

    private inline fun <reified T> findAll(qlString: String, ids: List<String>): List<T> {
        return entityManagerFactory.transaction { entityManager ->
            ids.chunked(100) { chunkedIds ->
                entityManager.createQuery(qlString, T::class.java)
                    .setParameter("ids", chunkedIds)
                    .resultList
            }.flatten()
        }
    }

    private fun aggregatePermissionSummariesForUsers(
        userLogins: List<InternalUserEnabledQueryDto>,
        userPermissionsFromRoles: Map<UserLogin, List<InternalPermissionQueryDto>>,
        userPermissionsFromGroups: Map<UserLogin, List<InternalPermissionQueryDto>>,
        timeOfPermissionSummary: Instant,
    ): Map<String, InternalUserPermissionSummary> {
        return userLogins.associateBy({ it.loginName }) {
            // rolePermissionsQuery features inner joins so a user without roles won't be present in this map
            val permissionsInheritedFromRoles = (
                (userPermissionsFromRoles[it.loginName] ?: emptyList()) +
                    (userPermissionsFromGroups[it.loginName] ?: emptyList())
                )
                .toSortedSet(PermissionQueryDtoComparator())

            InternalUserPermissionSummary(
                it.loginName,
                it.enabled,
                permissionsInheritedFromRoles,
                timeOfPermissionSummary
            )
        }
    }

    private fun findAllUserLoginsAndEnabledFlags(em: EntityManager): List<InternalUserEnabledQueryDto> {
        val query = "SELECT NEW net.corda.permissions.query.dto.InternalUserEnabledQueryDto(u.loginName, u.enabled) FROM User u"
        return em.createQuery(query, InternalUserEnabledQueryDto::class.java)
            .resultList
    }

    private fun findPermissionsForUsersFromRoleAssignment(em: EntityManager): Map<UserLogin, List<InternalPermissionQueryDto>> {
        val rolePermissionsQuery = """
            SELECT DISTINCT NEW net.corda.permissions.query.dto.InternalPermissionQueryDto(
                u.loginName,
                p.id,
                p.groupVisibility.id, 
                p.virtualNode, 
                p.permissionString, 
                p.permissionType
            )
            FROM User u
                JOIN RoleUserAssociation rua ON rua.user.id = u.id
                JOIN Role r ON rua.role.id = r.id
                JOIN RolePermissionAssociation rpa ON rpa.role.id = r.id
                JOIN Permission p ON rpa.permission.id = p.id
            """
        return em.createQuery(rolePermissionsQuery, InternalPermissionQueryDto::class.java)
            .resultList
            .sortedWith(PermissionQueryDtoComparator())
            .groupBy { it.loginName }
    }

    private fun findPermissionsForUsersFromGroupRoleAssignment(em: EntityManager): Map<UserLogin, List<InternalPermissionQueryDto>> {
        // Query to get all the root groups
        val allRootGroups =
            em.createQuery("SELECT g.id FROM Group g WHERE g.parentGroup IS NULL", String::class.java).resultList

        val userPermissionsList: MutableList<InternalPermissionQueryDto> = mutableListOf()

        allRootGroups.forEach {
            val rootGroupPermissions: MutableList<String> = mutableListOf()

            getAllUserPermissions(em, it, rootGroupPermissions, userPermissionsList)
        }

        return userPermissionsList
            .sortedWith(PermissionQueryDtoComparator())
            .groupBy { it.loginName }
    }

    // Recursively updates a list with all the users and their permissions
    private fun getAllUserPermissions(
        em: EntityManager,
        currentGroupId: String,
        parentPermissionIds: MutableList<String>,
        userPermissionsList: MutableList<InternalPermissionQueryDto>
    ) {
        // Adds the Permission Ids of the current group to the parentPermissionIds list
        parentPermissionIds.addAll(
            em.createQuery(permissionIdsOfGroupQuery, String::class.java)
                .setParameter("groupId", currentGroupId)
                .resultList
        )

        // For every user directly associated with the current group,
        // create an InternalPermissionQueryDto for each permission and add to the userPermissionList
        parentPermissionIds.forEach {
            userPermissionsList.addAll(
                em.createQuery(userGroupPermissionsQuery, InternalPermissionQueryDto::class.java)
                    .setParameter("groupId", currentGroupId)
                    .setParameter("permissionId", it)
                    .resultList
            )
        }

        // Get all Group Ids that are children of the current group and recursively call this function for each child
        val childGroupIds =
            em.createQuery("SELECT g.id FROM Group g WHERE g.parentGroup.id = :groupId", String::class.java)
                .setParameter("groupId", currentGroupId)
                .resultList

        if (childGroupIds.isNotEmpty()) {
            childGroupIds.forEach {
                getAllUserPermissions(em, it, parentPermissionIds, userPermissionsList)
            }
        }
    }
}
