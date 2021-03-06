package net.corda.configuration.rpcops.impl

import net.corda.configuration.rpcops.ConfigRPCOpsServiceException
import net.corda.configuration.rpcops.impl.v1.ConfigRPCOpsInternal
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.RPC_CONFIG
import net.corda.schema.configuration.ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS
import net.corda.schema.configuration.MessagingConfig.Bus.KAFKA_BOOTSTRAP_SERVERS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests of [ConfigRPCOpsConfigHandler]. */
class ConfigRPCOpsConfigHandlerTests {

    @Test
    fun `sets RPC sender timeout if corresponding config is provided under RPC config`() {
        val timeout = 999
        val configRPCOps = mock<ConfigRPCOpsInternal>()
        val config = mock<SmartConfig>().apply {
            whenever(hasPath(RPC_ENDPOINT_TIMEOUT_MILLIS)).thenReturn(true)
            whenever(getInt(RPC_ENDPOINT_TIMEOUT_MILLIS)).thenReturn(timeout)
            whenever(withFallback(any())).thenReturn(this)
        }
        val configHandler = ConfigRPCOpsConfigHandler(mock(), configRPCOps)

        configHandler.onNewConfiguration(setOf(RPC_CONFIG, MESSAGING_CONFIG),
            mapOf(RPC_CONFIG to config, BOOT_CONFIG to config, MESSAGING_CONFIG to config))

        verify(configRPCOps).setTimeout(timeout)
    }

    @Test
    fun `does not throw if timeout config is not provided under RPC config`() {
        val configRPCOps = mock<ConfigRPCOpsInternal>()
        val configHandler = ConfigRPCOpsConfigHandler(mock(), configRPCOps)

        val config = mock<SmartConfig>().apply {
            whenever(withFallback(any())).thenReturn(this)
        }

        assertDoesNotThrow {
            configHandler.onNewConfiguration(
                setOf(RPC_CONFIG, MESSAGING_CONFIG),
                mapOf(RPC_CONFIG to config,
                    BOOT_CONFIG to config,
                    MESSAGING_CONFIG to config)
            )
        }
    }

    @Test
    fun `creates RPC sender if RPC config is provided`() {
        val configRPCOps = mock<ConfigRPCOpsInternal>()
        val config = mock<SmartConfig>().apply {
            whenever(hasPath(KAFKA_BOOTSTRAP_SERVERS)).thenReturn(true)
            whenever(withFallback(any())).thenReturn(this)
        }
        val configHandler = ConfigRPCOpsConfigHandler(mock(), configRPCOps)
        configHandler.onNewConfiguration(setOf(RPC_CONFIG, MESSAGING_CONFIG),
            mapOf(RPC_CONFIG to config, BOOT_CONFIG to config, MESSAGING_CONFIG to config))

        verify(configRPCOps).createAndStartRPCSender(config)
    }

    @Test
    fun `sets coordinator to down and throws if RPC sender cannot be created`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configRPCOps = mock<ConfigRPCOpsInternal>().apply {
            whenever(createAndStartRPCSender(any())).thenAnswer { throw IllegalStateException() }
        }
        val config = mock<SmartConfig>().apply {
            whenever(withFallback(any())).thenReturn(this)
        }
        val configHandler = ConfigRPCOpsConfigHandler(coordinator, configRPCOps)

        val e = assertThrows<ConfigRPCOpsServiceException> {
            configHandler.onNewConfiguration(
                setOf(RPC_CONFIG, MESSAGING_CONFIG),
                mapOf(RPC_CONFIG to config, BOOT_CONFIG to config, MESSAGING_CONFIG to config)
            )
        }

        verify(coordinator).updateStatus(ERROR)
        assertEquals(
            "Could not start the RPC sender for incoming HTTP RPC configuration management requests",
            e.message
        )
    }

    @Test
    fun `does not throw if RPC sender config is not provided under RPC config`() {
        val configRPCOps = mock<ConfigRPCOpsInternal>()
        val config = mock<SmartConfig>().apply {
            whenever(withFallback(any())).thenReturn(this)
        }

        val configHandler = ConfigRPCOpsConfigHandler(mock(), configRPCOps)

        assertDoesNotThrow {
            configHandler.onNewConfiguration(
                setOf(RPC_CONFIG, MESSAGING_CONFIG),
                mapOf(RPC_CONFIG to config, BOOT_CONFIG to config, MESSAGING_CONFIG to config)
            )
        }
    }

    @Test
    fun `sets status to UP if ConfigRPCOps is running`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configRPCOps = mock<ConfigRPCOpsInternal>().apply {
            whenever(isRunning).thenReturn(true)
        }
        val configHandler = ConfigRPCOpsConfigHandler(coordinator, configRPCOps)

        configHandler.onNewConfiguration(emptySet(), emptyMap())

        verify(coordinator).updateStatus(UP)
    }
}