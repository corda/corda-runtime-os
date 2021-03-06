package net.corda.processors.rpc

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import net.corda.flow.rpcops.v1.FlowRpcOps
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.RpcOps
import net.corda.httprpc.server.config.models.HttpRpcContext
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.factory.HttpRpcServerFactory
import net.corda.libs.configuration.endpoints.v1.ConfigRPCOps
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRPCOps
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRPCOps
import net.corda.membership.httprpc.v1.CertificatesRpcOps
import net.corda.membership.httprpc.v1.HsmRpcOps
import net.corda.membership.httprpc.v1.KeysRpcOps
import net.corda.membership.httprpc.v1.MemberLookupRpcOps
import net.corda.membership.httprpc.v1.MemberRegistrationRpcOps
import net.corda.membership.httprpc.v1.NetworkRpcOps
import net.corda.processors.rpc.diff.diff
import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.assertThat
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
            CertificatesRpcOps::class.java,
            ConfigRPCOps::class.java,
            CpiUploadRPCOps::class.java,
            FlowRpcOps::class.java,
            HsmRpcOps::class.java,
            KeysRpcOps::class.java,
            MemberLookupRpcOps::class.java,
            MemberRegistrationRpcOps::class.java,
            NetworkRpcOps::class.java,
            PermissionEndpoint::class.java,
            RoleEndpoint::class.java,
            UserEndpoint::class.java,
            VirtualNodeRPCOps::class.java,
            VirtualNodeMaintenanceRPCOps::class.java
        )

        // `cardinality` is not equal to `expectedRpcOps.size` as there might be some test RpcOps as well
        @InjectService(service = PluggableRPCOps::class, cardinality = 16, timeout = 10_000)
        lateinit var dynamicRpcOps: List<RpcOps>

        @InjectService(service = HttpRpcServerFactory::class, timeout = 10_000)
        lateinit var httpServerFactory: HttpRpcServerFactory
    }

    @Test
    fun test() {
        val allOps = dynamicRpcOps.map { (it as PluggableRPCOps<*>).targetInterface }.sortedBy { it.name }
        assertThat(allOps).containsAll(importantRpcOps)

        logger.info("RPC Ops discovered: ${allOps.map { it.simpleName }}")

        val existingSwaggerJson = computeExistingSwagger()
        val baselineSwagger = fetchBaseline()

        val diffReport = existingSwaggerJson.second.diff(baselineSwagger)

        assertThat(diffReport).withFailMessage(
            "Produced Open API content:\n" + existingSwaggerJson.first +
                    "\nis different to the baseline. Differences noted: ${diffReport.joinToString(" ## ")}"
        ).isEmpty()
    }

    private fun fetchBaseline(): OpenAPI {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream("/swaggerBaseline.json"))
        return stream.use {
            val jsonString = String(it.readAllBytes())
            Json.mapper().readValue(jsonString, OpenAPI::class.java)
        }
    }

    private fun computeExistingSwagger(): Pair<String, OpenAPI> {
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

            // It may be handy to leave the HTTP Server running for a little while such that when developers
            // experimenting with new endpoints locally could access URL: http://localhost:port/api/v1/swagger to see
            // how their newly introduced OpenAPI is looking in SwaggerUI.
            //Thread.sleep(1_000_000)

            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder(URI.create(url))
                .header("accept", "application/json")
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            val body = response.body()
            body to Json.mapper().readValue(body, OpenAPI::class.java)
        }
    }
}