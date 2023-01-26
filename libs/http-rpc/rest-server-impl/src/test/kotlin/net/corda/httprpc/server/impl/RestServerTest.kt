package net.corda.httprpc.server.impl

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import net.corda.httprpc.server.config.RestServerSettingsProvider
import net.corda.httprpc.server.impl.apigen.models.EndpointMethod
import net.corda.httprpc.server.impl.apigen.processing.APIStructureRetriever
import net.corda.httprpc.server.impl.apigen.processing.JavalinRouteProviderImpl
import net.corda.httprpc.server.impl.apigen.processing.openapi.OpenApiInfoProvider
import net.corda.httprpc.server.impl.internal.RestServerInternal
import net.corda.httprpc.server.impl.internal.RestServerInternal.Companion.INSECURE_SERVER_DEV_MODE_WARNING
import net.corda.httprpc.server.impl.internal.RestServerInternal.Companion.SSL_PASSWORD_MISSING
import net.corda.httprpc.server.impl.rest.resources.impl.MultipleParamAnnotationApiImpl
import net.corda.httprpc.server.impl.security.RestAuthenticationProviderImpl
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.utilities.NetworkHostAndPort
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.nio.file.Paths
import org.mockito.kotlin.mock

class RestServerTest {

    private val portAllocator = 8080 // doesn't matter, server won't start anyway

    private val multiPartDir = Path.of(System.getProperty("java.io.tmpdir"))

    @Test
    fun `start server with ssl option but without ssl password specified throws illegal argument exception`() {
        val configProvider = mock(RestServerSettingsProvider::class.java)
        doReturn(NetworkHostAndPort("localhost", portAllocator)).whenever(configProvider).getHostAndPort()
        doReturn("1").whenever(configProvider).getApiVersion()
        doReturn("/").whenever(configProvider).getBasePath()
        doReturn(Paths.get("my", "ssl", "keystore", "path")).whenever(configProvider).getSSLKeyStorePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePassword()
        doReturn(false).whenever(configProvider).isDevModeEnabled()
        Assertions.assertThrows(
            IllegalArgumentException::class.java, {
                RestServerInternal(
                    JavalinRouteProviderImpl(
                        "/",
                        "1",
                        APIStructureRetriever(listOf(TestHealthCheckAPIImpl())).structure
                    ),
                    RestAuthenticationProviderImpl(emptySet()),
                    configProvider,
                    OpenApiInfoProvider(APIStructureRetriever(listOf(TestHealthCheckAPIImpl())).structure, configProvider),
                    multiPartDir,
                    mock()
                )
            },
            SSL_PASSWORD_MISSING
        )
    }

    @Test
    fun `start server with ssl disabled but without dev mode enabled throws unsupported operation exception`() {
        val configProvider = mock(RestServerSettingsProvider::class.java)
        doReturn(NetworkHostAndPort("localhost", portAllocator)).whenever(configProvider).getHostAndPort()
        doReturn("1").whenever(configProvider).getApiVersion()
        doReturn("/").whenever(configProvider).getBasePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePassword()
        doReturn(false).whenever(configProvider).isDevModeEnabled()
        Assertions.assertThrows(
            Exception::class.java, {
                RestServerInternal(
                    JavalinRouteProviderImpl(
                        "/",
                        "1",
                        APIStructureRetriever(listOf(TestHealthCheckAPIImpl())).structure
                    ),
                    RestAuthenticationProviderImpl(emptySet()),
                    configProvider,
                    OpenApiInfoProvider(APIStructureRetriever(listOf(TestHealthCheckAPIImpl())).structure, configProvider),
                    multiPartDir,
                    mock()
                )
            },
            INSECURE_SERVER_DEV_MODE_WARNING
        )
    }

    @Test
    fun `OpenApi Json of discovered REST resources should be deserializable to OpenApi object`() {
        val configProvider = mock(RestServerSettingsProvider::class.java)
        doReturn(NetworkHostAndPort("localhost", portAllocator)).whenever(configProvider).getHostAndPort()
        doReturn("1").whenever(configProvider).getApiVersion()
        doReturn("/").whenever(configProvider).getBasePath()
        doReturn("testOpenApi").whenever(configProvider).getApiTitle()
        doReturn(null).whenever(configProvider).getSSLKeyStorePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePassword()
        doReturn(true).whenever(configProvider).isDevModeEnabled()

        val resources = APIStructureRetriever(listOf(TestHealthCheckAPIImpl())).structure
        val endpointsCount =
            resources.sumOf { resource -> resource.endpoints.filterNot { it.method == EndpointMethod.WS }.count() }
        val openApiJson = OpenApiInfoProvider(resources, configProvider).openApiString
        val openApi = Json.mapper().readValue(openApiJson, OpenAPI::class.java)
        val totalPathsCount = openApi.paths.count { it.value.get != null } + openApi.paths.count { it.value.post != null }

        assertNotNull(openApi)
        assertEquals(resources.size, openApi.tags.size)
        assertEquals(endpointsCount, totalPathsCount)
    }

    @Test
    fun `start server with duplicate Parameter throws exception`() {
        val configProvider = mock(RestServerSettingsProvider::class.java)
        doReturn(NetworkHostAndPort("localhost", portAllocator)).whenever(configProvider).getHostAndPort()
        doReturn("1").whenever(configProvider).getApiVersion()
        doReturn("/").whenever(configProvider).getBasePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePassword()
        doReturn(true).whenever(configProvider).isDevModeEnabled()
        assertThatThrownBy {
            RestServerInternal(
                JavalinRouteProviderImpl(
                    "/",
                    "1",
                    APIStructureRetriever(listOf(MultipleParamAnnotationApiImpl())).structure
                ),
                RestAuthenticationProviderImpl(emptySet()),
                configProvider,
                OpenApiInfoProvider(APIStructureRetriever(listOf(TestHealthCheckAPIImpl())).structure, configProvider),
                multiPartDir,
                mock()
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage(
                "Errors when validate resource classes:\n" +
                        "Parameter test.twoAnnotations can't have multiple types"
            )
    }
}

