package net.corda.applications.workers.rest

import net.corda.applications.workers.rest.http.SkipWhenRestEndpointUnavailable
import net.corda.applications.workers.rest.utils.E2eClusterBConfig
import net.corda.applications.workers.rest.utils.E2eClusterFactory
import net.corda.rest.client.exceptions.MissingRequestedResourceException
import net.corda.rest.response.ResponseEntity
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionResponseType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

@SkipWhenRestEndpointUnavailable
class CreatePermissionE2eTest {

    private val cordaCluster = E2eClusterFactory.getE2eCluster(E2eClusterBConfig)

    @Test
    fun testCreateAndGet() {
        cordaCluster.clusterHttpClientFor(PermissionEndpoint::class.java).use { client ->
            val proxy = client.start().proxy

            // Check that permission does not exist yet
            with("randomID") {
                assertThatThrownBy { proxy.getPermission(this) }.isInstanceOf(MissingRequestedResourceException::class.java)
                    .hasMessageContaining("Permission '$this' not found")
            }

            // Create permission
            val setPermString = cordaCluster.uniqueName + "-PermissionString"
            val createPermType = CreatePermissionType(PermissionType.ALLOW, setPermString, null, null)

            fun PermissionResponseType.assertResponseType(): PermissionResponseType {
                assertSoftly {
                    it.assertThat(permissionString).isEqualTo(setPermString)
                    it.assertThat(permissionType).isEqualTo(PermissionType.ALLOW)
                }
                return this
            }
            fun ResponseEntity<PermissionResponseType>.assertCreated(): PermissionResponseType {
                assertSoftly {
                    it.assertThat(this.responseCode.statusCode).isEqualTo(201)
                    it.assertThat(this.responseBody).isNotNull
                    this.responseBody.assertResponseType()
                }
                return this.responseBody
            }

            val permId = proxy.createPermission(createPermType).assertCreated().id

            // Check that the permission does exist now. The distribution of entity records may take some time to complete on the
            // message bus, hence use of `eventually` along with `assertDoesNotThrow`.
            eventually {
                assertDoesNotThrow {
                    // Retrieve by id
                    val permById = proxy.getPermission(permId).assertResponseType()

                    // Retrieve by query
                    val firstPermission = proxy.queryPermissions(
                        1, PermissionType.ALLOW.name,
                        permissionStringPrefix = setPermString
                    ).first()
                    assertThat(firstPermission).isEqualTo(permById)
                }
            }
        }
    }
}