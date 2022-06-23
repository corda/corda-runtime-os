package net.corda.processors.rpc

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.RpcOps
import net.corda.httprpc.server.config.models.HttpRpcContext
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.factory.HttpRpcServerFactory
import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ExtendWith(ServiceExtension::class)
class OpenApiCompatibilityTest {

    companion object {
        private val logger = contextLogger()

        private val importantRpcOps = setOf(
            "CertificatesRpcOps",
            "ConfigRPCOps",
            "CpiUploadRPCOps",
            "FlowRpcOps",
            "HsmRpcOps",
            "KeysRpcOps",
            "MemberLookupRpcOps",
            "MemberRegistrationRpcOps",
            "PermissionEndpoint",
            "RoleEndpoint",
            "UserEndpoint",
            "VirtualNodeMaintenanceRPCOps",
            "VirtualNodeRPCOps"
        )

        // `cardinality` is not equal to `expectedRpcOps.size` as there might be some test RpcOps as well
        @InjectService(service = PluggableRPCOps::class, cardinality = 15, timeout = 10_000)
        lateinit var dynamicRpcOps: List<RpcOps>

        @InjectService(service = HttpRpcServerFactory::class, timeout = 10_000)
        lateinit var httpServerFactory: HttpRpcServerFactory

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setup() {

        }

        @Suppress("unused")
        @AfterAll
        @JvmStatic
        fun tearDown() {

        }
    }

    @Test
    fun test() {
        val allOps = dynamicRpcOps.map { (it as PluggableRPCOps<*>).targetInterface.simpleName }.sorted()
        logger.info("RPC Ops discovered: $allOps")
        assertThat(allOps).containsAll(importantRpcOps)

        val existingSwaggerJson = computeExistingSwagger()
        assertThat(existingSwaggerJson).contains(""""openapi" : "3.0.1"""")

        val baselineSwagger = fetchBaseline()
        assertThat(existingSwaggerJson).isEqualTo(baselineSwagger)
    }

    private fun fetchBaseline(): String {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream("/swaggerBaseline.json"))
        return stream.use {
            String(it.readAllBytes())
        }
    }

    private fun computeExistingSwagger(): String {
        val context = HttpRpcContext(
            "1",
            "api",
            "HttpRpcContext ${javaClass.simpleName}",
            "HttpRpcContext ${javaClass.simpleName}"
        )
        val freePort = findFreePort()
        val serverAddress = NetworkHostAndPort("localhost", freePort)
        val httpRpcSettings = HttpRpcSettings(
            serverAddress,
            context,
            null,
            null,
            HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE
        )

        val server = httpServerFactory.createHttpRpcServer(
            dynamicRpcOps.map { it as PluggableRPCOps<out RpcOps> }.sortedBy { it.targetInterface.name },
            FakeSecurityManager(), httpRpcSettings, multipartDir, devMode = true
        ).apply { start() }

        val url = "http://${serverAddress.host}:${serverAddress.port}/${context.basePath}/v${context.version}/swagger.json"
        logger.info("Swagger should be accessible on: $url")

        return server.use {

            //Thread.sleep(1_000_000)
            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder(URI.create(url))
                .header("accept", "application/json")
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.body()
        }
    }
}