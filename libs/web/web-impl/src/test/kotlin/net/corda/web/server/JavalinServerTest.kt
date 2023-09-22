package net.corda.web.server

import io.javalin.Javalin
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import net.corda.web.api.WebHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.lang.reflect.Field

class JavalinServerTest {

    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) }.doReturn(lifecycleCoordinator)
    }

    private lateinit var javalinServer: JavalinServer
    private lateinit var endpointsField: Field
    private val javalinMock: Javalin = mock()

    private val port = 8888
    private val webHandler = WebHandler { context -> context }
    private val infoProviderMock = mock<PlatformInfoProvider> {
        on { localWorkerSoftwareShortVersion } doReturn ("1.2")
    }

    @BeforeEach
    fun setup() {
        javalinServer = JavalinServer(lifecycleCoordinatorFactory, { javalinMock }, infoProviderMock)

        endpointsField = JavalinServer::class.java.getDeclaredField("endpoints")
        endpointsField.isAccessible = true
    }

    @Test
    fun `starting the server should call start on Javalin`() {
        javalinServer.start(port)
        verify(javalinMock).start(port)
    }

    @Test
    fun `stopping the server should call stop on Javalin`() {
        javalinServer.start(port)
        javalinServer.stop()
        verify(javalinMock).stop()
    }

    @Test
    fun `starting an already started server should throw exception`() {
        javalinServer.start(port)
        assertThrows<IllegalStateException> {
            javalinServer.start(port)
        }
    }

    @Test
    fun `registering an endpoint should call the correct method on javalin`() {
        // start server so endpoints register immediately
        javalinServer.start(port)

        javalinServer.registerEndpoint(Endpoint(HTTPMethod.GET, "/url", webHandler))
        verify(javalinMock).get(eq("/url"), any())

        javalinServer.registerEndpoint(Endpoint(HTTPMethod.GET, "/foo", webHandler, true))
        verify(javalinMock).get(eq("/api/1.2/foo"), any())

        javalinServer.registerEndpoint(Endpoint(HTTPMethod.POST, "/url", webHandler))
        verify(javalinMock).post(eq("/url"), any())

        javalinServer.registerEndpoint(Endpoint(HTTPMethod.POST, "/foo", webHandler, true))
        verify(javalinMock).post(eq("/api/1.2/foo"), any())
    }

    @Test
    fun `register endpoints when the server is started`() {
        val endpoint = Endpoint(HTTPMethod.GET, "/url", webHandler)
        javalinServer.registerEndpoint(endpoint)
        //check it hasn't been registered yet
        verify(javalinMock, never()).get(eq("/url"), any())
        // but it's in the collection
        assertThat(javalinServer.endpoints).contains(endpoint)

        javalinServer.start(port)
        // now it is
        verify(javalinMock).get(eq("/url"), any())
    }

    @Test
    fun `register an endpoint with existing path and method throws`() {
        val endpoint = Endpoint(HTTPMethod.GET, "/url", webHandler)
        // same path and method, different handler
        val endpoint2 = Endpoint(HTTPMethod.GET, "/url", mock())
        javalinServer.registerEndpoint(endpoint)
        assertThrows<IllegalArgumentException> {
            javalinServer.registerEndpoint(endpoint2)
        }
    }

    @Test
    fun `register an endpoint with existing path and different method is valid`() {
        val endpoint = Endpoint(HTTPMethod.GET, "/url", webHandler)
        // same path and different method
        val endpoint2 = Endpoint(HTTPMethod.POST, "/url", mock())
        javalinServer.registerEndpoint(endpoint)
        assertDoesNotThrow {
            javalinServer.registerEndpoint(endpoint2)
        }
    }

    @Test
    fun `registering an endpoint add it to the endpoints list`() {
        javalinServer.start(port)
        val getEndpoint = Endpoint(HTTPMethod.GET, "/url1", webHandler)
        val postEndpoint = Endpoint(HTTPMethod.POST, "/url2", webHandler)

        javalinServer.registerEndpoint(getEndpoint)
        javalinServer.registerEndpoint(postEndpoint)

        assertEquals(2, javalinServer.endpoints.size)
        assertEquals(getEndpoint, javalinServer.endpoints.elementAt(0))
        assertEquals(postEndpoint, javalinServer.endpoints.elementAt(1))
    }

    @Test
    fun `unregistering an endpoint removes it from the endpoints list and restarts the server`() {
        javalinServer.start(port)

        val getEndpoint = Endpoint(HTTPMethod.GET, "/url1", webHandler)
        val postEndpoint = Endpoint(HTTPMethod.POST, "/url2", webHandler)

        javalinServer.registerEndpoint(getEndpoint)
        javalinServer.registerEndpoint(postEndpoint)

        javalinServer.removeEndpoint(getEndpoint)

        verify(javalinMock).stop()
        verify(javalinMock).start(port)

        val endpoints = javalinServer.endpoints
        assertEquals(1, endpoints.size)
        assertEquals(postEndpoint, endpoints.elementAt(0))
    }

    @Test
    fun `unregistering an endpoint when server not started just removes it from the endpoints list`() {
        val getEndpoint = Endpoint(HTTPMethod.GET, "/url1", webHandler)
        val postEndpoint = Endpoint(HTTPMethod.POST, "/url2", webHandler)

        javalinServer.registerEndpoint(getEndpoint)
        javalinServer.registerEndpoint(postEndpoint)

        javalinServer.removeEndpoint(getEndpoint)

        verify(javalinMock, never()).stop()
        verify(javalinMock, never()).start(port)

        val endpoints = javalinServer.endpoints
        assertEquals(1, endpoints.size)
        assertEquals(postEndpoint, endpoints.elementAt(0))
    }

    @Test
    fun `unregistering a non-existing endpoint does nothing`() {
        val getEndpoint = Endpoint(HTTPMethod.GET, "/url1", webHandler)
        val postEndpoint = Endpoint(HTTPMethod.POST, "/url2", webHandler)

        javalinServer.registerEndpoint(postEndpoint)

        javalinServer.removeEndpoint(getEndpoint)

        val endpoints = javalinServer.endpoints
        assertEquals(1, endpoints.size)
        assertEquals(postEndpoint, endpoints.elementAt(0))
    }
}