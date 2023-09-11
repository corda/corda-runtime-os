package net.corda.web.server

import io.javalin.Javalin
import java.lang.reflect.Field
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import net.corda.web.api.WebHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

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

    @BeforeEach
    fun setup() {
        javalinServer = JavalinServer(lifecycleCoordinatorFactory) { javalinMock }

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
        assertThrows<CordaRuntimeException> {
            javalinServer.start(port)
        }
    }

    @Test
    fun `registering an endpoint with improper endpoint string throws`() {
        javalinServer.start(port)

        assertThrows<CordaRuntimeException> {
            javalinServer.registerEndpoint(Endpoint(HTTPMethod.GET, "", webHandler))
        }
        assertThrows<CordaRuntimeException> {
            javalinServer.registerEndpoint(Endpoint(HTTPMethod.GET, "noslash", webHandler))
        }
        assertThrows<CordaRuntimeException> {
            javalinServer.registerEndpoint(Endpoint(HTTPMethod.GET, "not a url", webHandler))
        }
        assertDoesNotThrow {
            javalinServer.registerEndpoint(Endpoint(HTTPMethod.GET, "/url", webHandler))
        }
    }

    @Test
    fun `registering an endpoint should call the correct method on javalin`() {
        javalinServer.start(port)

        javalinServer.registerEndpoint(Endpoint(HTTPMethod.GET, "/url", webHandler))
        verify(javalinMock).get(eq("/url"), any())

        javalinServer.registerEndpoint(Endpoint(HTTPMethod.POST, "/url", webHandler))
        verify(javalinMock).post(eq("/url"), any())
    }

    @Test
    fun `registering an endpoint add it to the endpoints list`() {
        javalinServer.start(port)
        val getEndpoint = Endpoint(HTTPMethod.GET, "/url1", webHandler)
        val postEndpoint = Endpoint(HTTPMethod.POST, "/url2", webHandler)

        javalinServer.registerEndpoint(getEndpoint)
        javalinServer.registerEndpoint(postEndpoint)

        val endpoints = listCast(endpointsField.get(javalinServer) as MutableList<*>)

        assertEquals(2, endpoints.size)
        assertEquals(getEndpoint, endpoints[0])
        assertEquals(postEndpoint, endpoints[1])
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

        val endpoints = listCast(endpointsField.get(javalinServer) as MutableList<*>)
        assertEquals(1, endpoints.size)
        assertEquals(postEndpoint, endpoints[0])
    }

    @Suppress("UNCHECKED_CAST")
    private fun listCast(inputList: MutableList<*>): MutableList<Endpoint> {
        return inputList as? MutableList<Endpoint> ?: mutableListOf()
    }
}