package net.corda.applications.workers.rest

import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import java.util.concurrent.Executors
import net.corda.applications.workers.rest.http.TestToolkitProperty
import net.corda.applications.workers.rest.http.SkipWhenRestEndpointUnavailable
import net.corda.applications.workers.rest.utils.AdminPasswordUtil.adminPassword
import net.corda.applications.workers.rest.utils.AdminPasswordUtil.adminUser
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * These tests make assertions about permission summaries utilizing the `getPermissionSummary` API.
 */
@SkipWhenRestEndpointUnavailable
class PermissionSummaryConcurrentE2eTest {

    companion object {
        private val testToolkit by TestToolkitProperty()
        private val concurrentTestToolkit by TestToolkitProperty()
        private val adminTestHelper = RbacE2eClientRequestHelper(testToolkit, adminUser, adminPassword)
        private val concurrentAdminTestHelper = RbacE2eClientRequestHelper(concurrentTestToolkit, adminUser, adminPassword)
    }

    @Test
    fun `permission summary eventually consistent`() {
        val newUser1: String = testToolkit.uniqueName
        val newUser2: String = testToolkit.uniqueName
        val newUserPassword: String = testToolkit.uniqueName

        val passwordExpiry = Instant.now().plus(1, DAYS).truncatedTo(DAYS)
        adminTestHelper.createUser(newUser1, newUserPassword, passwordExpiry)
        adminTestHelper.createUser(newUser2, newUserPassword, passwordExpiry)

        val roleId1 = adminTestHelper.createRole(testToolkit.uniqueName)
        val roleId2 = adminTestHelper.createRole(testToolkit.uniqueName)

        adminTestHelper.addRoleToUser(newUser1, roleId1)
        adminTestHelper.addRoleToUser(newUser1, roleId2)
        adminTestHelper.addRoleToUser(newUser2, roleId1)
        adminTestHelper.addRoleToUser(newUser2, roleId2)

        with(adminTestHelper.getPermissionSummary(newUser1)) {
            assertEquals(0, this.permissions.size, "Permission summary should be empty before the role is assigned")
        }
        with(adminTestHelper.getPermissionSummary(newUser2)) {
            assertEquals(0, this.permissions.size, "Permission summary should be empty before the role is assigned")
        }

        val permissionsCount = 50
        val client = testToolkit.httpClientFor(PermissionEndpoint::class.java, adminUser, adminPassword)
        val proxy = client.start().proxy
        val permissionIdsAllow = (1..permissionsCount).map {
            proxy.createPermission(PermissionType.ALLOW, "$it-allow-${testToolkit.uniqueName}", false)
        }
        val permissionIdsDeny = (1..permissionsCount).map {
            proxy.createPermission(PermissionType.DENY, "$it-deny-${testToolkit.uniqueName}", false)
        }
        val allPermissionIds = permissionIdsAllow + permissionIdsDeny

        val executorService = Executors.newFixedThreadPool(2)
        val role1PopulationFuture = executorService.submit {
            adminTestHelper.addPermissionsToRole(roleId1, *permissionIdsAllow.toTypedArray())
        }
        val role2PopulationFuture = executorService.submit {
            concurrentAdminTestHelper.addPermissionsToRole(roleId2, *permissionIdsDeny.toTypedArray())
        }

        role1PopulationFuture.get()
        role2PopulationFuture.get()
        executorService.shutdown()

        eventually {
            with(adminTestHelper.getPermissionSummary(newUser1)) {
                assertEquals(permissionsCount * 2, this.permissions.size)
                val discoveredPermissionIds = this.permissions.map { it.id }
                assertTrue(discoveredPermissionIds.containsAll(allPermissionIds))
            }
            with(adminTestHelper.getPermissionSummary(newUser2)) {
                assertEquals(permissionsCount * 2, this.permissions.size)
                val discoveredPermissionIds = this.permissions.map { it.id }
                assertTrue(discoveredPermissionIds.containsAll(allPermissionIds))
            }
        }
    }
}