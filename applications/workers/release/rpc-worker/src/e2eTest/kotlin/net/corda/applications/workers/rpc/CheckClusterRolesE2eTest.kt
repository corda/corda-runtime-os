package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.http.SkipWhenRpcEndpointUnavailable
import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.RoleResponseType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

/**
 * Confirms that certain roles been pre-created at cluster bootstrap time
 */
@SkipWhenRpcEndpointUnavailable
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CheckClusterRolesE2eTest {

    private val testToolkit by TestToolkitProperty()

    @Test
    fun `test UserAdminRole content`() {
        testToolkit.httpClientFor(RoleEndpoint::class.java).use { roleClient ->
            val roleProxy = roleClient.start().proxy

            val allRoles = roleProxy.getRoles()

            val mayBeUserAdminRole: RoleResponseType? = allRoles.firstOrNull { it.roleName == "UserAdminRole" }
            val userAdminRole = requireNotNull(mayBeUserAdminRole) { "Available roles: $allRoles" }

            testToolkit.httpClientFor(PermissionEndpoint::class.java).use { permClient ->
                val permProxy = permClient.start().proxy
                val permissions = userAdminRole.permissions.map { permProxy.getPermission(it.id) }
                assertThat(permissions.size).withFailMessage("Permissions: $permissions").isEqualTo(13)
                assertThat(permissions.map { it.permissionString }).contains("POST:/api/v1/user")
            }
        }
    }

    @Test
    fun `test VNodeCreatorRole content`() {
        testToolkit.httpClientFor(RoleEndpoint::class.java).use { roleClient ->
            val roleProxy = roleClient.start().proxy

            val allRoles = roleProxy.getRoles()

            val mayBeRequiredRole: RoleResponseType? = allRoles.firstOrNull { it.roleName == "VNodeCreatorRole" }
            val requiredRole = requireNotNull(mayBeRequiredRole) { "Available roles: $allRoles" }

            testToolkit.httpClientFor(PermissionEndpoint::class.java).use { permClient ->
                val permProxy = permClient.start().proxy
                val permissions = requiredRole.permissions.map { permProxy.getPermission(it.id) }
                assertThat(permissions.size).withFailMessage("Permissions: $permissions").isEqualTo(6)
                assertThat(permissions.map { it.permissionString }).contains("POST:/api/v1/virtualnode")
            }
        }
    }

    @Test
    fun `test CordaDeveloperRole content`() {
        testToolkit.httpClientFor(RoleEndpoint::class.java).use { roleClient ->
            val roleProxy = roleClient.start().proxy

            val allRoles = roleProxy.getRoles()

            val mayBeRequiredRole: RoleResponseType? = allRoles.firstOrNull { it.roleName == "CordaDeveloperRole" }
            val requiredRole = requireNotNull(mayBeRequiredRole) { "Available roles: $allRoles" }

            testToolkit.httpClientFor(PermissionEndpoint::class.java).use { permClient ->
                val permProxy = permClient.start().proxy
                val permissions = requiredRole.permissions.map { permProxy.getPermission(it.id) }
                assertThat(permissions.size).withFailMessage("Permissions: $permissions").isEqualTo(1)
                assertThat(permissions.map { it.permissionString }).contains("POST:/api/v1/maintenance/virtualnode")
            }
        }
    }
}