package net.corda.libs.permissions.storage.reader.util

import net.corda.libs.permissions.storage.reader.impl.repository.PermissionQueryDtoComparator
import net.corda.libs.permissions.storage.reader.repository.UserLogin
import net.corda.libs.permissions.storage.reader.summary.InternalUserPermissionSummary
import net.corda.permissions.query.dto.InternalPermissionQueryDto
import net.corda.permissions.query.dto.InternalPermissionWithParentGroupQueryDto
import net.corda.permissions.query.dto.InternalUserEnabledQueryDto
import net.corda.permissions.query.dto.InternalUserGroup
import net.corda.permissions.query.dto.Permission
import java.time.Instant
import javax.persistence.EntityManager

internal object PermissionUserUtil {
    // Query to get all the Permissions for each Group
    // InternalPermissionWithParentGroupQueryDto.loginName is empty to signify that it is a Group
    const val allGroupPermissionsQuery =
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
            LEFT JOIN RoleGroupAssociation rga ON rga.group.id = g.id
            LEFT JOIN Role r ON rga.role.id = r.id
            LEFT JOIN RolePermissionAssociation rpa ON rpa.role.id = r.id
            LEFT JOIN Permission p ON rpa.permission.id = p.id
            """

    // Query to get all the Permissions for each User
    const val allUsersPermissionsQuery =
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
            LEFT JOIN RoleUserAssociation rua ON rua.user.id = u.id
            LEFT JOIN Role r ON rua.role.id = r.id
            LEFT JOIN RolePermissionAssociation rpa ON rpa.role.id = r.id
            LEFT JOIN Permission p ON rpa.permission.id = p.id
            """
    fun calculatePermissionsForUsers(em: EntityManager): Map<UserLogin, List<InternalPermissionQueryDto>> {
        val groupList = getInternalPermissionWithParentGroupQueryDtoToInternalUserGroup(em, allGroupPermissionsQuery)
        val userList = getInternalPermissionWithParentGroupQueryDtoToInternalUserGroup(em, allUsersPermissionsQuery)

        // Generate a map that allows us to find all children given a parentId
        val parentIdToChildListMap = HashMap<String?, MutableList<Node>>()
        getParentToChildListMap(parentIdToChildListMap, groupList)
        getParentToChildListMap(parentIdToChildListMap, userList)

        return calculatePermissions(parentIdToChildListMap)
    }

    fun aggregatePermissionSummariesForUsers(
        userLogins: List<InternalUserEnabledQueryDto>,
        userPermissionsFromRolesAndGroups: Map<UserLogin, List<InternalPermissionQueryDto>>,
        timeOfPermissionSummary: Instant,
    ): Map<String, InternalUserPermissionSummary> {
        val userPermissionMap = userPermissionsFromRolesAndGroups.filter { it.value.isNotEmpty() }
        return userLogins.associateBy({ user -> user.loginName }) { user ->
            // rolePermissionsQuery features inner joins so a user without roles won't be present in this map
            val permissionsInheritedFromRoles = (
                    (userPermissionMap[user.loginName] ?: emptyList()).toSortedSet(
                        PermissionQueryDtoComparator())
                    )

            InternalUserPermissionSummary(
                user.loginName,
                user.enabled,
                permissionsInheritedFromRoles,
                timeOfPermissionSummary
            )
        }
    }

    private fun getInternalPermissionWithParentGroupQueryDtoToInternalUserGroup(
        em: EntityManager,
        query: String
    ): List<InternalUserGroup> {
        // Gets a list with an ID, parentGroup ID and associated list of Permissions
        return em.createQuery(
            query,
            InternalPermissionWithParentGroupQueryDto::class.java
        ).resultList.groupBy { it.id }.map { (id, permissionList) ->
            InternalUserGroup(
                id,
                permissionList.firstOrNull()?.parentGroupId,
                permissionList.firstOrNull()?.loginName,
                permissionList.filter { !it.permissionString.isNullOrEmpty() }.map { permission ->
                    Permission(
                        permission.permissionId,
                        permission.groupVisibility,
                        permission.virtualNode,
                        permission.permissionString!!,
                        permission.permissionType!!
                    )
                }
            )
        }
    }

    private fun getParentToChildListMap(
        parentIdToChildListMap: HashMap<String?, MutableList<Node>>,
        userGroupList: List<InternalUserGroup>
    ) {
        // For each element check if the first permission's parentId exists in parentIdToChildListMap and add the Node to its value
        // otherwise create a new entry with the parentGroupId and add the Node
        userGroupList.forEach { userGroup ->
            parentIdToChildListMap.computeIfAbsent(userGroup.parentId) { mutableListOf() }
                .add(Node(userGroup))
        }
    }

    // Builds the tree from the root node
    private fun buildTree(
        node: Node,
        parentIdToChildListMap: HashMap<String?, MutableList<Node>>
    ) {
        parentIdToChildListMap[node.userGroup.id]?.forEach { child ->
            node.addChild(child)
            buildTree(child, parentIdToChildListMap)
        }
    }

    private fun calculatePermissions(
        parentIdToChildListMap: HashMap<String?, MutableList<Node>>
    ): Map<UserLogin, List<InternalPermissionQueryDto>> {
        val userPermissions = mutableMapOf<UserLogin, List<InternalPermissionQueryDto>>()
        // For each root node build a tree and calculate the permissions for each user
        val root = null
        parentIdToChildListMap[root]?.forEach { rootNode ->
            buildTree(rootNode, parentIdToChildListMap)
            calculatePermissions(rootNode, mutableListOf(), userPermissions)
        }

        return userPermissions
    }

    // Calculates all the permissions for each user by traversing the tree and adding the permissions to the userPermissions map
    private fun calculatePermissions(
        node: Node,
        permissions: MutableList<Permission>,
        userPermissions: MutableMap<UserLogin, List<InternalPermissionQueryDto>>
    ) {
        val userGroup = node.userGroup
        // Adds the current Node's permissions to the permissions list
        permissions.addAll(userGroup.permissionsList)

        // If the current Node has no children, add the permissions to the userPermissions map
        if (!node.hasChildren()) {
            // If there is no loginName, it means it is a group and we don't want to add it to the userPermissions map
            if (!userGroup.loginName.isNullOrEmpty()) {
                userPermissions[userGroup.loginName!!] = permissions.map { permission ->
                    InternalPermissionQueryDto(
                        userGroup.loginName!!,
                        permission.id,
                        permission.groupVisibility,
                        permission.virtualNode,
                        permission.permissionString,
                        permission.permissionType
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
        for (count in 1..userGroup.permissionsList.size) {
            permissions.removeLast()
        }
    }

    private data class Node(val userGroup: InternalUserGroup) {
        val children: MutableList<Node> = mutableListOf()

        fun addChild(node: Node) {
            children.add(node)
        }

        fun hasChildren(): Boolean =
            children.size >= 1
    }
}
