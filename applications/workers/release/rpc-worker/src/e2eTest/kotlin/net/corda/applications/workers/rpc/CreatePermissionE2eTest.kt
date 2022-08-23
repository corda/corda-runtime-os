package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.applications.workers.rpc.http.SkipWhenRpcEndpointUnavailable
import net.corda.httprpc.client.exceptions.MissingRequestedResourceException
import net.corda.httprpc.response.HttpResponse
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionResponseType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

@SkipWhenRpcEndpointUnavailable
class CreatePermissionE2eTest {

    private val testToolkit by TestToolkitProperty()

    @Test
    fun testCreateAndGet() {
        testToolkit.httpClientFor(PermissionEndpoint::class.java).use { client ->
            val proxy = client.start().proxy

            // Check that permission does not exist yet
            with("randomID") {
                assertThatThrownBy { proxy.getPermission(this) }.isInstanceOf(MissingRequestedResourceException::class.java)
                    .hasMessageContaining("Permission '$this' not found")
            }

            // Create permission
            val setPermString = testToolkit.uniqueName + "-PermissionString"
            val createPermType = CreatePermissionType(PermissionType.ALLOW, setPermString, null, null)

            fun PermissionResponseType.assertAsExpected(): PermissionResponseType {
                assertSoftly {
                    it.assertThat(permissionString).isEqualTo(setPermString)
                    it.assertThat(permissionType).isEqualTo(PermissionType.ALLOW)
                }
                return this
            }
            fun HttpResponse<PermissionResponseType>.assertAsExpected(): PermissionResponseType {
                assertSoftly {
                    it.assertThat(this.responseCode.statusCode).isEqualTo(201)
                    it.assertThat(this.responseBody).isNotNull
                    this.responseBody!!.assertAsExpected()
                }
                return this.responseBody!!
            }

            val permId = proxy.createPermission(createPermType).assertAsExpected().id

            // Check that the permission does exist now. The distribution of entity records may take some time to complete on the
            // message bus, hence use of `eventually` along with `assertDoesNotThrow`.
            eventually {
                assertDoesNotThrow {
                    proxy.getPermission(permId).assertAsExpected()
                }
            }
        }
    }
}