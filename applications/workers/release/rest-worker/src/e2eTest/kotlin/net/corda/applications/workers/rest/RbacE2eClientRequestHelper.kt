package net.corda.applications.workers.rest

import java.time.Instant
import net.corda.applications.workers.rest.utils.E2eCluster
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.permission.types.BulkCreatePermissionsRequestType
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionResponseType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.endpoints.v1.role.types.RoleResponseType
import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.endpoints.v1.user.types.UserPermissionSummaryResponseType
import net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
import net.corda.test.util.eventually
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.assertDoesNotThrow

class RbacE2eClientRequestHelper(
    private val cluster: E2eCluster,
    private val requestUserName: String,
    private val requestUserPassword: String,
) {
    fun createUser(newUserName: String, newUserPassword: String, newUserPasswordExpiry: Instant) {
        val client = cluster.clusterHttpClientFor(UserEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy

        val createUserType = CreateUserType(newUserName, newUserName, true, newUserPassword, newUserPasswordExpiry, null)
        with(proxy.createUser(createUserType)) {
            SoftAssertions.assertSoftly {
                it.assertThat(this.responseCode.statusCode).isEqualTo(201)
                it.assertThat(this.responseBody).isNotNull
                it.assertThat(this.responseBody.loginName).isEqualToIgnoringCase(newUserName)
                it.assertThat(this.responseBody.passwordExpiry).isEqualTo(newUserPasswordExpiry)
            }
        }
        verifyUserCreationPersisted(proxy, newUserName, newUserPasswordExpiry)
    }

    private fun verifyUserCreationPersisted(proxy: UserEndpoint, userName: String, passwordExpirySet: Instant) {
        eventually {
            assertDoesNotThrow {
                with(proxy.getUser(userName)) {
                    SoftAssertions.assertSoftly {
                        it.assertThat(loginName).isEqualToIgnoringCase(userName)
                        it.assertThat(passwordExpiry).isEqualTo(passwordExpirySet)
                    }
                }
            }
        }
    }

    fun createRole(roleName: String): String {
        val client = cluster.clusterHttpClientFor(RoleEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy

        val createRoleType = CreateRoleType(roleName, null)
        val roleId = with(proxy.createRole(createRoleType)) {
            SoftAssertions.assertSoftly {
                it.assertThat(this.responseCode.statusCode).isEqualTo(201)
                it.assertThat(this.responseBody).isNotNull
                it.assertThat(this.responseBody.roleName).isEqualTo(roleName)
            }
            this.responseBody.id
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

    fun createPermission(permissionType: PermissionType, permissionString: String, verify: Boolean = true): String {
        val client = cluster.clusterHttpClientFor(PermissionEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy

        return proxy.createPermission(permissionType, permissionString, verify)
    }

    fun addPermissionsToRole(roleId: String, vararg permissionIds: String) {
        val client = cluster.clusterHttpClientFor(RoleEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy
        for(permissionId in permissionIds) {
            with(proxy.addPermission(roleId, permissionId)) {
                SoftAssertions.assertSoftly {
                    it.assertThat(this.responseCode.statusCode).isEqualTo(200)
                    it.assertThat(this.responseBody).isNotNull
                    it.assertThat(this.responseBody.permissions.map { perm -> perm.id }).contains(permissionId)
                }
            }
        }
    }

    fun addRoleToUser(userName: String, roleId: String) {
        val client = cluster.clusterHttpClientFor(UserEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy
        with(proxy.addRole(userName, roleId)) {
            SoftAssertions.assertSoftly {
                it.assertThat(this.responseCode.statusCode).isEqualTo(200)
                it.assertThat(this.responseBody).isNotNull
                it.assertThat(this.responseBody.roleAssociations.map { role -> role.roleId }).contains(roleId)
            }
        }
    }

    fun removeRoleFromUser(userName: String, roleId: String) {
        val client = cluster.clusterHttpClientFor(UserEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy
        with(proxy.removeRole(userName, roleId)) {
            SoftAssertions.assertSoftly {
                it.assertThat(this.responseCode.statusCode).isEqualTo(200)
                it.assertThat(this.responseBody).isNotNull
                it.assertThat(this.responseBody.roleAssociations.map { role -> role.roleId }).contains(roleId)
            }
        }
    }

    fun getUser(userLogin: String): UserResponseType {
        val client = cluster.clusterHttpClientFor(UserEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy
        return with(proxy.getUser(userLogin)) {
            SoftAssertions.assertSoftly {
                it.assertThat(this.loginName).isEqualToIgnoringCase(userLogin)
            }
            this
        }
    }

    fun getRole(roleId: String): RoleResponseType {
        val client = cluster.clusterHttpClientFor(RoleEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy
        return with(proxy.getRole(roleId)) {
            SoftAssertions.assertSoftly {
                it.assertThat(this.id).isEqualTo(roleId)
            }
            this
        }
    }

    fun getPermission(permissionId: String): PermissionResponseType {
        val client = cluster.clusterHttpClientFor(PermissionEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy
        return with(proxy.getPermission(permissionId)) {
            SoftAssertions.assertSoftly {
                it.assertThat(this.id).isEqualTo(permissionId)
            }
            this
        }
    }

    fun getPermissionSummary(userLogin: String): UserPermissionSummaryResponseType {
        val client = cluster.clusterHttpClientFor(UserEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy
        return with(proxy.getPermissionSummary(userLogin)) {
            SoftAssertions.assertSoftly {
                it.assertThat(this.loginName).isEqualToIgnoringCase(userLogin)
            }
            this
        }
    }

    fun createPermissionsAndAssignToRoles(
        permissionsToCreate: Set<Pair<PermissionType, String>>,
        roleIds: Set<String>
    ): Set<String> {
        val client = cluster.clusterHttpClientFor(PermissionEndpoint::class.java, requestUserName, requestUserPassword)
        val proxy = client.start().proxy

        val permissionsToCreateDto: Set<CreatePermissionType> =
            permissionsToCreate.map { CreatePermissionType(it.first, it.second, null, null) }.toSet()

        val responseEntity =
            proxy.createAndAssignPermissions(BulkCreatePermissionsRequestType(permissionsToCreateDto, roleIds))

        SoftAssertions.assertSoftly {
            it.assertThat(responseEntity.responseCode.statusCode).isEqualTo(201)
            it.assertThat(responseEntity.responseBody.permissionIds).isNotEmpty
        }
        return responseEntity.responseBody.permissionIds
    }
}

fun PermissionEndpoint.createPermission(
    permissionType: PermissionType,
    permissionString: String,
    verify: Boolean
): String {
    val type = CreatePermissionType(permissionType, permissionString, null, null)
    val permissionId = with(createPermission(type)) {
        SoftAssertions.assertSoftly {
            it.assertThat(this.responseCode.statusCode).isEqualTo(201)
            it.assertThat(this.responseBody).isNotNull
            it.assertThat(this.responseBody.permissionType).isEqualTo(permissionType)
            it.assertThat(this.responseBody.permissionString).isEqualTo(permissionString)
        }
        this.responseBody.id
    }
    if (verify) {
        verifyPermissionCreationPersisted(this, permissionId, permissionType, permissionString)
    }
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