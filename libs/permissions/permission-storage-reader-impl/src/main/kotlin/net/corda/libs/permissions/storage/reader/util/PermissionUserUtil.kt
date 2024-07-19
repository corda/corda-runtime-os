package net.corda.libs.permissions.storage.reader.util

import net.corda.libs.permissions.storage.reader.impl.repository.PermissionQueryDtoComparator
import net.corda.libs.permissions.storage.reader.repository.UserLogin
import net.corda.libs.permissions.storage.reader.summary.InternalUserPermissionSummary
import net.corda.permissions.query.dto.InternalPermission
import net.corda.permissions.query.dto.InternalPermissionQueryDto
import net.corda.permissions.query.dto.InternalPermissionWithParentGroupQueryDto
import net.corda.permissions.query.dto.InternalUserEnabledQueryDto
import net.corda.permissions.query.dto.InternalUserGroup
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.persistence.EntityManager

internal object PermissionUserUtil {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Query to get all the Permissions for each Group.
     * InternalPermissionWithParentGroupQueryDto.loginName is empty to signify that it is a Group.
     * We are using LEFT JOIN to get all the Groups even if they don't have any permissions and
     * p.id, p.permissionString, p.permissionType will be null if a Group exists without any permissions.
     * This is necessary to get all the relationships between all Groups as some Groups may inherit permissions from other Groups.
     */
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

    /**
     * Query to get all the Permissions for each User.
     * We are using LEFT JOIN to get all the Users even if they don't have any permissions and
     * p.id, p.permissionString, p.permissionType will be null if a User exists without any permissions.
     * This is necessary as some Users may inherit permissions from Groups.
     */
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
    fun calculatePermissionsForUsers(em: EntityManager): Map<UserLogin, InternalUserGroup> {
        val groupList = getUserGroupPermissions(em, allGroupPermissionsQuery)
        val userList = getUserGroupPermissions(em, allUsersPermissionsQuery)

        logger.debug("List of Group permissions: {}", groupList)
        logger.debug("List of User permissions {}", userList)

        // Generate a map that allows us to find all children given a parentId
        val parentIdToChildListMap = HashMap<String?, MutableList<Node>>()
        getParentToChildListMap(parentIdToChildListMap, groupList)
        getParentToChildListMap(parentIdToChildListMap, userList)

        val calculatedPermission = calculatePermissions(parentIdToChildListMap)
        logger.debug("Calculated permissions: {}", calculatedPermission)

        return calculatedPermission
    }

    fun aggregatePermissionSummariesForUsers(
        userLogins: List<InternalUserEnabledQueryDto>,
        userMap: Map<UserLogin, InternalUserGroup>,
        timeOfPermissionSummary: Instant,
    ): Map<String, InternalUserPermissionSummary> {
        val userPermissionSummaries = userLogins.associateBy({ user -> user.loginName }) { userLogin ->
            val permissionSortedSet =
                getPermissionListAsDto(userLogin.loginName, userMap).toSortedSet(PermissionQueryDtoComparator())

            InternalUserPermissionSummary(
                userLogin.loginName,
                userLogin.enabled,
                permissionSortedSet,
                timeOfPermissionSummary
            )
        }

        logger.debug("User permission summaries: {}", userPermissionSummaries)

        return userPermissionSummaries
    }

    private fun getPermissionListAsDto(
        loginName: String,
        usersMap: Map<UserLogin, InternalUserGroup>
    ): List<InternalPermissionQueryDto> {
        val permissionList = usersMap[loginName]?.permissionsList ?: emptyList()
        return getPermissionListAsDto(loginName, permissionList)
    }

    private fun getPermissionListAsDto(
        loginName: String,
        permissionList: List<InternalPermission>
    ): List<InternalPermissionQueryDto> {
        return permissionList.map { permission ->
            InternalPermissionQueryDto(
                loginName,
                permission.id,
                permission.groupVisibility,
                permission.virtualNode,
                permission.permissionString,
                permission.permissionType
            )
        }
    }

    private fun getUserGroupPermissions(
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
                // Use the first parentGroupId and loginName as they will be the same for all the permissions in the list
                permissionList.firstOrNull()?.parentGroupId,
                permissionList.firstOrNull()?.loginName,
                // Filter out any permissions that have a null permissionString, so we do not add the null permissions to the permissionList
                permissionList.filter { !it.permissionString.isNullOrEmpty() }.map { permission ->
                    InternalPermission(
                        permission.permissionId!!,
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
    ): Map<UserLogin, InternalUserGroup> {
        val userPermissions = mutableMapOf<UserLogin, InternalUserGroup>()
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
        permissions: MutableList<InternalPermission>,
        userPermissions: MutableMap<UserLogin, InternalUserGroup>
    ) {
        val userGroup = node.userGroup
        // Adds the current Node's permissions to the permissions list
        permissions.addAll(userGroup.permissionsList)

        // If the current Node is a user then add the permissions to the userPermissions map.
        // A node represents a user if it does not have children and the loginName is not null or empty.
        if (!node.hasChildren()) {
            if (!userGroup.loginName.isNullOrEmpty()) {
                userPermissions[userGroup.loginName!!] = node.userGroup.copy(permissionsList = permissions.toList())
            }
        } else {
            // Calculate the permissions for each child node recursively
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
