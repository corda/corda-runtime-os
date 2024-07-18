package net.corda.libs.permissions.storage.reader.util

import net.corda.permissions.query.dto.InternalPermissionQueryDto
import net.corda.permissions.query.dto.InternalPermissionWithParentGroupQueryDto
import javax.persistence.EntityManager

private typealias UserLogin = String
internal object PermissionUserUtil {
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
            JOIN RoleUserAssociation rua ON rua.user.id = u.id
            JOIN Role r ON rua.role.id = r.id
            JOIN RolePermissionAssociation rpa ON rpa.role.id = r.id
            JOIN Permission p ON rpa.permission.id = p.id
            """
    fun calculatePermissionsForUsers(em: EntityManager): Map<UserLogin, List<InternalPermissionQueryDto>> {
        // Gets a map with a key of Group ID and value of its associated list of Permissions
        val groupPermissionMap = em.createQuery(
            userGroupPermissionsQuery,
            InternalPermissionWithParentGroupQueryDto::class.java
        ).resultList.groupBy { it.id }

        // Gets a map with a key of User ID and value of its associated list of Permissions
        val userPermissionMap = em.createQuery(
            allUsersPermissionsQuery,
            InternalPermissionWithParentGroupQueryDto::class.java
        ).resultList.groupBy { it.id }

        // Generate a map that allows us to find all children given a parentId
        val parentIdToChildListMap = HashMap<String?, MutableList<Node>>()
        getParentToChildListMap(parentIdToChildListMap, groupPermissionMap)
        getParentToChildListMap(parentIdToChildListMap, userPermissionMap)

        return calculatePermissions(parentIdToChildListMap)
    }

    private fun getParentToChildListMap(
        parentIdToChildListMap: HashMap<String?, MutableList<Node>>,
        permissionMap: Map<String, List<InternalPermissionWithParentGroupQueryDto>>
    ) {
        // For each element check if the first permission's parentId exists in parentIdToChildListMap and add the Node to its value
        // otherwise create a new entry with the parentGroupId and add the Node
        permissionMap.forEach { (_, permissionsList) ->
            parentIdToChildListMap.computeIfAbsent(permissionsList.first().parentGroupId) { mutableListOf() }
                .add(Node(permissionsList))
        }
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
        permissions: MutableList<InternalPermissionWithParentGroupQueryDto>,
        userPermissions: MutableMap<UserLogin, List<InternalPermissionQueryDto>>
    ) {
        // Adds the current Node's permissions to the permissions list
        permissions.addAll(node.permissionList)

        // If the current Node has no children, add the permissions to the userPermissions map
        if (!node.hasChildren()) {
            // If there is no loginName, it means it is a group and we don't want to add it to the userPermissions map
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