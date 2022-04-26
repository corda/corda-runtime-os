package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.httprpc.client.exceptions.InternalErrorException
import net.corda.httprpc.client.exceptions.MissingRequestedResourceException
import net.corda.httprpc.client.exceptions.RequestErrorException
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertDoesNotThrow

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CreateRoleE2eTest {

    private val testToolkit by TestToolkitProperty()

    companion object {
        private var sharedRoleId: String? = null
    }

    @Test
    @Disabled("The test is flaky, will be fixed by CORE-4656")
    @Order(1)
    fun `test getRole and createRole HTTP APIs including validation`() {
        testToolkit.httpClientFor(RoleEndpoint::class.java).use { client ->
            val name = testToolkit.uniqueName
            val proxy = client.start().proxy

            // Check the role does not exist yet
            assertThatThrownBy { proxy.getRole("fakeId") }.isInstanceOf(MissingRequestedResourceException::class.java)
                .hasMessageContaining("Role 'fakeId' not found.")

            // Create role
            val createRoleType = CreateRoleType(name, null)

            val roleId = with(proxy.createRole(createRoleType)) {
                assertSoftly {
                    it.assertThat(roleName).isEqualTo(name)
                    it.assertThat(version).isEqualTo(0L)
                    it.assertThat(groupVisibility).isNull()
                    it.assertThat(permissions).isEmpty()
                }
                id
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
            val name2 = testToolkit.uniqueName

            // Try to create a role with a group that does not exist
            assertThatThrownBy { proxy.createRole(CreateRoleType(name2, groupName)) }
                .isInstanceOf(MissingRequestedResourceException::class.java)
                .hasMessageContaining("Group '$groupName' not found.")

            sharedRoleId = roleId
        }
    }

    @Test
    @Disabled("The test is flaky, will be fixed by CORE-4656")
    @Order(2)
    fun `test add and remove permission to a role`() {
        val roleId = requireNotNull(sharedRoleId)

        // Create permission
        val permission = testToolkit.httpClientFor(PermissionEndpoint::class.java).use { client ->
            val proxy = client.start().proxy

            // Create permission
            val setPermString = testToolkit.uniqueName + "-PermissionString"
            val createPermType = CreatePermissionType(PermissionType.ALLOW, setPermString, null, null)

            val perm = proxy.createPermission(createPermType)

            // Check that the permission does exist now. The distribution of entity records may take some time to complete on the
            // message bus, hence use of `eventually` along with `assertDoesNotThrow`.
            eventually {
                assertDoesNotThrow {
                    assertNotNull(proxy.getPermission(perm.id))
                }
            }
            perm
        }

        val permId = permission.id
        val permTs = permission.updateTimestamp

        // Test adding/removing permission to a role
        testToolkit.httpClientFor(RoleEndpoint::class.java).use { client ->
            val proxy = client.start().proxy

            // Try to remove association when it does not exist
            assertThatThrownBy { proxy.removePermission(roleId, permId) }.isInstanceOf(RequestErrorException::class.java)
                .hasMessageContaining("Permission '$permId' is not associated with Role '$roleId'.")

            val roleWithPermission = proxy.addPermission(roleId, permId)
            assertEquals(permId, roleWithPermission.permissions[0].id)

            eventually {
                assertDoesNotThrow {
                    val role = proxy.getRole(roleId)
                    val permissionAssociationResponseType = role.permissions[0]
                    assertEquals(permId, permissionAssociationResponseType.id)
                    assertTrue(permissionAssociationResponseType.createdTimestamp.isAfter(permTs))
                }
            }

            // Try to add same association again when it already exists
            assertThatThrownBy { proxy.addPermission(roleId, permId) }.isInstanceOf(RequestErrorException::class.java)
                .hasMessageContaining("Permission '$permId' is already associated with Role '$roleId'.")

            val roleWithPermissionRemoved = proxy.removePermission(roleId, permId)
            assertTrue(roleWithPermissionRemoved.permissions.isEmpty())
            eventually {
                assertDoesNotThrow {
                    assertTrue(proxy.getRole(roleId).permissions.isEmpty())
                }
            }
        }
    }
}