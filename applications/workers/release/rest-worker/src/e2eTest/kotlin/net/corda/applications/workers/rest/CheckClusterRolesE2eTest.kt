package net.corda.applications.workers.rest

import net.corda.applications.workers.rest.http.SkipWhenRestEndpointUnavailable
import net.corda.applications.workers.rest.utils.E2eClusterBConfig
import net.corda.applications.workers.rest.utils.E2eClusterFactory
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionResponseType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.RoleResponseType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

/**
 * Confirms that certain roles been pre-created at cluster bootstrap time
 */
@SkipWhenRestEndpointUnavailable
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CheckClusterRolesE2eTest {

    private val cordaCluster = E2eClusterFactory.getE2eCluster(E2eClusterBConfig)

    @Test
    fun `test UserAdminRole content`() {

        fun wildcardMatch(permissionString: String, permissionRequested: String): Boolean {
            return permissionRequested.matches(permissionString.toRegex(RegexOption.IGNORE_CASE))
        }

        cordaCluster.clusterHttpClientFor(RoleEndpoint::class.java).use { roleClient ->
            val roleProxy = roleClient.start().proxy

            val allRoles = roleProxy.getRoles()

            val mayBeUserAdminRole: RoleResponseType? = allRoles.firstOrNull { it.roleName == "UserAdminRole" }
            val userAdminRole = requireNotNull(mayBeUserAdminRole) { "Available roles: $allRoles" }

            cordaCluster.clusterHttpClientFor(PermissionEndpoint::class.java).use { permClient ->
                val permProxy = permClient.start().proxy
                val permissions: List<PermissionResponseType> =
                    userAdminRole.permissions.map { permProxy.getPermission(it.id) }
                assertThat(permissions.size).withFailMessage("Permissions: $permissions").isEqualTo(14)
                assertThat(permissions.map { it.permissionString }).contains("POST:/api/v1/user")
                assertThat(permissions.filter { it.permissionType == PermissionType.ALLOW }).anyMatch {
                    wildcardMatch(
                        it.permissionString,
                        "GET:/api/v1/user?loginname=RandomUser"
                    )
                }
            }
        }
    }

    @Test
    fun `test VNodeCreatorRole content`() {
        cordaCluster.clusterHttpClientFor(RoleEndpoint::class.java).use { roleClient ->
            val roleProxy = roleClient.start().proxy

            val allRoles = roleProxy.getRoles()

            val mayBeRequiredRole: RoleResponseType? = allRoles.firstOrNull { it.roleName == "VNodeCreatorRole" }
            val requiredRole = requireNotNull(mayBeRequiredRole) { "Available roles: $allRoles" }

            cordaCluster.clusterHttpClientFor(PermissionEndpoint::class.java).use { permClient ->
                val permProxy = permClient.start().proxy
                val permissions = requiredRole.permissions.map { permProxy.getPermission(it.id) }
                assertThat(permissions.size).withFailMessage("Permissions: $permissions").isEqualTo(8)
                assertThat(permissions.map { it.permissionString }).contains("POST:/api/v1/virtualnode")
            }
        }
    }

    @Test
    fun `test CordaDeveloperRole content`() {
        cordaCluster.clusterHttpClientFor(RoleEndpoint::class.java).use { roleClient ->
            val roleProxy = roleClient.start().proxy

            val allRoles = roleProxy.getRoles()

            val mayBeRequiredRole: RoleResponseType? = allRoles.firstOrNull { it.roleName == "CordaDeveloperRole" }
            val requiredRole = requireNotNull(mayBeRequiredRole) { "Available roles: $allRoles" }

            cordaCluster.clusterHttpClientFor(PermissionEndpoint::class.java).use { permClient ->
                val permProxy = permClient.start().proxy
                val permissions = requiredRole.permissions.map { permProxy.getPermission(it.id) }
                assertThat(permissions.size).withFailMessage("Permissions: $permissions").isEqualTo(2)
                assertThat(permissions.map { it.permissionString }).contains("POST:/api/v1/maintenance/virtualnode/forcecpiupload")
            }
        }
    }
}