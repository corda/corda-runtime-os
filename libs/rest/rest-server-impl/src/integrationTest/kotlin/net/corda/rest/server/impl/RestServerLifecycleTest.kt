package net.corda.rest.server.impl

import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.test.LifecycleRestResourceImpl
import net.corda.rest.test.utils.TestHttpClientUnirestImpl
import net.corda.rest.test.utils.WebRequest
import net.corda.rest.test.utils.multipartDir
import net.corda.rest.tools.HttpVerb.GET
import net.corda.test.util.lifecycle.usingLifecycle
import net.corda.utilities.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class RestServerLifecycleTest : RestServerTestBase() {
    companion object {

        val lifecycleRestResourceImpl = LifecycleRestResourceImpl()

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
            server = RestServerImpl(
                listOf(lifecycleRestResourceImpl),
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

        // Should report unavailable when REST implementation is not started
        with(client.call(GET, WebRequest<Any>("lifecycle/hello/world?id=1"), userName, password)) {
            println("### $responseStatus")
            assertEquals(HttpStatus.SC_SERVICE_UNAVAILABLE, responseStatus)
        }

        // Do start
        lifecycleRestResourceImpl.start()

        // Assert functions normally
        lifecycleRestResourceImpl.usingLifecycle {
            with(client.call(GET, WebRequest<Any>("lifecycle/hello/world?id=1"), userName, password)) {
                assertEquals(HttpStatus.SC_OK, responseStatus)
                assertEquals("Hello 1 : world", body)
            }
            lifecycleRestResourceImpl.stop()
        }

        // Assert back to unavailable after stop/close
        with(client.call(GET, WebRequest<Any>("lifecycle/hello/world?id=1"), userName, password)) {
            assertEquals(HttpStatus.SC_SERVICE_UNAVAILABLE, responseStatus)
        }
    }
}
