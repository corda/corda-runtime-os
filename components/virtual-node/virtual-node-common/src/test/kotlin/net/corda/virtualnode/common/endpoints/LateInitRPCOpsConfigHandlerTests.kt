package net.corda.virtualnode.common.endpoints

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.schema.configuration.ConfigKeys.Companion.BOOTSTRAP_SERVERS
import net.corda.schema.configuration.ConfigKeys.Companion.RPC_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.RPC_ENDPOINT_TIMEOUT_MILLIS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration

/** Tests of [LateInitRPCOpsConfigHandler]. */
class LateInitRPCOpsConfigHandlerTests {
    @Test
    fun `throws if RPC config is listed as a changed key but no corresponding config is provided`() {
        val configHandler = LateInitRPCOpsConfigHandler(mock(), mock())
        val e = assertThrows<LateInitRPCOpsServiceException> {
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
        val configRPCOps = mock<LateInitRPCOps>()
        val config = mock<SmartConfig>().apply {
            whenever(hasPath(RPC_ENDPOINT_TIMEOUT_MILLIS)).thenReturn(true)
            whenever(getInt(RPC_ENDPOINT_TIMEOUT_MILLIS)).thenReturn(timeout)
        }
        val configHandler = LateInitRPCOpsConfigHandler(mock(), configRPCOps)

        configHandler.onNewConfiguration(setOf(RPC_CONFIG), mapOf(RPC_CONFIG to config))

        verify(configRPCOps).setRpcRequestTimeout(Duration.ofMillis(timeout.toLong()))
    }

    @Test
    fun `does not throw if timeout config is not provided under RPC config`() {
        val configRPCOps = mock<LateInitRPCOps>()
        val configHandler = LateInitRPCOpsConfigHandler(mock(), configRPCOps)

        assertDoesNotThrow {
            configHandler.onNewConfiguration(setOf(RPC_CONFIG), mapOf(RPC_CONFIG to mock()))
        }
    }

    @Test
    fun `creates RPC sender if RPC config is provided`() {
        val configRPCOps = mock<LateInitRPCOps>()
        val config = mock<SmartConfig>().apply {
            whenever(hasPath(BOOTSTRAP_SERVERS)).thenReturn(true)
        }
        val configHandler = LateInitRPCOpsConfigHandler(mock(), configRPCOps)
        configHandler.onNewConfiguration(setOf(RPC_CONFIG), mapOf(RPC_CONFIG to config))

        verify(configRPCOps).createAndStartRpcSender(config)
    }

    @Test
    fun `sets coordinator to down and throws if RPC sender cannot be created`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configRPCOps = mock<LateInitRPCOps>().apply {
            whenever(createAndStartRpcSender(any())).thenAnswer { throw IllegalStateException() }
        }
        val config = mock<SmartConfig>().apply {
            whenever(hasPath(BOOTSTRAP_SERVERS)).thenReturn(true)
        }
        val configHandler = LateInitRPCOpsConfigHandler(coordinator, configRPCOps)

        val e = assertThrows<LateInitRPCOpsServiceException> {
            configHandler.onNewConfiguration(setOf(RPC_CONFIG), mapOf(RPC_CONFIG to config))
        }

        verify(coordinator).updateStatus(ERROR)
        assertEquals(
            "Could not start the RPC sender for incoming HTTP RPC virtual node management requests",
            e.message
        )
    }

    @Test
    fun `does not throw if RPC sender config is not provided under RPC config`() {
        val configRPCOps = mock<LateInitRPCOps>()
        val configHandler = LateInitRPCOpsConfigHandler(mock(), configRPCOps)

        assertDoesNotThrow {
            configHandler.onNewConfiguration(setOf(RPC_CONFIG), mapOf(RPC_CONFIG to mock()))
        }
    }

    @Test
    fun `sets status to UP if VirtualNodeRPCOps is running`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configRPCOps = mock<LateInitRPCOps>().apply {
            whenever(isRunning).thenReturn(true)
        }
        val configHandler = LateInitRPCOpsConfigHandler(coordinator, configRPCOps)

        configHandler.onNewConfiguration(emptySet(), emptyMap())

        verify(coordinator).updateStatus(UP)
    }
}