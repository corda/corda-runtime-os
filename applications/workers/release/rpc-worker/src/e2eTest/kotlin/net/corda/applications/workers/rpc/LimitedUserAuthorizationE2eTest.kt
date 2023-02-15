package net.corda.applications.workers.rpc

import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.applications.workers.rpc.http.SkipWhenRestEndpointUnavailable
import net.corda.applications.workers.rpc.utils.AdminPasswordUtil.adminPassword
import net.corda.applications.workers.rpc.utils.AdminPasswordUtil.adminUser
import net.corda.httprpc.client.exceptions.PermissionException
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * This test uses the admin user to create a new user with limited operations authorized.
 *
 * The new user:
 * - is not allowed to perform addPermission or addRole operations.
 * - is allowed to perform user operations (except `addRole`).
 * - is allowed to perform read-only get operations.
 * - no explicit permission is set for creation of a role, therefore this should not be permitted.
 */
@SkipWhenRestEndpointUnavailable
class LimitedUserAuthorizationE2eTest {

    companion object {
        private val testToolkit by TestToolkitProperty()
        private val adminTestHelper = RbacE2eClientRequestHelper(testToolkit, adminUser, adminPassword)
        private lateinit var limitedUserTestHelper: RbacE2eClientRequestHelper
        private var limitedUserLogin: String = testToolkit.uniqueName
        private var limitedUserPassword: String = testToolkit.uniqueName
        private lateinit var creatorRoleId: String
        private lateinit var allowUserOperationsPermId: String

        @JvmStatic
        @BeforeAll
        fun setup() {
            val passwordExpiry = Instant.now().plus(1, DAYS).truncatedTo(DAYS)
            val creatorRoleName = "creator-" + testToolkit.uniqueName
            val readerRoleName = "reader-" + testToolkit.uniqueName

            adminTestHelper.createUser(limitedUserLogin, limitedUserPassword, passwordExpiry)

            // explicit ALLOW on all user operations should get overridden by explicit DENY on addRole and remove.*
            allowUserOperationsPermId = adminTestHelper.createPermission(PermissionType.ALLOW, ".*user")
            val denyAddOperationsPermId = adminTestHelper.createPermission(PermissionType.DENY, "PUT:.*(addPermission|addRole|remove.*)")
            val allowReadOperationsPermId = adminTestHelper.createPermission(PermissionType.ALLOW, "GET:.*")

            creatorRoleId = adminTestHelper.createRole(creatorRoleName)
            val readerRoleId = adminTestHelper.createRole(readerRoleName)

            adminTestHelper.addPermissionsToRole(creatorRoleId, allowUserOperationsPermId, denyAddOperationsPermId)
            adminTestHelper.addPermissionsToRole(readerRoleId, allowReadOperationsPermId)

            adminTestHelper.addRoleToUser(limitedUserLogin, creatorRoleId)
            adminTestHelper.addRoleToUser(limitedUserLogin, readerRoleId)

            limitedUserTestHelper = RbacE2eClientRequestHelper(testToolkit, limitedUserLogin, limitedUserPassword)
        }
    }

    @Test
    fun `verify limited user can exercise ALLOW permission on user operations to create a user`() {
        val newUser = testToolkit.uniqueName
        val newPass = testToolkit.uniqueName
        val passwordExpiry = Instant.now().plus(1, DAYS).truncatedTo(DAYS)

        limitedUserTestHelper.createUser(newUser, newPass, passwordExpiry)
    }

    @Test
    fun `verify limited user cannot create a role which has no explicit ALLOW or DENY permission set`() {
        Assertions.assertThatThrownBy {
            limitedUserTestHelper.createRole("this-rolename-should-not-be-persisted")
        }.isInstanceOf(PermissionException::class.java)
            .hasMessageContaining("User not authorized.")
    }

    @Test
    fun `verify limited user cannot add role to user with explicit DENY on addRole despite explicit ALLOW set for user operations`() {
        Assertions.assertThatThrownBy {
            limitedUserTestHelper.addRoleToUser(limitedUserLogin, creatorRoleId)
        }.isInstanceOf(PermissionException::class.java)
            .hasMessageContaining("User not authorized.")
    }

    @Test
    fun `verify limited user cannot remove role from user with explicit DENY on remove operations`() {
        Assertions.assertThatThrownBy {
            limitedUserTestHelper.removeRoleFromUser(limitedUserLogin, creatorRoleId)
        }.isInstanceOf(PermissionException::class.java)
            .hasMessageContaining("User not authorized.")
    }

    @Test
    fun `verify limited user can perform read operations on User, Role and Permission`() {
        val user = limitedUserTestHelper.getUser(limitedUserLogin)
        val role = limitedUserTestHelper.getRole(user.roleAssociations.first().roleId)
        limitedUserTestHelper.getPermission(role.permissions.first().id)
    }

    @Test
    fun `verify new user created by limited user will have no permissions`() {
        val newUser = testToolkit.uniqueName
        val newPass = testToolkit.uniqueName
        val passwordExpiry = Instant.now().plus(1, DAYS).truncatedTo(DAYS)

        limitedUserTestHelper.createUser(newUser, newPass, passwordExpiry)

        val newUserTestHelper = RbacE2eClientRequestHelper(testToolkit, newUser, newPass)
        // now with new user try to create a permission
        Assertions.assertThatThrownBy {
            newUserTestHelper.getPermission(allowUserOperationsPermId)
        }.isInstanceOf(PermissionException::class.java)
            .hasMessageContaining("User not authorized.")
    }
}