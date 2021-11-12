package net.corda.p2p.gateway.messaging.http

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture

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
    private val future = mock<CompletableFuture<Unit>>()
    private val resourcesHolder = mock<ResourcesHolder>()
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
    private val badConfigurationException = RuntimeException("Bad Config")
    private val badConfiguration = mock<GatewayConfiguration> {
        on { hostPort } doThrow(badConfigurationException)
    }

    private lateinit var configHandler: ReconfigurableHttpServer.ReconfigurableHttpServerConfigChangeHandler
    private val dominoTile = mockConstruction(DominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        configHandler = (context.arguments()[4] as ReconfigurableHttpServer.ReconfigurableHttpServerConfigChangeHandler)
    }

    private val server = ReconfigurableHttpServer(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        listener
    )

    @AfterEach
    fun cleanUp() {
        serverMock.close()
        dominoTile.close()
    }

    @Test
    fun `writeResponse will throw an exception if server is not ready`() {
        assertThrows<IllegalStateException> {
            server.writeResponse(HttpResponseStatus.CREATED, address)
        }
    }

    @Test
    fun `writeResponse will write to server if ready`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder, future)

        server.writeResponse(HttpResponseStatus.CREATED, address)

        verify(serverMock.constructed().first()).write(HttpResponseStatus.CREATED, ByteArray(0), address)
    }

    @Test
    fun `applyNewConfiguration will start a new server`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder, future)

        verify(serverMock.constructed().first()).start()
    }

    @Test
    fun `applyNewConfiguration sets configApplied`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder, future)

        verify(future).complete(null)
    }

    @Test
    fun `applyNewConfiguration sets configApplied if bad config`() {
        configHandler.applyNewConfiguration(badConfiguration, null, resourcesHolder, future)

        verify(future).completeExceptionally(badConfigurationException)
    }

    @Test
    fun `applyNewConfiguration will stop the previous server`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder, future)
        configHandler.applyNewConfiguration(configuration.copy(hostAddress = "aaa"), configuration, resourcesHolder, future)

        verify(serverMock.constructed().first()).stop()
        verify(serverMock.constructed()[1]).start()
    }

    @Test
    fun `applyNewConfiguration will stop the previous server in different port`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder, future)
        configHandler.applyNewConfiguration(configuration.copy(hostPort = 13), configuration, resourcesHolder, future)

        verify(serverMock.constructed().first()).stop()
        verify(serverMock.constructed()[1]).start()
    }

    @Test
    fun `applyNewConfiguration keeps the sever in the resource holder`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder, future)

        verify(resourcesHolder).keep(serverMock.constructed().last())
    }
}
