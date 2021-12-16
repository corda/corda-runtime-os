package net.corda.applications.rpc

import net.corda.applications.rpc.http.TestToolkitProperty
import net.corda.httprpc.client.exceptions.InternalErrorException
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class CreateRoleE2eTest {

    private val testToolkit by TestToolkitProperty()

    @Test
    fun testCreateAndGet() {
        testToolkit.httpClientFor(RoleEndpoint::class.java).use { client ->
            val name = testToolkit.uniqueName
            val proxy = client.start().proxy

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

            // Try to create a role with the same name again and verify that it fails
            assertThatThrownBy { proxy.createRole(CreateRoleType(name, null)) }
                .isInstanceOf(InternalErrorException::class.java)
                .hasMessageContaining("Failed to create new role: $name as they already exist")

            val groupName = "non-existing-group"
            val name2 = testToolkit.uniqueName

            // Try to create a role with the same name again and verify that it fails
            assertThatThrownBy { proxy.createRole(CreateRoleType(name2, groupName)) }
                .isInstanceOf(InternalErrorException::class.java)
                .hasMessageContaining("Failed to create new Role: $name2 as the specified group visibility: $groupName does not exist.")

        }
    }
}