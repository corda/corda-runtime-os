package net.corda.applications.rpc

import net.corda.applications.rpc.http.TestToolkitProperty
import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.test.util.eventually
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.endpoints.v1.user.types.AddRoleToUserType

class UserRoleAssociationE2eTest {

    private val testToolkit by TestToolkitProperty()

    data class RoleDetails(val id:String, val name: String)

    @Test
    fun `test association of user and role`() {

        val newRoleDetails = testToolkit.httpClientFor(RoleEndpoint::class.java).use { client ->
            val name = testToolkit.uniqueName
            val proxy = client.start().proxy

            // Create role
            val createRoleType = CreateRoleType(name, null)

            val roleDetails = with(proxy.createRole(createRoleType)) {
                assertSoftly {
                    it.assertThat(roleName).isEqualTo(name)
                    it.assertThat(version).isEqualTo(0L)
                    it.assertThat(groupVisibility).isNull()
                    it.assertThat(permissions).isEmpty()
                }
                RoleDetails(id, roleName)
            }

            // Check the role does exist now. The distribution of Role record may take some time to complete on the
            // message bus, hence use of `eventually` along with `assertDoesNotThrow`.
            eventually {
                assertDoesNotThrow {
                    with(proxy.getRole(roleDetails.id)) {
                        assertSoftly {
                            it.assertThat(roleName).isEqualTo(name)
                            it.assertThat(version).isEqualTo(0L)
                            it.assertThat(groupVisibility).isNull()
                            it.assertThat(permissions).isEmpty()
                        }
                    }
                }
            }
            roleDetails
        }

        testToolkit.httpClientFor(UserEndpoint::class.java).use { client ->
            val userName = testToolkit.uniqueName
            val proxy = client.start().proxy

            // Create user
            val password = testToolkit.uniqueName
            val passwordExpirySet = Instant.now().plus(1, DAYS).truncatedTo(DAYS)
            val createUserType = CreateUserType(userName, userName, true, password, passwordExpirySet, null)

            with(proxy.createUser(createUserType)) {
                assertSoftly {
                    it.assertThat(loginName).isEqualTo(userName)
                    it.assertThat(passwordExpiry).isEqualTo(passwordExpirySet)
                }
            }

            // Check the user does exist now. The distribution of User record may take some time to complete on the
            // message bus, hence use of `eventually` along with `assertDoesNotThrow`.
            eventually {
                assertDoesNotThrow {
                    with(proxy.getUser(userName)) {
                        assertSoftly {
                            it.assertThat(loginName).isEqualTo(userName)
                            it.assertThat(passwordExpiry).isEqualTo(passwordExpirySet)
                        }
                    }
                }
            }

            val addRoleToUserType = AddRoleToUserType(userName, newRoleDetails.name)
            with(proxy.addRole(addRoleToUserType)) {
                assertSoftly {
                    it.assertThat(loginName).isEqualTo(userName)
                    it.assertThat(roleAssociations).hasSize(1)
                    it.assertThat(roleAssociations.first().id).isEqualTo(newRoleDetails.id)
                }
            }
        }
    }
}