package net.corda.httprpc.server.impl

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import net.corda.httprpc.server.impl.apigen.processing.APIStructureRetriever
import net.corda.httprpc.server.impl.apigen.processing.JavalinRouteProviderImpl
import net.corda.httprpc.server.impl.apigen.processing.openapi.OpenApiInfoProvider
import net.corda.httprpc.server.impl.apigen.test.MultipleParamAnnotationApiImpl
import net.corda.httprpc.server.impl.apigen.test.NonCordaSerializableAPI
import net.corda.httprpc.server.impl.apigen.test.NonCordaSerializableAPIImpl
import net.corda.httprpc.server.impl.apigen.test.TestHealthCheckAPIImpl
import net.corda.httprpc.server.config.HttpRpcSettingsProvider
import net.corda.httprpc.server.impl.internal.HttpRpcServerInternal
import net.corda.httprpc.server.impl.internal.HttpRpcServerInternal.Companion.INSECURE_SERVER_DEV_MODE_WARNING
import net.corda.httprpc.server.impl.internal.HttpRpcServerInternal.Companion.SSL_PASSWORD_MISSING
import net.corda.httprpc.server.impl.security.SecurityManagerRPCImpl
import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.httprpc.tools.annotations.validation.ParameterBodyCordaSerializableAnnotationValidator
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.nio.file.Paths
import kotlin.reflect.jvm.javaMethod

class HttpRpcServerTest {

    private val portAllocator = 8080 // doesn't matter, server won't start anyway

    @Test
    fun `start server with ssl option but without ssl password specified throws illegal argument exception`() {
        val configProvider = mock(HttpRpcSettingsProvider::class.java)
        doReturn(NetworkHostAndPort("localhost", portAllocator)).whenever(configProvider).getHostAndPort()
        doReturn("1").whenever(configProvider).getApiVersion()
        doReturn("/").whenever(configProvider).getBasePath()
        doReturn(Paths.get("my", "ssl", "keystore", "path")).whenever(configProvider).getSSLKeyStorePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePassword()
        doReturn(false).whenever(configProvider).isDevModeEnabled()
        Assertions.assertThrows(
            IllegalArgumentException::class.java, {
                HttpRpcServerInternal(
                    JavalinRouteProviderImpl(
                        "/",
                        "1",
                        APIStructureRetriever(listOf(TestHealthCheckAPIImpl())).structure.getOrThrow(),
                        ClassLoader.getSystemClassLoader()
                    ),
                    SecurityManagerRPCImpl(emptySet()),
                    configProvider,
                    OpenApiInfoProvider(APIStructureRetriever(listOf(TestHealthCheckAPIImpl())).structure.getOrThrow(), configProvider)
                )
            },
            SSL_PASSWORD_MISSING
        )
    }

    @Test
    fun `start server with ssl disabled but without dev mode enabled throws unsupported operation exception`() {
        val configProvider = mock(HttpRpcSettingsProvider::class.java)
        doReturn(NetworkHostAndPort("localhost", portAllocator)).whenever(configProvider).getHostAndPort()
        doReturn("1").whenever(configProvider).getApiVersion()
        doReturn("/").whenever(configProvider).getBasePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePassword()
        doReturn(false).whenever(configProvider).isDevModeEnabled()
        Assertions.assertThrows(
            Exception::class.java, {
                HttpRpcServerInternal(
                    JavalinRouteProviderImpl(
                        "/",
                        "1",
                        APIStructureRetriever(listOf(TestHealthCheckAPIImpl())).structure.getOrThrow(),
                        ClassLoader.getSystemClassLoader()
                    ),
                    SecurityManagerRPCImpl(emptySet()),
                    configProvider,
                    OpenApiInfoProvider(APIStructureRetriever(listOf(TestHealthCheckAPIImpl())).structure.getOrThrow(), configProvider)
                )
            },
            INSECURE_SERVER_DEV_MODE_WARNING
        )
    }

    @Test
    fun `start server with ssl disabled with dev mode enabled but non-CordaSerializable endpoint parameters throws exception`() {
        val configProvider = mock(HttpRpcSettingsProvider::class.java)
        doReturn(NetworkHostAndPort("localhost", portAllocator)).whenever(configProvider).getHostAndPort()
        doReturn("1").whenever(configProvider).getApiVersion()
        doReturn("/").whenever(configProvider).getBasePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePassword()
        doReturn(true).whenever(configProvider).isDevModeEnabled()
        assertThatThrownBy {
            HttpRpcServerInternal(
                JavalinRouteProviderImpl(
                    "/",
                    "1",
                    APIStructureRetriever(listOf(NonCordaSerializableAPIImpl())).structure.getOrThrow(),
                    ClassLoader.getSystemClassLoader()
                ),
                SecurityManagerRPCImpl(emptySet()),
                configProvider,
                OpenApiInfoProvider(APIStructureRetriever(listOf(TestHealthCheckAPIImpl())).structure.getOrThrow(), configProvider)
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage(
                "Errors when validate resource classes:\n" +
                        ParameterBodyCordaSerializableAnnotationValidator.error(NonCordaSerializableAPI::call.javaMethod!!, "data")
            )
    }

    @Test
    fun `OpenApi Json of discovered RPCOps should be deserializable to OpenApi object`() {
        val configProvider = mock(HttpRpcSettingsProvider::class.java)
        doReturn(NetworkHostAndPort("localhost", portAllocator)).whenever(configProvider).getHostAndPort()
        doReturn("1").whenever(configProvider).getApiVersion()
        doReturn("/").whenever(configProvider).getBasePath()
        doReturn("testOpenApi").whenever(configProvider).getApiTitle()
        doReturn(null).whenever(configProvider).getSSLKeyStorePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePassword()
        doReturn(true).whenever(configProvider).isDevModeEnabled()

        val resources = APIStructureRetriever(listOf(TestHealthCheckAPIImpl())).structure.getOrThrow()
        val endpointsCount = resources.sumOf { it.endpoints.count() }
        val openApiJson = OpenApiInfoProvider(resources, configProvider).openApiString
        val openApi = Json.mapper().readValue(openApiJson, OpenAPI::class.java)
        val totalPathsCount = openApi.paths.count { it.value.get != null } + openApi.paths.count { it.value.post != null }

        assertNotNull(openApi)
        assertEquals(resources.size, openApi.tags.size)
        assertEquals(endpointsCount, totalPathsCount)
    }

    @Test
    fun `start server with duplicate HttpRpcParameter throws exception`() {
        val configProvider = mock(HttpRpcSettingsProvider::class.java)
        doReturn(NetworkHostAndPort("localhost", portAllocator)).whenever(configProvider).getHostAndPort()
        doReturn("1").whenever(configProvider).getApiVersion()
        doReturn("/").whenever(configProvider).getBasePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePath()
        doReturn(null).whenever(configProvider).getSSLKeyStorePassword()
        doReturn(true).whenever(configProvider).isDevModeEnabled()
        assertThatThrownBy {
            HttpRpcServerInternal(
                JavalinRouteProviderImpl(
                    "/",
                    "1",
                    APIStructureRetriever(listOf(MultipleParamAnnotationApiImpl())).structure.getOrThrow(),
                    ClassLoader.getSystemClassLoader()
                ),
                SecurityManagerRPCImpl(emptySet()),
                configProvider,
                OpenApiInfoProvider(APIStructureRetriever(listOf(TestHealthCheckAPIImpl())).structure.getOrThrow(), configProvider)
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage(
                "Errors when validate resource classes:\n" +
                        "Parameter test.twoAnnotations can't have multiple types"
            )
    }
}

