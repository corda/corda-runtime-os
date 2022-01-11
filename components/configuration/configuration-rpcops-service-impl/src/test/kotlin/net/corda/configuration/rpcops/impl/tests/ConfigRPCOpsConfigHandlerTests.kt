package net.corda.configuration.rpcops.impl.tests

import net.corda.configuration.rpcops.ConfigRPCOpsServiceException
import net.corda.configuration.rpcops.impl.CONFIG_KEY_CONFIG_RPC_TIMEOUT_MILLIS
import net.corda.configuration.rpcops.impl.ConfigRPCOpsConfigHandler
import net.corda.configuration.rpcops.impl.v1.ConfigRPCOps
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.schema.configuration.ConfigKeys.Companion.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.RPC_CONFIG
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
    fun `throws if boot config is listed as a changed key but no corresponding config is provided`() {
        val configHandler = ConfigRPCOpsConfigHandler(mock(), mock())
        val e = assertThrows<ConfigRPCOpsServiceException> {
            configHandler.onNewConfiguration(setOf(BOOT_CONFIG), emptyMap())
        }

        assertEquals(
            "Was notified of an update to configuration key $BOOT_CONFIG, but no such configuration was found.",
            e.message
        )
    }

    @Test
    fun `creates RPC sender if boot config is provided`() {
        val configRPCOps = mock<ConfigRPCOps>()
        val config = mock<SmartConfig>()
        val configHandler = ConfigRPCOpsConfigHandler(mock(), configRPCOps)
        configHandler.onNewConfiguration(setOf(BOOT_CONFIG), mapOf(BOOT_CONFIG to config))

        verify(configRPCOps).createAndStartRPCSender(config)
    }

    @Test
    fun `sets coordinator to down and throws if RPC sender cannot be created`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configRPCOps = mock<ConfigRPCOps>().apply {
            whenever(createAndStartRPCSender(any())).thenAnswer { throw IllegalStateException() }
        }
        val configHandler = ConfigRPCOpsConfigHandler(coordinator, configRPCOps)

        val e = assertThrows<ConfigRPCOpsServiceException> {
            configHandler.onNewConfiguration(setOf(BOOT_CONFIG), mapOf(BOOT_CONFIG to mock()))
        }

        verify(coordinator).updateStatus(ERROR)
        assertEquals(
            "Could not start the RPC sender for incoming HTTP RPC configuration management requests",
            e.message
        )
    }

    @Test
    fun `throws if RPC config is listed as a changed key but no corresponding config is provided`() {
        val configHandler = ConfigRPCOpsConfigHandler(mock(), mock())
        val e = assertThrows<ConfigRPCOpsServiceException> {
            configHandler.onNewConfiguration(setOf(RPC_CONFIG), emptyMap())
        }

        assertEquals(
            "Was notified of an update to configuration key $RPC_CONFIG, but no such configuration was found.",
            e.message
        )
    }

    @Test
    fun `sets RPC sender timeout if corresponding config is provided under RPC config`() {
        val timeout = 999
        val configRPCOps = mock<ConfigRPCOps>()
        val config = mock<SmartConfig>().apply {
            whenever(getInt(CONFIG_KEY_CONFIG_RPC_TIMEOUT_MILLIS)).thenReturn(timeout)
        }
        val configHandler = ConfigRPCOpsConfigHandler(mock(), configRPCOps)

        configHandler.onNewConfiguration(setOf(RPC_CONFIG), mapOf(RPC_CONFIG to config))

        verify(configRPCOps).setTimeout(timeout)
    }

    @Test
    fun `does not throw if timeout config is not provided under RPC config`() {
        val configRPCOps = mock<ConfigRPCOps>()
        val configHandler = ConfigRPCOpsConfigHandler(mock(), configRPCOps)

        assertDoesNotThrow {
            configHandler.onNewConfiguration(setOf(RPC_CONFIG), mapOf(RPC_CONFIG to mock()))
        }
    }

    @Test
    fun `sets status to UP if ConfigRPCOps is running`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configRPCOps = mock<ConfigRPCOps>().apply {
            whenever(isRunning).thenReturn(true)
        }
        val configHandler = ConfigRPCOpsConfigHandler(coordinator, configRPCOps)

        configHandler.onNewConfiguration(emptySet(), emptyMap())

        verify(coordinator).updateStatus(UP)
    }
}