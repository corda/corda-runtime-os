package net.corda.applications.rpc

import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import net.corda.applications.rpc.http.TestToolkitProperty
import net.corda.httprpc.client.exceptions.InternalErrorException
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class UserRoleAssociationE2eTest {

    private val testToolkit by TestToolkitProperty()

    @Test
    fun `test association of user and role`() {

        val roleId = testToolkit.httpClientFor(RoleEndpoint::class.java).use { client ->
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
            roleId
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

            // add the role to the user
            with(proxy.addRole(userName, roleId)) {
                assertSoftly {
                    it.assertThat(loginName).isEqualTo(userName)
                    it.assertThat(roleAssociations).hasSize(1)
                    it.assertThat(roleAssociations.first().roleId).isEqualTo(roleId)
                }
            }

            // add the role again to assert validation
            Assertions.assertThatThrownBy { proxy.addRole(userName, roleId) }
                .isInstanceOf(InternalErrorException::class.java)
                .hasMessageContaining("Role '$roleId' is already associated with User '$userName'.")

            // remove role
            with(proxy.removeRole(userName, roleId)) {
                assertSoftly {
                    it.assertThat(loginName).isEqualTo(userName)
                    it.assertThat(roleAssociations).hasSize(0)
                }
            }

            // get user, assert the change is persisted
            eventually {
                assertDoesNotThrow {
                    with(proxy.getUser(userName)) {
                        assertSoftly {
                            it.assertThat(loginName).isEqualTo(userName)
                            it.assertThat(passwordExpiry).isEqualTo(passwordExpirySet)
                            it.assertThat(roleAssociations.size).isEqualTo(0)
                        }
                    }
                }
            }
        }
    }
}