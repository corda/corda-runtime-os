package net.corda.applications.workers.rest

import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import net.corda.applications.workers.rest.http.TestToolkitProperty
import net.corda.applications.workers.rest.http.SkipWhenRestEndpointUnavailable
import net.corda.applications.workers.rest.utils.AdminPasswordUtil.adminPassword
import net.corda.applications.workers.rest.utils.AdminPasswordUtil.adminUser
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * These tests make assertions about permission summaries utilizing the `getPermissionSummary` API.
 */
@SkipWhenRestEndpointUnavailable
class PermissionSummaryE2eTest {

    companion object {
        private val testToolkit by TestToolkitProperty()
        private val adminTestHelper = RbacE2eClientRequestHelper(testToolkit, adminUser, adminPassword)
        private val passwordExpiry = Instant.now().plus(1, DAYS).truncatedTo(DAYS)
    }

    @Test
    fun `permission summary added to kafka when a permission is added to a single user and a single role`() {
        val newUser: String = testToolkit.uniqueName
        val newUserPassword: String = testToolkit.uniqueName
        val permissionString: String = testToolkit.uniqueName
        val roleName: String = testToolkit.uniqueName

        adminTestHelper.createUser(newUser, newUserPassword, passwordExpiry)

        with(adminTestHelper.getPermissionSummary(newUser)) {
            assertEquals(0, this.permissions.size, "New user should have permission summary with empty list")
        }

        val permissionId = adminTestHelper.createPermission(PermissionType.ALLOW, permissionString)
        val roleId = adminTestHelper.createRole(roleName)
        adminTestHelper.addPermissionsToRole(roleId, permissionId)

        with(adminTestHelper.getPermissionSummary(newUser)) {
            assertEquals(0, this.permissions.size, "Permission summary should be empty before the role is assigned")
        }

        adminTestHelper.addRoleToUser(newUser, roleId)

        with(adminTestHelper.getPermissionSummary(newUser)) {
            assertEquals(1, this.permissions.size)
            assertEquals(permissionId, this.permissions[0].id)
            assertEquals(permissionString, this.permissions[0].permissionString)
            assertEquals(PermissionType.ALLOW, this.permissions[0].permissionType)
        }
    }

    @Test
    fun `permission summary added to kafka when multiple users are assigned a role with multiple permissions`() {
        val newUser1: String = testToolkit.uniqueName
        val newUser2: String = testToolkit.uniqueName
        val newUserPassword: String = testToolkit.uniqueName

        adminTestHelper.createUser(newUser1, newUserPassword, passwordExpiry)
        adminTestHelper.createUser(newUser2, newUserPassword, passwordExpiry)

        with(adminTestHelper.getPermissionSummary(newUser1)) {
            assertEquals(0, this.permissions.size, "New user should have permission summary with empty list")
        }
        with(adminTestHelper.getPermissionSummary(newUser2)) {
            assertEquals(0, this.permissions.size, "New user should have permission summary with empty list")
        }

        val permissionString1: String = testToolkit.uniqueName
        val permissionString2: String = testToolkit.uniqueName
        val permissionId1 = adminTestHelper.createPermission(PermissionType.DENY, permissionString1)
        val permissionId2 = adminTestHelper.createPermission(PermissionType.ALLOW, permissionString2)

        val roleName: String = testToolkit.uniqueName
        val roleId = adminTestHelper.createRole(roleName)

        adminTestHelper.addPermissionsToRole(roleId, permissionId1)

        adminTestHelper.addRoleToUser(newUser1, roleId)
        adminTestHelper.addRoleToUser(newUser2, roleId)

        with(adminTestHelper.getPermissionSummary(newUser1)) {
            assertEquals(1, this.permissions.size)
            assertEquals(permissionId1, this.permissions[0].id)
            assertEquals(permissionString1, this.permissions[0].permissionString)
            assertEquals(PermissionType.DENY, this.permissions[0].permissionType)
        }
        with(adminTestHelper.getPermissionSummary(newUser2)) {
            assertEquals(1, this.permissions.size)
            assertEquals(permissionId1, this.permissions[0].id)
            assertEquals(permissionString1, this.permissions[0].permissionString)
            assertEquals(PermissionType.DENY, this.permissions[0].permissionType)
        }

        adminTestHelper.addPermissionsToRole(roleId, permissionId2)

        with(adminTestHelper.getPermissionSummary(newUser1)) {
            assertEquals(2, this.permissions.size)
            assertEquals(permissionId1, this.permissions[0].id)
            assertEquals(permissionString1, this.permissions[0].permissionString)
            assertEquals(PermissionType.DENY, this.permissions[0].permissionType)
            assertEquals(permissionId2, this.permissions[1].id)
            assertEquals(permissionString2, this.permissions[1].permissionString)
            assertEquals(PermissionType.ALLOW, this.permissions[1].permissionType)
        }
        with(adminTestHelper.getPermissionSummary(newUser2)) {
            assertEquals(2, this.permissions.size)
            assertEquals(permissionId1, this.permissions[0].id)
            assertEquals(permissionString1, this.permissions[0].permissionString)
            assertEquals(PermissionType.DENY, this.permissions[0].permissionType)
            assertEquals(permissionId2, this.permissions[1].id)
            assertEquals(permissionString2, this.permissions[1].permissionString)
            assertEquals(PermissionType.ALLOW, this.permissions[1].permissionType)
        }
    }

    @Test
    fun `permission summary added to kafka when user is assigned multiple roles with multiple permissions`() {
        val newUser1: String = testToolkit.uniqueName
        val newUserPassword: String = testToolkit.uniqueName

        adminTestHelper.createUser(newUser1, newUserPassword, passwordExpiry)

        with(adminTestHelper.getPermissionSummary(newUser1)) {
            assertEquals(0, this.permissions.size, "New user should have permission summary with empty list")
        }

        val permissionString1: String = "aa" + testToolkit.uniqueName
        val permissionString2: String = "bb" + testToolkit.uniqueName
        val permissionString3: String = "cc" + testToolkit.uniqueName
        val permissionId1 = adminTestHelper.createPermission(PermissionType.DENY, permissionString1)
        val permissionId2 = adminTestHelper.createPermission(PermissionType.ALLOW, permissionString2)
        val permissionId3 = adminTestHelper.createPermission(PermissionType.DENY, permissionString3)

        val roleId1 = adminTestHelper.createRole(testToolkit.uniqueName)
        val roleId2 = adminTestHelper.createRole(testToolkit.uniqueName)

        adminTestHelper.addPermissionsToRole(roleId1, permissionId1)
        adminTestHelper.addPermissionsToRole(roleId1, permissionId2)
        adminTestHelper.addPermissionsToRole(roleId2, permissionId3)

        adminTestHelper.addRoleToUser(newUser1, roleId1)

        with(adminTestHelper.getPermissionSummary(newUser1)) {
            assertEquals(2, this.permissions.size)
            assertEquals(permissionId1, this.permissions[0].id)
            assertEquals(permissionString1, this.permissions[0].permissionString)
            assertEquals(PermissionType.DENY, this.permissions[0].permissionType)
            assertEquals(permissionId2, this.permissions[1].id)
            assertEquals(permissionString2, this.permissions[1].permissionString)
            assertEquals(PermissionType.ALLOW, this.permissions[1].permissionType)
        }

        adminTestHelper.addRoleToUser(newUser1, roleId2)

        with(adminTestHelper.getPermissionSummary(newUser1)) {
            assertEquals(3, this.permissions.size)
            // order guaranteed to be DENY permissions first, alphabetically ordered by permission string
            assertEquals(permissionId1, this.permissions[0].id)
            assertEquals(permissionString1, this.permissions[0].permissionString)
            assertEquals(PermissionType.DENY, this.permissions[0].permissionType)
            assertEquals(permissionId3, this.permissions[1].id)
            assertEquals(permissionString3, this.permissions[1].permissionString)
            assertEquals(PermissionType.DENY, this.permissions[1].permissionType)
            assertEquals(permissionId2, this.permissions[2].id)
            assertEquals(permissionString2, this.permissions[2].permissionString)
            assertEquals(PermissionType.ALLOW, this.permissions[2].permissionType)
        }
    }

    @Test
    fun `check permission summary when multiple users are assigned multiple roles with multiple permissions using bulk operation`() {
        val newUserPassword: String = testToolkit.uniqueName
        val users = (1..2).map { testToolkit.uniqueName }
        users.map { adminTestHelper.createUser(it, newUserPassword, passwordExpiry) }

        users.map {
            with(adminTestHelper.getPermissionSummary(it)) {
                assertEquals(0, this.permissions.size, "New user ($it) should have permission summary with empty list")
            }
        }

        val roleIds: List<String> = (1..4).map { adminTestHelper.createRole(testToolkit.uniqueName + "_role") }

        // Add roles to users
        users.forEach { user ->
            roleIds.forEach { roleId ->
                adminTestHelper.addRoleToUser(user, roleId)
            }
        }

        val permissionsToCreate = setOf(
            PermissionType.DENY to "permissionString1",
            PermissionType.ALLOW to "permissionString2",
            PermissionType.DENY to "permissionString3"
        )

        // Perform bulk operation
        val permIds = adminTestHelper.createPermissionsAndAssignToRoles(permissionsToCreate, roleIds.toSet())

        // Check the result, eventually since it takes time to propagate
        eventually(Duration.ofSeconds(60)) {
            // Check permissions summary
            users.forEach { user ->
                with(adminTestHelper.getPermissionSummary(user)) {
                    assertThat(permissionsToCreate).withFailMessage("User: $user")
                        .isEqualTo(
                            this.permissions.map { it.permissionType to it.permissionString }.toSet()
                        )

                    assertThat(permIds).withFailMessage("User: $user")
                        .isEqualTo(this.permissions.map { it.id }.toSet())

                }
            }

            // Check roles content
            roleIds.forEach { roleId ->
                with(adminTestHelper.getRole(roleId)) {
                    assertThat(this.permissions.map { it.id }.toSet()).withFailMessage("RoleId: $roleId")
                        .isEqualTo(permIds)
                }
            }
        }
    }
}