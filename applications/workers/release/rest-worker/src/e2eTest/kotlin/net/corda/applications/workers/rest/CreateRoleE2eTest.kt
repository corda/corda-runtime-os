package net.corda.applications.workers.rest

import net.corda.applications.workers.rest.http.SkipWhenRestEndpointUnavailable
import net.corda.applications.workers.rest.utils.AdminPasswordUtil.adminUser
import net.corda.applications.workers.rest.utils.E2eClusterBConfig
import net.corda.applications.workers.rest.utils.E2eClusterFactory
import net.corda.rest.client.exceptions.MissingRequestedResourceException
import net.corda.rest.client.exceptions.RequestErrorException
import net.corda.rest.exception.ResourceAlreadyExistsException
import net.corda.libs.permissions.common.constant.RoleKeys.DEFAULT_SYSTEM_ADMIN_ROLE
import net.corda.libs.permissions.common.constant.UserKeys.DEFAULT_ADMIN_FULL_NAME
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertDoesNotThrow

@SkipWhenRestEndpointUnavailable
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CreateRoleE2eTest {

    private val cordaCluster = E2eClusterFactory.getE2eCluster(E2eClusterBConfig)

    companion object {
        private var sharedRoleId: String? = null
    }

    @Test
    @Order(1)
    fun `test getRole and createRole HTTP APIs including validation`() {
        cordaCluster.clusterHttpClientFor(RoleEndpoint::class.java).use { client ->
            val name = cordaCluster.uniqueName
            val proxy = client.start().proxy

            // Check the role does not exist yet
            assertThatThrownBy { proxy.getRole("fakeId") }.isInstanceOf(MissingRequestedResourceException::class.java)
                .hasMessageContaining("Role 'fakeId' not found.")

            // Create role
            val createRoleType = CreateRoleType(name, null)

            val roleId = with(proxy.createRole(createRoleType)) {
                assertSoftly {
                    it.assertThat(this.responseCode.statusCode).isEqualTo(201)
                    it.assertThat(this.responseBody).isNotNull
                    it.assertThat(this.responseBody.roleName).isEqualTo(name)
                    it.assertThat(this.responseBody.version).isEqualTo(0L)
                    it.assertThat(this.responseBody.groupVisibility).isNull()
                    it.assertThat(this.responseBody.permissions).isEmpty()
                }
                this.responseBody.id
            }

            // Check the role does exist now. The distribution of Role record may take some time to complete on the
            // message bus, hence use of `eventually` along with `assertDoesNotThrow`.
            eventually {
                assertDoesNotThrow {
                    with(proxy.getRole(roleId)) {
                        assertSoftly {
                            it.assertThat(roleName).isEqualTo(name)
                            it.assertThat(version).isEqualTo(0L)
                            it.assertThat(groupVisibility).isNull()
                            it.assertThat(permissions).isEmpty()
                        }
                    }
                }
            }

            val groupName = "non-existing-group"
            val name2 = cordaCluster.uniqueName

            // Try to create a role with a group that does not exist
            assertThatThrownBy { proxy.createRole(CreateRoleType(name2, groupName)) }
                .isInstanceOf(MissingRequestedResourceException::class.java)
                .hasMessageContaining("Group '$groupName' not found.")

            sharedRoleId = roleId
        }
    }

    @Test
    @Order(2)
    fun `test add and remove permission to a role`() {
        val roleId = requireNotNull(sharedRoleId)

        // Create permission
        val permission = cordaCluster.clusterHttpClientFor(PermissionEndpoint::class.java).use { client ->
            val proxy = client.start().proxy

            // Create permission
            val setPermString = cordaCluster.uniqueName + "-PermissionString"
            val createPermType = CreatePermissionType(PermissionType.ALLOW, setPermString, null, null)

            val perm = proxy.createPermission(createPermType)

            // Check that the permission does exist now. The distribution of entity records may take some time to complete on the
            // message bus, hence use of `eventually` along with `assertDoesNotThrow`.
            eventually {
                assertDoesNotThrow {
                    assertNotNull(proxy.getPermission(perm.responseBody.id))
                }
            }
            perm
        }

        val permId = permission.responseBody.id
        val permTs = permission.responseBody.updateTimestamp

        // Test adding/removing permission to a role
        cordaCluster.clusterHttpClientFor(RoleEndpoint::class.java).use { client ->
            val proxy = client.start().proxy

            // Try to remove association when it does not exist
            assertThatThrownBy { proxy.removePermission(roleId, permId) }.isInstanceOf(RequestErrorException::class.java)
                .hasMessageContaining("Permission '$permId' is not associated with Role '$roleId'.")

            val roleWithPermission = proxy.addPermission(roleId, permId)
            assertEquals(permId, roleWithPermission.responseBody.permissions[0].id)

            eventually {
                assertDoesNotThrow {
                    val role = proxy.getRole(roleId)
                    val permissionAssociationResponseType = role.permissions[0]
                    assertEquals(permId, permissionAssociationResponseType.id)
                    assertTrue(permissionAssociationResponseType.createdTimestamp.isAfter(permTs))

                    // Validate the role is listed among all the roles
                    assertThat(proxy.getRoles()).contains(role)
                }
            }

            // Try to add same association again when it already exists
            assertThatThrownBy { proxy.addPermission(roleId, permId) }.isInstanceOf(ResourceAlreadyExistsException::class.java)
                .hasMessageContaining("Permission '$permId' is already associated with Role '$roleId'.")

            // Remove permission and test the outcome
            val roleWithPermissionRemoved = proxy.removePermission(roleId, permId)
            assertTrue(roleWithPermissionRemoved.responseBody.permissions.isEmpty())
            eventually {
                assertDoesNotThrow {
                    assertTrue(proxy.getRole(roleId).permissions.isEmpty())
                }
            }
        }
    }

    @Test
    @Order(3)
    fun `test unable to un-associate protected role`() {
        val protectedRole = cordaCluster.clusterHttpClientFor(RoleEndpoint::class.java).use { client ->
            val proxy = client.start().proxy
            proxy.getRoles().first { it.roleName == DEFAULT_SYSTEM_ADMIN_ROLE }
        }

        cordaCluster.clusterHttpClientFor(UserEndpoint::class.java).use { client ->
            val proxy = client.start().proxy

            assertThatThrownBy {
                proxy.removeRole(adminUser, protectedRole.id)
            }.isInstanceOf(RequestErrorException::class.java)
             .hasMessageContaining("$DEFAULT_SYSTEM_ADMIN_ROLE cannot be removed from $DEFAULT_ADMIN_FULL_NAME")
        }
    }

    @Test
    @Order(4)
    fun `test cannot change protected role`() {
        cordaCluster.clusterHttpClientFor(RoleEndpoint::class.java).use { client ->
            val proxy = client.start().proxy
            val protectedRole = proxy.getRoles().first { it.roleName == DEFAULT_SYSTEM_ADMIN_ROLE }
            assertThatThrownBy {
                proxy.removePermission(protectedRole.id, protectedRole.permissions.first().id)
            }.isInstanceOf(RequestErrorException::class.java)
                .hasMessageContaining("$DEFAULT_SYSTEM_ADMIN_ROLE cannot be changed")
        }
    }
}