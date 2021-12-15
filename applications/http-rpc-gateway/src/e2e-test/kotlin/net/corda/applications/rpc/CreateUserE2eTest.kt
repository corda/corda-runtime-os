package net.corda.applications.rpc

import net.corda.applications.rpc.http.TestHttpInterfaceProperty
import net.corda.httprpc.client.exceptions.MissingRequestedResourceException
import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CreateUserE2eTest {

    private val httpInterface by TestHttpInterfaceProperty()

    @Test
    fun testCreateAndGet() {
        httpInterface.clientFor(UserEndpoint::class.java).use {
            val userName = httpInterface.uniqueName
            val proxy = it.start().proxy

            // Check the user does not exist yet
            assertThatThrownBy { proxy.getUser(userName) }.isInstanceOf(MissingRequestedResourceException::class.java)
                .hasMessageContaining("$userName not found")
        }
    }
}