package net.corda.httprpc.server.impl

import net.corda.httprpc.server.config.models.RestServerSettings
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
            val restServerSettings = RestServerSettings(
                NetworkHostAndPort("localhost", 0),
                context,
                null,
                null,
                RestServerSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
                20000L
            )
            server = HttpRpcServerImpl(
                listOf(lifecycleRPCOpsImpl),
                ::securityManager,
                restServerSettings,
                multipartDir,
                true
            ).apply { start() }
            client =
                TestHttpClientUnirestImpl("http://${restServerSettings.address.host}:${server.port}/" +
                        "${restServerSettings.context.basePath}/v${restServerSettings.context.version}/")
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
