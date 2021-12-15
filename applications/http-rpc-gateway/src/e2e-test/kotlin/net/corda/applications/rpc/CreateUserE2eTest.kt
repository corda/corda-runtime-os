package net.corda.applications.rpc

import net.corda.applications.rpc.http.TestHttpInterfaceProperty
import net.corda.httprpc.client.exceptions.MissingRequestedResourceException
import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS

class CreateUserE2eTest {

    private val httpInterface by TestHttpInterfaceProperty()

    @Test
    fun testCreateAndGet() {
        httpInterface.clientFor(UserEndpoint::class.java).use { client ->
            val userName = httpInterface.uniqueName
            val proxy = client.start().proxy

            // Check the user does not exist yet
            assertThatThrownBy { proxy.getUser(userName) }.isInstanceOf(MissingRequestedResourceException::class.java)
                .hasMessageContaining("$userName not found")

            // Create user
            val password = httpInterface.uniqueName
            val passwordExpirySet = Instant.now().plus(1, DAYS).truncatedTo(DAYS)
            with(proxy.createUser(
                    CreateUserType(userName, userName, true, password, passwordExpirySet, null))) {
                assertSoftly {
                    it.assertThat(loginName).isEqualTo(userName)
                    it.assertThat(passwordExpiry).isEqualTo(passwordExpirySet)
                }
            }

            // Check the user does exist now
            with(proxy.getUser(userName)) {
                assertSoftly {
                    it.assertThat(loginName).isEqualTo(userName)
                    it.assertThat(passwordExpiry).isEqualTo(passwordExpirySet)
                }
            }
        }
    }
}