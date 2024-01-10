package net.corda.libs.permissions.cache.impl

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.ChangeDetails
import net.corda.data.permissions.Group
import net.corda.data.permissions.Permission
import net.corda.data.permissions.PermissionAssociation
import net.corda.data.permissions.PermissionType
import net.corda.data.permissions.Role
import net.corda.data.permissions.RoleAssociation
import net.corda.data.permissions.User
import net.corda.data.permissions.summary.PermissionSummary
import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.permissions.cache.exception.PermissionCacheException
import net.corda.libs.permissions.management.cache.impl.PermissionManagementCacheImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PermissionManagementCacheImplTest {

    private val userData = ConcurrentHashMap<String, User>()
    private val groupData = ConcurrentHashMap<String, Group>()
    private val roleData = ConcurrentHashMap<String, Role>()
    private val permissionData = ConcurrentHashMap<String, Permission>()
    private val permissionSummaryData = ConcurrentHashMap<String, UserPermissionSummary>()
    private val permissionCache = PermissionManagementCacheImpl(userData, groupData, roleData, permissionData)

    private val user1 = User("id1", 1, ChangeDetails(Instant.now()), "user-login1", "full name", true,
        "hashedPassword", "saltValue", null, false, null, null, emptyList())
    private val user2 = User("id2", 1, ChangeDetails(Instant.now()), "user-login2", "full name2", true,
        "hashedPassword", "saltValue", null, false, null, null, emptyList())

    private val group1 = Group("grpId1", 0, ChangeDetails(Instant.now()), "group1", null,
        emptyList(), emptyList())
    private val group2 = Group("grpId2", 0, ChangeDetails(Instant.now()), "group1", null,
        emptyList(), listOf(RoleAssociation(ChangeDetails(Instant.now()), "role1"), RoleAssociation(ChangeDetails(Instant.now()), "role2")))

    private val permission1 = Permission("perm1", 0, ChangeDetails(Instant.now()), "virtNode1",
        PermissionType.ALLOW, "*", group1.id)
    private val permission2 = Permission("perm2", 0, ChangeDetails(Instant.now()), "virtNode2",
        PermissionType.DENY, "*", group1.id)

    private val role1 = Role("role1", 0, ChangeDetails(Instant.now()), "role1Name", group1.id,
        listOf(PermissionAssociation(ChangeDetails(Instant.now()), permission1.id)))
    private val role2 = Role("role2", 0, ChangeDetails(Instant.now()), "role2Name", group1.id,
        listOf(PermissionAssociation(ChangeDetails(Instant.now()), permission2.id)))

    private val permissionSummary1 = UserPermissionSummary(
        "userlogin1",
        true,
        listOf(PermissionSummary("id1", null, null, "", PermissionType.ALLOW)),
        Instant.now()
    )
    private val permissionSummary2 = UserPermissionSummary(
        "userlogin2",
        true,
        listOf(PermissionSummary("id2", null, null, "", PermissionType.DENY)),
        Instant.now()
    )

    @BeforeEach
    fun setUp() {
        userData["user-login-1"] = user1
        userData["user-login-2"] = user2
        groupData[group1.id] = group1
        groupData[group2.id] = group2
        roleData[role1.id] = role1
        roleData[role2.id] = role2
        permissionData[permission1.id] = permission1
        permissionData[permission2.id] = permission2
        permissionCache.start()
        permissionSummaryData["userLogin1"] = permissionSummary1
        permissionSummaryData["userLogin2"] = permissionSummary2
    }

    @Test
    fun `stopped permission cache prevents calling user functions`() {
        permissionCache.stop()
        assertThrows(PermissionCacheException::class.java) {
            permissionCache.getUser("id")
        }
        assertThrows(PermissionCacheException::class.java) {
            permissionCache.getRole("id")
        }
        assertThrows(PermissionCacheException::class.java) {
            permissionCache.getGroup("id")
        }
        assertThrows(PermissionCacheException::class.java) {
            permissionCache.users
        }
        assertThrows(PermissionCacheException::class.java) {
            permissionCache.groups
        }
        assertThrows(PermissionCacheException::class.java) {
            permissionCache.roles
        }
    }

    @Test
    fun getUser() {
        assertEquals(user1, permissionCache.getUser("user-login-1"), "GetUser did not return the expected user.")
    }

    @Test
    fun getGroup() {
        assertEquals(group1, permissionCache.getGroup(group1.id), "GetGroup did not return the expected group.")
    }

    @Test
    fun getRole() {
        assertEquals(role1, permissionCache.getRole(role1.id), "GetRole did not return the expected role.")
    }

    @Test
    fun getUsers() {
        val userMap = permissionCache.users
        val userIds = userMap.keys
        val users = userMap.values
        assertEquals(2, userMap.size, "GetUsers should return all users in the map.")
        assertTrue(userIds.containsAll(listOf("user-login-1", "user-login-2")), "GetUsers result should contain user loginNames as keys.")
        assertTrue(users.containsAll(listOf(user1, user2)), "GetUsers result should contain expected users in the map.")
    }

    @Test
    fun getGroups() {
        val groupMap = permissionCache.groups
        val groupIds = groupMap.keys
        val groups = groupMap.values
        assertEquals(2, groupMap.size, "GetGroups should return all groups in the map.")
        assertTrue(groupIds.containsAll(listOf(group1.id, group2.id)), "GetGroups result should contain group ids as keys.")
        assertTrue(groups.containsAll(listOf(group1, group2)), "GetGroups result should contain expected groups in the map.")
    }

    @Test
    fun getRoles() {
        val rolesMap = permissionCache.roles
        val roleIds = rolesMap.keys
        val roles = rolesMap.values
        assertEquals(2, rolesMap.size, "GetRoles should return all roles in the map.")
        assertTrue(roleIds.containsAll(listOf(role1.id, role2.id)), "GetRoles result should contain role IDs as keys.")
        assertTrue(roles.containsAll(listOf(role1, role2)), "GetRoles result should contain expected roles in the map.")
    }

    @Test
    fun getPermissions() {
        val permissionsMap = permissionCache.permissions
        val permissionIds = permissionsMap.keys
        val permissions = permissionsMap.values
        assertEquals(2, permissionsMap.size, "GetPermissions should return all permissions in the map.")
        assertTrue(permissionIds.containsAll(listOf(permission1.id, permission2.id)),
            "GetPermissions result should contain permission IDs as keys.")
        assertTrue(permissions.containsAll(listOf(permission1, permission2)),
            "GetPermissions result should contain expected permissions in the map.")
    }
}