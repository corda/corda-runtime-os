package net.corda.p2p.gateway.messaging.http

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.net.InetSocketAddress

class ReconfigurableHttpServerTest {
    private val coordinatorHandler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { postEvent(any()) } doAnswer {
            coordinatorHandler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
    }
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), coordinatorHandler.capture()) } doReturn coordinator
    }
    private val configurationReaderService = mock<ConfigurationReadService>()
    private val listener = mock<HttpServerListener>()
    private val address = InetSocketAddress("www.r3.com", 30)
    private val serverMock = mockConstruction(HttpServer::class.java)
    private val configuration = GatewayConfiguration(
        hostAddress = "www.r3.com",
        hostPort = 33,
        sslConfig = mock {
            on { keyStore } doReturn mock()
            on { keyStorePassword } doReturn "hi"
        }
    )

    private val server = ReconfigurableHttpServer(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        listener
    )

    @AfterEach
    fun cleanUp() {
        serverMock.close()
    }

    @Test
    fun `writeResponse will throw an exception if server is not ready`() {
        assertThrows<IllegalStateException> {
            server.writeResponse(HttpResponseStatus.CREATED, address)
        }
    }

    @Test
    fun `writeResponse will write to server if ready`() {
        server.applyNewConfiguration(configuration, null)

        server.writeResponse(HttpResponseStatus.CREATED, address)

        verify(serverMock.constructed().first()).write(HttpResponseStatus.CREATED, ByteArray(0), address)
    }

    @Test
    fun `applyNewConfiguration will start a new server`() {
        server.applyNewConfiguration(configuration, null)

        verify(serverMock.constructed().first()).start()
    }

    @Test
    fun `applyNewConfiguration will stop the previous server`() {
        server.applyNewConfiguration(configuration, null)
        server.applyNewConfiguration(configuration.copy(hostAddress = "aaa"), configuration)

        verify(serverMock.constructed().first()).stop()
        verify(serverMock.constructed()[1]).start()
    }

    @Test
    fun `applyNewConfiguration will stop the previous server in different port`() {
        server.applyNewConfiguration(configuration, null)
        server.applyNewConfiguration(configuration.copy(hostPort = 13), configuration)

        verify(serverMock.constructed().first()).stop()
        verify(serverMock.constructed()[1]).start()
    }

    @Test
    fun `stop will stop the server`() {
        server.applyNewConfiguration(configuration, null)
        server.stop()

        verify(serverMock.constructed().first()).stop()
    }
}
