package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.applications.workers.rpc.http.SkipWhenRestEndpointUnavailable
import net.corda.httprpc.client.exceptions.MissingRequestedResourceException
import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import net.corda.httprpc.exception.ResourceAlreadyExistsException

@SkipWhenRestEndpointUnavailable
class CreateUserE2eTest {

    private val testToolkit by TestToolkitProperty()

    @Test
    fun testCreateAndGet() {
        testToolkit.httpClientFor(UserEndpoint::class.java).use { client ->
            val userName = testToolkit.uniqueName
            val proxy = client.start().proxy

            // Check the user does not exist yet
            assertThatThrownBy { proxy.getUser(userName) }.isInstanceOf(MissingRequestedResourceException::class.java)
                .hasMessageContaining("User '$userName' not found")

            // Create user
            val password = testToolkit.uniqueName
            val passwordExpirySet = Instant.now().plus(1, DAYS).truncatedTo(DAYS)
            val createUserType = CreateUserType(userName, userName, true, password, passwordExpirySet, null)

            with(proxy.createUser(createUserType)) {
                assertSoftly {
                    it.assertThat(this.responseCode.statusCode).isEqualTo(201)
                    it.assertThat(this.responseBody).isNotNull
                    it.assertThat(this.responseBody.loginName).isEqualToIgnoringCase(userName)
                    it.assertThat(this.responseBody.passwordExpiry).isEqualTo(passwordExpirySet)
                }
            }

            // Check the user does exist now. The distribution of User record may take some time to complete on the
            // message bus, hence use of `eventually` along with `assertDoesNotThrow`.
            eventually {
                assertDoesNotThrow {
                    with(proxy.getUser(userName)) {
                        assertSoftly {
                            it.assertThat(loginName).isEqualToIgnoringCase(userName)
                            it.assertThat(passwordExpiry).isEqualTo(passwordExpirySet)
                        }
                    }
                }
            }

            // Try to create a user with the same login name again and verify that it fails
            assertThatThrownBy { proxy.createUser(createUserType.copy(fullName = "Alice")) }
                .isInstanceOf(ResourceAlreadyExistsException::class.java)
                .hasMessageContaining("User '${userName.lowercase()}' already exists.")
        }
    }
}
