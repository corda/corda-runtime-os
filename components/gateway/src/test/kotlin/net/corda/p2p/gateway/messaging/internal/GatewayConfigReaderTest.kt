package net.corda.p2p.gateway.messaging.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.SslConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.mock

class GatewayConfigReaderTest {

    private var configChangeHandler: ConfigurationChangeHandler<GatewayConfiguration>? = null

    private val domino = mockConstruction(ComplexDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        configChangeHandler = context.arguments()[6] as ConfigurationChangeHandler<GatewayConfiguration>?
    }
    private val coordinatorFactory = mock(LifecycleCoordinatorFactory::class.java)
    private val configReadService = mock(ConfigurationReadService::class.java)

    @AfterEach
    fun cleanup() {
        domino.close()
    }

    @Test
    fun `configuration handler processes new, valid configuration successfully`() {
        val gatewayConfigReader = GatewayConfigReader(coordinatorFactory, configReadService)
        val connectionConfig = ConnectionConfiguration().copy(maxClientConnections = 1)
        val sslConfiguration = mock<SslConfiguration>()
        val gatewayConfig = GatewayConfiguration("", 1, "/", sslConfiguration, 1000, connectionConfig)

        val future = configChangeHandler!!.applyNewConfiguration(gatewayConfig, null, mock())
        assertThat(future.isDone).isTrue
        assertThat(gatewayConfigReader.connectionConfig.maxClientConnections).isEqualTo(1)
        assertThat(gatewayConfigReader.sslConfiguration).isEqualTo(sslConfiguration)
    }

    @Test
    fun `sslConfiguration is null by default`() {
        val gatewayConfigReader = GatewayConfigReader(coordinatorFactory, configReadService)

        assertThat(gatewayConfigReader.sslConfiguration).isNull()
    }

    @Test
    fun `configuration handler returns no change if configuration is the same as before`() {
        val gatewayConfigReader = GatewayConfigReader(coordinatorFactory, configReadService)
        val gatewayConfig = GatewayConfiguration("", 1, "/", mock(), 1000, gatewayConfigReader.connectionConfig)

        val future = configChangeHandler!!.applyNewConfiguration(gatewayConfig, null, mock())
        assertThat(future.isDone).isTrue
    }

}