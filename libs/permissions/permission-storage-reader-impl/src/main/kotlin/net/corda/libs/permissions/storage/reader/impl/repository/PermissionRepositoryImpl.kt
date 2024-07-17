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
import net.corda.permissions.query.dto.InternalPermissionWithParentGroupQueryDto
import net.corda.permissions.query.dto.InternalUserEnabledQueryDto
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

@Suppress("TooManyFunctions")
class PermissionRepositoryImpl(private val entityManagerFactory: EntityManagerFactory) : PermissionRepository {

    companion object {
        const val groupPermissionsQuery =
            """
            SELECT DISTINCT NEW net.corda.permissions.query.dto.InternalPermissionWithParentGroupQueryDto(
                    g.id,
                    p.id,
                    p.groupVisibility.id, 
                    p.virtualNode, 
                    p.permissionString, 
                    p.permissionType,
                    g.parentGroup.id
                )
            FROM Group g
            JOIN RoleGroupAssociation rga ON rga.group.id = g.id
            JOIN Role r ON rga.role.id = r.id
            JOIN RolePermissionAssociation rpa ON rpa.role.id = r.id
            JOIN Permission p ON rpa.permission.id = p.id
            """

        const val usersPermissionQuery =
            """
            SELECT DISTINCT NEW net.corda.permissions.query.dto.InternalPermissionWithParentGroupQueryDto(
                u.id,
                p.id,
                p.groupVisibility.id, 
                p.virtualNode, 
                p.permissionString, 
                p.permissionType,
                u.parentGroup.id
            )
            FROM User u
                JOIN RoleUserAssociation rua ON rua.user.id = u.id
                JOIN Role r ON rua.role.id = r.id
                JOIN RolePermissionAssociation rpa ON rpa.role.id = r.id
                JOIN Permission p ON rpa.permission.id = p.id
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
        val groupPermissionMap = em.createQuery(
            groupPermissionsQuery,
            InternalPermissionWithParentGroupQueryDto::class.java
        ).resultList.groupBy { it.id }
        val userPermissionMap = em.createQuery(
            usersPermissionQuery,
            InternalPermissionWithParentGroupQueryDto::class.java
        ).resultList.groupBy { it.id }
        val userGroupHierarchyMap = userPermissionMap.mapValues {
            var parent = it.value.first().parentGroupId
            val userGroupHierarchyList = mutableListOf(parent)
            while (!parent.isNullOrBlank()) {
                parent = groupPermissionMap[parent]?.first()?.parentGroupId
                userGroupHierarchyList.add(parent)
            }
            userGroupHierarchyList
        }

        val allUserPermissionsMap = mutableMapOf<String, MutableList<InternalPermissionQueryDto>>()

        userGroupHierarchyMap.forEach { userGroupHierarchy ->
            userGroupHierarchy.value.forEach { groupId ->
                groupPermissionMap[groupId]?.let { groupPermissions ->
                    groupPermissions.forEach { groupPermission ->
                        allUserPermissionsMap[userGroupHierarchy.key]?.add(
                            InternalPermissionQueryDto(
                                userGroupHierarchy.key,
                                groupPermission.id,
                                groupPermission.groupVisibility,
                                groupPermission.virtualNode,
                                groupPermission.permissionString,
                                groupPermission.permissionType
                            )
                        )
                    }
                }
            }
        }
        return allUserPermissionsMap
    }
}
