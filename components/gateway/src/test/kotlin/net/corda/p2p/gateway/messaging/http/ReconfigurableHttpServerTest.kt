package net.corda.p2p.gateway.messaging.http

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.gateway.messaging.DynamicKeyStore
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import org.assertj.core.api.Assertions
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
import org.mockito.kotlin.whenever
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
    private val resourcesHolder = mock<ResourcesHolder>()
    private val address = InetSocketAddress("www.r3.com", 30)
    private val serverMock = mockConstruction(HttpServer::class.java)
    private val configuration = GatewayConfiguration(
        hostAddress = "www.r3.com",
        hostPort = 33,
        urlPath = "/",
        sslConfig = mock(),
        maxRequestSize = 1000
    )
    private val badConfigurationException = RuntimeException("Bad Config")
    private val badConfiguration = mock<GatewayConfiguration> {
        on { hostPort } doThrow(badConfigurationException)
    }

    private lateinit var configHandler: ReconfigurableHttpServer.ReconfigurableHttpServerConfigChangeHandler
    private val dominoTile = mockConstruction(ComplexDominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        configHandler = (context.arguments()[6] as ReconfigurableHttpServer.ReconfigurableHttpServerConfigChangeHandler)
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
    }

    private val dynamicKeyStore = mockConstruction(DynamicKeyStore::class.java) { mock, _ ->
        val mockDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        whenever(mock.dominoTile).doReturn(mockDominoTile)
    }

    private val server = ReconfigurableHttpServer(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        listener,
        mock(),
        mock(),
        mock()
    )

    @AfterEach
    fun cleanUp() {
        serverMock.close()
        dominoTile.close()
        dynamicKeyStore.close()
    }

    @Test
    fun `writeResponse will throw an exception if server is not ready`() {
        assertThrows<IllegalStateException> {
            server.writeResponse(HttpResponseStatus.CREATED, address)
        }
    }

    @Test
    fun `writeResponse will write to server if ready`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder)

        server.writeResponse(HttpResponseStatus.CREATED, address)

        verify(serverMock.constructed().first()).write(HttpResponseStatus.CREATED, ByteArray(0), address)
    }

    @Test
    fun `applyNewConfiguration will start a new server`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder)

        verify(serverMock.constructed().first()).start()
    }

    @Test
    fun `applyNewConfiguration sets configApplied`() {
        val future = configHandler.applyNewConfiguration(configuration, null, resourcesHolder)

        Assertions.assertThat(future.isDone).isTrue
        Assertions.assertThat(future.isCompletedExceptionally).isFalse
    }

    @Test
    fun `applyNewConfiguration sets configApplied if bad config`() {
        val future = configHandler.applyNewConfiguration(badConfiguration, null, resourcesHolder)

        Assertions.assertThat(future.isDone).isTrue
        Assertions.assertThat(future.isCompletedExceptionally).isTrue
    }

    @Test
    fun `applyNewConfiguration will stop the previous server`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder)
        configHandler.applyNewConfiguration(configuration.copy(hostAddress = "aaa"), configuration, resourcesHolder)

        verify(serverMock.constructed().first()).close()
        verify(serverMock.constructed()[1]).start()
    }

    @Test
    fun `applyNewConfiguration will stop the previous server in different port`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder)
        configHandler.applyNewConfiguration(configuration.copy(hostPort = 13), configuration, resourcesHolder)

        verify(serverMock.constructed().first()).close()
        verify(serverMock.constructed()[1]).start()
    }

    @Test
    fun `applyNewConfiguration keeps the sever in the resource holder`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder)

        verify(resourcesHolder).keep(serverMock.constructed().last())
    }

    @Test
    fun `applyNewConfiguration creates new key store`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder)

        verify(dynamicKeyStore.constructed().first()).serverKeyStore
    }
}
