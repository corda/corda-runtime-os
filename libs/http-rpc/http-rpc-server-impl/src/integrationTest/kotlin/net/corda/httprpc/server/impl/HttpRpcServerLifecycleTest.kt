package net.corda.httprpc.server.impl

import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.impl.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.server.impl.utils.WebRequest
import net.corda.httprpc.server.impl.utils.multipartDir
import net.corda.httprpc.test.LifecycleRPCOpsImpl
import net.corda.httprpc.tools.HttpVerb.GET
import net.corda.v5.base.util.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals


class HttpRpcServerLifecycleTest : HttpRpcServerTestBase() {
    companion object {

        val lifecycleRPCOpsImpl = LifecycleRPCOpsImpl()

        @BeforeAll
        @JvmStatic
        fun setUpBeforeClass() {
            val httpRpcSettings = HttpRpcSettings(
                NetworkHostAndPort("localhost", findFreePort()),
                context,
                null,
                null,
                HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE
            )
            server = HttpRpcServerImpl(
                listOf(lifecycleRPCOpsImpl),
                securityManager,
                httpRpcSettings,
                multipartDir,
                true
            ).apply { start() }
            client =
                TestHttpClientUnirestImpl("http://${httpRpcSettings.address.host}:${httpRpcSettings.address.port}/" +
                        "${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/")
        }

        @AfterAll
        @JvmStatic
        fun cleanUpAfterClass() {
            if (isServerInitialized()) {
                server.stop()
            }
        }
    }

    @Test
    fun `GET hello name returns string greeting name`() {

        // Should report unavailable when RPC implementation is not started
        with(client.call(GET, WebRequest<Any>("lifecycle/hello/world?id=1"), userName, password)) {
            assertEquals(HttpStatus.SC_SERVICE_UNAVAILABLE, responseStatus)
        }

        // Do start
        lifecycleRPCOpsImpl.start()

        // Assert functions normally
        lifecycleRPCOpsImpl.use {
            with(client.call(GET, WebRequest<Any>("lifecycle/hello/world?id=1"), userName, password)) {
                assertEquals(HttpStatus.SC_OK, responseStatus)
                assertEquals(""""Hello 1 : world"""", body)
            }
        }

        // Assert back to unavailable after stop/close
        with(client.call(GET, WebRequest<Any>("lifecycle/hello/world?id=1"), userName, password)) {
            assertEquals(HttpStatus.SC_SERVICE_UNAVAILABLE, responseStatus)
        }
    }
}