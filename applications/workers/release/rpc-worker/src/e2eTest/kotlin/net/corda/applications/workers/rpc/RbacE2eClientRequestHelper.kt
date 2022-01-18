package net.corda.applications.workers.rpc

import java.time.Instant
import net.corda.applications.workers.rpc.http.TestToolkit
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.endpoints.v1.role.types.RoleResponseType
import net.corda.libs.permissions.endpoints.v1.schema.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.schema.CreatePermissionType.PermissionType
import net.corda.libs.permissions.endpoints.v1.schema.PermissionResponseType
import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
import net.corda.test.util.eventually
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.assertDoesNotThrow

class RbacE2eClientRequestHelper(
    private val testToolkit: TestToolkit,
    private val requestUserName: String,
    private val requestUserPassword: String,
) {
    fun createUser(newUserName: String, newUserPassword: String, newUserPasswordExpiry: Instant) {
        val client = testToolkit.httpClientFor(UserEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy

        val createUserType = CreateUserType(newUserName, newUserName, true, newUserPassword, newUserPasswordExpiry, null)
        with(proxy.createUser(createUserType)) {
            SoftAssertions.assertSoftly {
                it.assertThat(loginName).isEqualTo(newUserName)
                it.assertThat(this.passwordExpiry).isEqualTo(newUserPasswordExpiry)
            }
        }
        verifyUserCreationPersisted(proxy, newUserName, newUserPasswordExpiry)
    }

    private fun verifyUserCreationPersisted(proxy: UserEndpoint, userName: String, passwordExpirySet: Instant) {
        eventually {
            assertDoesNotThrow {
                with(proxy.getUser(userName)) {
                    SoftAssertions.assertSoftly {
                        it.assertThat(loginName).isEqualTo(userName)
                        it.assertThat(passwordExpiry).isEqualTo(passwordExpirySet)
                    }
                }
            }
        }
    }

    fun createRole(roleName: String): String {
        val client = testToolkit.httpClientFor(RoleEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy

        val createRoleType = CreateRoleType(roleName, null)
        val roleId = with(proxy.createRole(createRoleType)) {
            SoftAssertions.assertSoftly {
                it.assertThat(this.roleName).isEqualTo(roleName)
            }
            this.id
        }
        verifyRoleCreationPersisted(proxy, roleId, roleName)
        return roleId
    }

    private fun verifyRoleCreationPersisted(proxy: RoleEndpoint, roleId: String, roleName: String) {
        eventually {
            assertDoesNotThrow {
                with(proxy.getRole(roleId)) {
                    SoftAssertions.assertSoftly {
                        it.assertThat(this.id).isEqualTo(roleId)
                        it.assertThat(this.roleName).isEqualTo(roleName)
                    }
                }
            }
        }
    }

    fun createPermission(permissionType: PermissionType, permissionString: String): String {
        val client = testToolkit.httpClientFor(PermissionEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy

        val type = CreatePermissionType(permissionType, permissionString, null, null)
        val permissionId = with(proxy.createPermission(type)) {
            SoftAssertions.assertSoftly {
                it.assertThat(this.permissionType).isEqualTo(permissionType)
                it.assertThat(this.permissionString).isEqualTo(permissionString)
            }
            this.id
        }
        verifyPermissionCreationPersisted(proxy, permissionId, permissionType, permissionString)
        return permissionId
    }

    private fun verifyPermissionCreationPersisted(
        proxy: PermissionEndpoint,
        permissionId: String,
        permissionType: PermissionType,
        permissionString: String
    ) {
        eventually {
            assertDoesNotThrow {
                with(proxy.getPermission(permissionId)) {
                    SoftAssertions.assertSoftly {
                        it.assertThat(this.id).isEqualTo(permissionId)
                        it.assertThat(this.permissionType).isEqualTo(permissionType)
                        it.assertThat(this.permissionString).isEqualTo(permissionString)
                    }
                }
            }
        }
    }

    fun addPermissionsToRole(roleId: String, vararg permissionIds: String) {
        val client = testToolkit.httpClientFor(RoleEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy
        for(permissionId in permissionIds) {
            with(proxy.addPermission(roleId, permissionId)) {
                SoftAssertions.assertSoftly {
                    it.assertThat(this.permissions.map { perm -> perm.id }).contains(permissionId)
                }
            }
        }
    }

    fun addRoleToUser(userName: String, roleId: String) {
        val client = testToolkit.httpClientFor(UserEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy
        with(proxy.addRole(userName, roleId)) {
            SoftAssertions.assertSoftly {
                it.assertThat(this.roleAssociations.map { role -> role.roleId }).contains(roleId)
            }
        }
    }

    fun removeRoleFromUser(userName: String, roleId: String) {
        val client = testToolkit.httpClientFor(UserEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy
        with(proxy.removeRole(userName, roleId)) {
            SoftAssertions.assertSoftly {
                it.assertThat(this.roleAssociations.map { role -> role.roleId }).contains(roleId)
            }
        }
    }

    fun getUser(userLogin: String): UserResponseType {
        val client = testToolkit.httpClientFor(UserEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy
        return with(proxy.getUser(userLogin)) {
            SoftAssertions.assertSoftly {
                it.assertThat(this.loginName).isEqualTo(userLogin)
            }
            this
        }
    }

    fun getRole(roleId: String): RoleResponseType {
        val client = testToolkit.httpClientFor(RoleEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy
        return with(proxy.getRole(roleId)) {
            SoftAssertions.assertSoftly {
                it.assertThat(this.id).isEqualTo(roleId)
            }
            this
        }
    }

    fun getPermission(permissionId: String): PermissionResponseType {
        val client = testToolkit.httpClientFor(PermissionEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy
        return with(proxy.getPermission(permissionId)) {
            SoftAssertions.assertSoftly {
                it.assertThat(this.id).isEqualTo(permissionId)
            }
            this
        }
    }
}