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

    private val ROOT = null

    companion object {
        // Query to get all the Permissions for each Group
        // InternalPermissionWithParentGroupQueryDto.loginName is empty to signify that it is a Group
        const val userGroupPermissionsQuery =
            """
                SELECT DISTINCT NEW net.corda.permissions.query.dto.InternalPermissionWithParentGroupQueryDto(
                g.id,
                p.id,
                p.groupVisibility.id, 
                p.virtualNode, 
                p.permissionString, 
                p.permissionType,
                g.parentGroup.id,
                ''
            )
            FROM Group g
            JOIN RoleGroupAssociation rga ON rga.group.id = g.id
            JOIN Role r ON rga.role.id = r.id
            JOIN RolePermissionAssociation rpa ON rpa.role.id = r.id
            JOIN Permission p ON rpa.permission.id = p.id
            """

        // Query to get all the Permissions for each User
        const val allUsersPermissionQuery =
            """
                SELECT DISTINCT NEW net.corda.permissions.query.dto.InternalPermissionWithParentGroupQueryDto(
                u.id,
                p.id,
                p.groupVisibility.id, 
                p.virtualNode, 
                p.permissionString, 
                p.permissionType,
                u.parentGroup.id,
                u.loginName
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
            val userPermissionsFromRolesAndGroups = findPermissionsForUsersFromGroupAndRoleAssignment(entityManager)

            return aggregatePermissionSummariesForUsers(
                userLoginsWithEnabledFlag,
                userPermissionsFromRolesAndGroups,
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
        userPermissionsFromRolesAndGroups: Map<UserLogin, List<InternalPermissionQueryDto>>,
        timeOfPermissionSummary: Instant,
    ): Map<String, InternalUserPermissionSummary> {
        return userLogins.associateBy({ it.loginName }) {
            // rolePermissionsQuery features inner joins so a user without roles won't be present in this map
            val permissionsInheritedFromRoles = (
                (userPermissionsFromRolesAndGroups[it.loginName] ?: emptyList()).toSortedSet(PermissionQueryDtoComparator())
                )

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

    private fun findPermissionsForUsersFromGroupAndRoleAssignment(em: EntityManager): Map<UserLogin, List<InternalPermissionQueryDto>> {
        // Gets a map with a key of Group ID and value of its associated list of Permissions
        val groupPermissionMap = em.createQuery(
            userGroupPermissionsQuery,
            InternalPermissionWithParentGroupQueryDto::class.java
        ).resultList.groupBy { it.id }

        // Gets a map with a key of User ID and value of its associated list of Permissions
        val userPermissionMap = em.createQuery(
            allUsersPermissionQuery,
            InternalPermissionWithParentGroupQueryDto::class.java
        ).resultList.groupBy { it.id }

        val parentIdToChildListMap = HashMap<String?, MutableList<Node>>()

        // For each Group check if the first permission's parentGroupId exists in parentIdToChildListMap and add the Node to its value
        // otherwise create a new entry with the parentGroupId and add the Node
        groupPermissionMap.forEach { groupIdAndPermissionList ->
            parentIdToChildListMap.computeIfAbsent(groupIdAndPermissionList.value.first().parentGroupId) { mutableListOf() }
                .add(Node(groupIdAndPermissionList.value))
        }

        // For each User check if the first permission's parentGroupId exists in parentIdToChildListMap and add the Node to its value
        // otherwise create a new entry with the parentGroupId and add the Node
        userPermissionMap.forEach { userIdAndPermissionList ->
            parentIdToChildListMap.computeIfAbsent(userIdAndPermissionList.value.first().parentGroupId) { mutableListOf() }
                .add(Node(userIdAndPermissionList.value))
        }

        val userPermissions = mutableMapOf<UserLogin, List<InternalPermissionQueryDto>>()

        // For each root node build a tree and calculate the permissions for each user
        parentIdToChildListMap[ROOT]!!.forEach { root ->
            buildTree(root, parentIdToChildListMap)
            calculatePermissions(root, mutableListOf(), userPermissions)
        }
        return userPermissions
    }

    // Builds the tree from the root node
    private fun buildTree(
        node: Node,
        parentIdToChildListMap: HashMap<String?, MutableList<Node>>
    ) {
        parentIdToChildListMap[node.permissionList.first().id]?.forEach { child ->
            node.addChild(child)
            buildTree(child, parentIdToChildListMap)
        }
    }

    // Calculates all the permissions for each user by traversing the tree and adding the permissions to the userPermissions map
    private fun calculatePermissions(
        node: Node,
        permissions: MutableList<InternalPermissionWithParentGroupQueryDto>,
        userPermissions: MutableMap<UserLogin, List<InternalPermissionQueryDto>>
    ) {
        // Adds the current Node's permissions to the permissions list
        permissions.addAll(node.permissionList)

        // If the current Node has no children, add the permissions to the userPermissions map
        if (!node.hasChildren()) {
            // If loginName there is no loginName, it means it is a group and we don't want to add it to the userPermissions map
            if (!node.permissionList.first().loginName.isNullOrEmpty()) {
                userPermissions[node.permissionList.first().loginName!!] = permissions.map {
                    InternalPermissionQueryDto(
                        it.loginName!!,
                        it.permissionId,
                        it.groupVisibility,
                        it.virtualNode,
                        it.permissionString,
                        it.permissionType
                    )
                }
            }
        } else {
            // Recursively calls calculatePermissions for each child of the current Node
            node.children.forEach { child ->
                calculatePermissions(
                    child,
                    permissions,
                    userPermissions
                )
            }
        }

        // Remove the current Node's permissions from the permissions list when we go up the tree
        for (count in 1..node.permissionList.size) {
            permissions.removeLast()
        }
    }

    private data class Node(val permissionList: List<InternalPermissionWithParentGroupQueryDto>) {
        val children: MutableList<Node> = mutableListOf()

        fun addChild(node: Node) {
            children.add(node)
        }

        fun hasChildren(): Boolean =
            children.size >= 1
    }
}
