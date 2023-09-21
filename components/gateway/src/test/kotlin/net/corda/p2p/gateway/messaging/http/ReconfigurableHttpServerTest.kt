package net.corda.p2p.gateway.messaging.http

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
import net.corda.p2p.gateway.messaging.GatewayServerConfiguration
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.TlsType
import net.corda.p2p.gateway.messaging.internal.CommonComponents
import net.corda.p2p.gateway.messaging.internal.RequestListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    private val listener = mock<RequestListener>()
    private val resourcesHolder = mock<ResourcesHolder>()
    private val serverAddress = InetSocketAddress("www.r3.com", 33)
    private var calledConfigurations = mutableListOf<GatewayServerConfiguration?>()
    private val serverMock = mockConstruction(HttpServer::class.java) { _, context ->
        val config = context.arguments()[2] as? GatewayServerConfiguration
        calledConfigurations.add(config)
    }
    private val serverConfiguration = GatewayServerConfiguration(
        hostAddress = serverAddress.hostName,
        hostPort = serverAddress.port,
        urlPath = "/",
    )
    private val configuration = GatewayConfiguration(
        serversConfiguration = listOf(
            serverConfiguration,
        ),
        sslConfig = SslConfiguration(
            revocationCheck = RevocationConfig(
                RevocationConfigMode.OFF
            ),
            TlsType.ONE_WAY,
        ),
        maxRequestSize = 1000
    )
    private val badConfigurationException = RuntimeException("Bad Config")
    private val badServerConfiguration = mock<GatewayServerConfiguration> {
        on { hostPort } doThrow(badConfigurationException)
    }
    private val badConfiguration = mock<GatewayConfiguration> {
        on { serversConfiguration } doReturn listOf(badServerConfiguration)
    }

    private lateinit var configHandler: ReconfigurableHttpServer.ReconfigurableHttpServerConfigChangeHandler
    private val dominoTile = mockConstruction(ComplexDominoTile::class.java) { mock, context ->
        configHandler = (context.arguments()[6] as ReconfigurableHttpServer.ReconfigurableHttpServerConfigChangeHandler)
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
    }
    private val commonComponentsDominoTile = mock<ComplexDominoTile> {
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
    }
    private val dynamicKeyStore = mock<DynamicKeyStore>()
    private val commonComponents = mock<CommonComponents> {
        on { dominoTile } doReturn commonComponentsDominoTile
        on { dynamicKeyStore } doReturn dynamicKeyStore
        on { trustStoresMap } doReturn mock()
    }

    @BeforeEach
    fun setUp() {
        ReconfigurableHttpServer(
            lifecycleCoordinatorFactory,
            configurationReaderService,
            listener,
            commonComponents,
            mock(),
        )
    }

    @AfterEach
    fun cleanUp() {
        serverMock.close()
        dominoTile.close()
    }

    @Test
    fun `applyNewConfiguration will start a new server`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder)

        verify(serverMock.constructed().first()).start()
    }

    @Test
    fun `applyNewConfiguration sets configApplied`() {
        val future = configHandler.applyNewConfiguration(configuration, null, resourcesHolder)

        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isFalse
    }

    @Test
    fun `applyNewConfiguration sets configApplied if bad config`() {
        val future = configHandler.applyNewConfiguration(badConfiguration, null, resourcesHolder)

        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isTrue
    }

    @Test
    fun `applyNewConfiguration will stop the previous server`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder)
        val servers = configuration.serversConfiguration.map { it.copy(hostAddress = "aaa") }
        configHandler.applyNewConfiguration(configuration.copy(serversConfiguration = servers), configuration, resourcesHolder)

        verify(serverMock.constructed().first()).close()
        verify(serverMock.constructed()[1]).start()
    }

    @Test
    fun `applyNewConfiguration will stop the previous server in the same address`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder)
        val servers = configuration.serversConfiguration.map { it.copy(urlPaths = setOf("/tests")) }
        configHandler.applyNewConfiguration(configuration.copy(serversConfiguration = servers), configuration, resourcesHolder)

        verify(serverMock.constructed().first()).close()
        verify(serverMock.constructed()[1]).start()
    }

    @Test
    fun `applyNewConfiguration will stop the previous server in different port`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder)
        val servers = configuration.serversConfiguration.map { it.copy(hostPort = 13) }
        configHandler.applyNewConfiguration(configuration.copy(serversConfiguration = servers), configuration, resourcesHolder)

        verify(serverMock.constructed().first()).close()
        verify(serverMock.constructed()[1]).start()
    }

    @Test
    fun `applyNewConfiguration keeps the severs in the resource holder`() {
        val closeable = argumentCaptor<AutoCloseable>()
        whenever(resourcesHolder.keep(closeable.capture())).doAnswer { }

        configHandler.applyNewConfiguration(configuration, null, resourcesHolder)

        closeable.firstValue.close()
        verify(serverMock.constructed().last()).close()
    }

    @Test
    fun `applyNewConfiguration creates new key store`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder)

        verify(dynamicKeyStore).serverKeyStore
    }

    @Test
    fun `applyNewConfiguration will throw an error if there are no servers defined`() {
        val future =
            configHandler.applyNewConfiguration(
                configuration.copy(serversConfiguration = emptyList()),
                null,
                resourcesHolder
            )

        assertThat(future).isCompletedExceptionally
    }

    @Test
    fun `applyNewConfiguration if there is more than one server that is using the same host and port will merge the configurations`() {
        configHandler.applyNewConfiguration(
            configuration.copy(
                serversConfiguration = configuration.serversConfiguration
                    + configuration.serversConfiguration.first().copy(urlPaths = setOf("/test"))
                    + configuration.serversConfiguration.first().copy(hostAddress = "0.0.0.0")
                    + configuration.serversConfiguration.first().copy(hostPort = 1000)
            ),
            null,
            resourcesHolder
        )

        assertThat(serverMock.constructed()).hasSize(3)
    }

    @Test
    fun `applyNewConfiguration with more than one path for the same host and port will be merged`() {
        val paths = (1..5).map {
            "/test/$it"
        }
        val configs = paths.map {
            configuration.serversConfiguration.first().copy(urlPaths = setOf(it))
        }
        configHandler.applyNewConfiguration(
            configuration.copy(
                serversConfiguration = configs
            ),
            null,
            resourcesHolder
        )

        assertThat(calledConfigurations.firstOrNull()?.urlPaths).containsExactlyElementsOf(paths)
    }

    @Test
    fun `applyNewConfiguration will not fail if there are duplicates in the host and port`() {
        val future =
            configHandler.applyNewConfiguration(
                configuration.copy(
                    serversConfiguration = listOf(
                        GatewayServerConfiguration(
                            hostAddress = serverAddress.hostName,
                            hostPort = serverAddress.port,
                            urlPath = "/",
                        ),
                        GatewayServerConfiguration(
                            hostAddress = serverAddress.hostName,
                            hostPort = serverAddress.port + 1,
                            urlPath = "/",
                        ),
                        GatewayServerConfiguration(
                            hostAddress = serverAddress.hostName,
                            hostPort = serverAddress.port + 2,
                            urlPath = "/",
                        ),
                        GatewayServerConfiguration(
                            hostAddress = serverAddress.hostName + ".net",
                            hostPort = serverAddress.port,
                            urlPath = "/",
                        ),
                    ),
                ),
                null,
                resourcesHolder
            )

        assertThat(future).isCompletedWithValue(Unit)
    }
}
