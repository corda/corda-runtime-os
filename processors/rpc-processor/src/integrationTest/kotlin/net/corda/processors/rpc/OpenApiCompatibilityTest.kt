package net.corda.processors.rpc

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import net.corda.flow.rpcops.v1.FlowClassRestResource
import net.corda.flow.rpcops.v1.FlowRestResource
import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.RestResource
import net.corda.httprpc.server.config.models.RestContext
import net.corda.httprpc.server.config.models.RestServerSettings
import net.corda.httprpc.server.factory.RestServerFactory
import net.corda.libs.configuration.endpoints.v1.ConfigRestResource
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRestResource
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRestResource
import net.corda.membership.httprpc.v1.CertificatesRestResource
import net.corda.membership.httprpc.v1.HsmRestResource
import net.corda.membership.httprpc.v1.KeysRestResource
import net.corda.membership.httprpc.v1.MGMRestResource
import net.corda.membership.httprpc.v1.MemberLookupRestResource
import net.corda.membership.httprpc.v1.MemberRegistrationRestResource
import net.corda.membership.httprpc.v1.NetworkRestResource
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
            CertificatesRestResource::class.java, // P2P
            HsmRestResource::class.java, // P2P
            KeysRestResource::class.java, // P2P
            ConfigRestResource::class.java, // Flow
            FlowRestResource::class.java, // Flow
            FlowClassRestResource::class.java, // Flow
            CpiUploadRestResource::class.java, // Packaging
            VirtualNodeRestResource::class.java, // Packaging
            MemberLookupRestResource::class.java, // MGM
            MemberRegistrationRestResource::class.java, // MGM
            MGMRestResource::class.java, // MGM
            NetworkRestResource::class.java, // MGM
            PermissionEndpoint::class.java, // RPC
            RoleEndpoint::class.java, // RPC
            UserEndpoint::class.java, // RPC
            VirtualNodeMaintenanceRestResource::class.java // RPC
        )

        // `cardinality` is not equal to `importantRpcOps.size` as there might be some test RestResource as well
        @InjectService(service = PluggableRestResource::class, cardinality = 16, timeout = 10_000)
        lateinit var dynamicRpcOps: List<RestResource>

        @InjectService(service = RestServerFactory::class, timeout = 10_000)
        lateinit var httpServerFactory: RestServerFactory
    }

    @Test
    fun test() {
        val allOps = dynamicRpcOps.map { (it as PluggableRestResource<*>).targetInterface }.sortedBy { it.name }
        assertThat(allOps.filterNot {
            it.name.contains("HelloRestResource") // the only test, i.e. not important RPC Ops we have
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
        val context = RestContext(
            "1",
            "api",
            "Corda HTTP RPC API",
            "All the endpoints for publicly visible Open API calls"
        )
        val serverAddress = NetworkHostAndPort("localhost", 0)
        val restServerSettings = RestServerSettings(
            serverAddress,
            context,
            null,
            null,
            RestServerSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
            20000L
        )

        val server = httpServerFactory.createRestServer(
            dynamicRpcOps.map { it as PluggableRestResource<out RestResource> }.sortedBy { it.targetInterface.name },
            { FakeSecurityManager() }, restServerSettings, multipartDir, devMode = true
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
