package net.corda.processors.rpc

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import net.corda.flow.rpcops.v1.FlowClassRpcOps
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
import net.corda.membership.httprpc.v1.MGMRpcOps
import net.corda.membership.httprpc.v1.MemberLookupRpcOps
import net.corda.membership.httprpc.v1.MemberRegistrationRpcOps
import net.corda.membership.httprpc.v1.NetworkRpcOps
import net.corda.processors.rpc.diff.diff
import net.corda.utilities.NetworkHostAndPort
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ExtendWith(ServiceExtension::class)
class OpenApiCompatibilityTest {

    companion object {
        private val logger = contextLogger()

        private val importantRpcOps = setOf(
            CertificatesRpcOps::class.java, // P2P
            HsmRpcOps::class.java, // P2P
            KeysRpcOps::class.java, // P2P
            ConfigRPCOps::class.java, // Flow
            FlowRpcOps::class.java, // Flow
            FlowClassRpcOps::class.java, // Flow
            CpiUploadRPCOps::class.java, // Packaging
            VirtualNodeRPCOps::class.java, // Packaging
            MemberLookupRpcOps::class.java, // MGM
            MemberRegistrationRpcOps::class.java, // MGM
            MGMRpcOps::class.java, // MGM
            NetworkRpcOps::class.java, // MGM
            PermissionEndpoint::class.java, // RPC
            RoleEndpoint::class.java, // RPC
            UserEndpoint::class.java, // RPC
            VirtualNodeMaintenanceRPCOps::class.java // RPC
        )

        // `cardinality` is not equal to `importantRpcOps.size` as there might be some test RpcOps as well
        @InjectService(service = PluggableRPCOps::class, cardinality = 16, timeout = 10_000)
        lateinit var dynamicRpcOps: List<RpcOps>

        @InjectService(service = HttpRpcServerFactory::class, timeout = 10_000)
        lateinit var httpServerFactory: HttpRpcServerFactory
    }

    @Test
    fun test() {
        val allOps = dynamicRpcOps.map { (it as PluggableRPCOps<*>).targetInterface }.sortedBy { it.name }
        assertThat(allOps.filterNot {
            it.name.contains("HelloRpcOps") // the only test, i.e. not important RPC Ops we have
        }.toSet()).isEqualTo(importantRpcOps)

        logger.info("RPC Ops discovered: ${allOps.map { it.simpleName }}")

        val existingSwaggerJson = computeExistingSwagger()
        val baselineSwagger = fetchBaseline()

        val diffReport = existingSwaggerJson.second.diff(baselineSwagger)

        val tmpBaselineFile = kotlin.io.path.createTempFile(
            prefix = "open-api-baseline", suffix = ".json")
        File(tmpBaselineFile.toUri()).printWriter().use {
            it.println(existingSwaggerJson.second.toJson())
        }

        assertThat(diffReport).withFailMessage(
            "Produced Open API content:\n" + existingSwaggerJson.first +
                    "\nis different to the baseline. Differences noted: ${diffReport.joinToString(" ## ")}\n\n" +
                    "New baseline written to: $tmpBaselineFile"
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
            "Corda HTTP RPC API",
            "All the endpoints for publicly visible Open API calls"
        )
        val serverAddress = NetworkHostAndPort("localhost", 0)
        val httpRpcSettings = HttpRpcSettings(
            serverAddress,
            context,
            null,
            null,
            HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
            20000L
        )

        val server = httpServerFactory.createHttpRpcServer(
            dynamicRpcOps.map { it as PluggableRPCOps<out RpcOps> }.sortedBy { it.targetInterface.name },
            { FakeSecurityManager() }, httpRpcSettings, multipartDir, devMode = true
        ).apply { start() }

        val url = "http://${serverAddress.host}:${server.port}/${context.basePath}/v${context.version}/swagger.json"
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

private fun OpenAPI.toJson(): String {
    return Json.pretty(this)
}
