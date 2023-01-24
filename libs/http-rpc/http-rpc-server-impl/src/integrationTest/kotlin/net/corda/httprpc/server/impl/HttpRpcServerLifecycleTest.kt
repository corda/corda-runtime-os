package net.corda.httprpc.server.impl

import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.test.LifecycleRestResourceImpl
import net.corda.httprpc.test.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.test.utils.WebRequest
import net.corda.httprpc.test.utils.multipartDir
import net.corda.httprpc.tools.HttpVerb.GET
import net.corda.test.util.lifecycle.usingLifecycle
import net.corda.utilities.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class HttpRpcServerLifecycleTest : HttpRpcServerTestBase() {
    companion object {

        val lifecycleRPCOpsImpl = LifecycleRestResourceImpl()

        @BeforeAll
        @JvmStatic
        fun setUpBeforeClass() {
            val httpRpcSettings = HttpRpcSettings(
                NetworkHostAndPort("localhost", 0),
                context,
                null,
                null,
                HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
                20000L
            )
            server = HttpRpcServerImpl(
                listOf(lifecycleRPCOpsImpl),
                ::securityManager,
                httpRpcSettings,
                multipartDir,
                true
            ).apply { start() }
            client =
                TestHttpClientUnirestImpl("http://${httpRpcSettings.address.host}:${server.port}/" +
                        "${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/")
        }

        @AfterAll
        @JvmStatic
        fun cleanUpAfterClass() {
            if (isServerInitialized()) {
                server.close()
            }
        }
    }

    @Test
    fun `GET hello name returns string greeting name`() {

        // Should report unavailable when RPC implementation is not started
        with(client.call(GET, WebRequest<Any>("lifecycle/hello/world?id=1"), userName, password)) {
            println("### $responseStatus")
            assertEquals(HttpStatus.SC_SERVICE_UNAVAILABLE, responseStatus)
        }

        // Do start
        lifecycleRPCOpsImpl.start()

        // Assert functions normally
        lifecycleRPCOpsImpl.usingLifecycle {
            with(client.call(GET, WebRequest<Any>("lifecycle/hello/world?id=1"), userName, password)) {
                assertEquals(HttpStatus.SC_OK, responseStatus)
                assertEquals("Hello 1 : world", body)
            }
            lifecycleRPCOpsImpl.stop()
        }

        // Assert back to unavailable after stop/close
        with(client.call(GET, WebRequest<Any>("lifecycle/hello/world?id=1"), userName, password)) {
            assertEquals(HttpStatus.SC_SERVICE_UNAVAILABLE, responseStatus)
        }
    }
}
