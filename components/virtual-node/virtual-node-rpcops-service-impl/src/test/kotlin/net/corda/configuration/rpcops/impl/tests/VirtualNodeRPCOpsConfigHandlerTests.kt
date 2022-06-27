package net.corda.configuration.rpcops.impl.tests

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.RPC_CONFIG
import net.corda.schema.configuration.ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS
import net.corda.schema.configuration.MessagingConfig.Bus.KAFKA_BOOTSTRAP_SERVERS
import net.corda.virtualnode.rpcops.VirtualNodeRPCOpsServiceException
import net.corda.virtualnode.rpcops.impl.VirtualNodeRPCOpsEventHandler
import net.corda.virtualnode.rpcops.impl.v1.VirtualNodeRPCOpsInternal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests of config changes within the vnode rpcs ops service. */
class VirtualNodeRPCOpsConfigHandlerTests {

    @Test
    fun `sets RPC sender timeout if corresponding config is provided under RPC config`() {
        val timeout = 999
        val coordinator = mock<LifecycleCoordinator>()
        val configRPCOps = mock<VirtualNodeRPCOpsInternal>()
        val config = mock<SmartConfig>().apply {
            whenever(hasPath(RPC_ENDPOINT_TIMEOUT_MILLIS)).thenReturn(true)
            whenever(getInt(RPC_ENDPOINT_TIMEOUT_MILLIS)).thenReturn(timeout)
            whenever(withFallback(any())).thenReturn(this)
        }

        val eventHandler = VirtualNodeRPCOpsEventHandler(mock(), configRPCOps)

        val event = ConfigChangedEvent(
            setOf(RPC_CONFIG), mapOf(RPC_CONFIG to config, MESSAGING_CONFIG to config, BOOT_CONFIG to config)
        )
        eventHandler.processEvent(event, coordinator)

        verify(configRPCOps).setTimeout(timeout)
    }

    @Test
    fun `does not throw if timeout config is not provided under RPC config`() {
        val coordinator = mock<LifecycleCoordinator>()

        val configRPCOps = mock<VirtualNodeRPCOpsInternal>()
        val config = mock<SmartConfig>().apply {
            whenever(withFallback(any())).thenReturn(this)
        }
        val eventHandler = VirtualNodeRPCOpsEventHandler(mock(), configRPCOps)

        val event = ConfigChangedEvent(
            setOf(RPC_CONFIG), mapOf(RPC_CONFIG to config, MESSAGING_CONFIG to config, BOOT_CONFIG to config)
        )

        assertDoesNotThrow {
            eventHandler.processEvent(event, coordinator)
        }
    }

    @Test
    fun `creates RPC sender if RPC config is provided`() {
        val coordinator = mock<LifecycleCoordinator>()

        val configRPCOps = mock<VirtualNodeRPCOpsInternal>()
        val config = mock<SmartConfig>().apply {
            whenever(hasPath(KAFKA_BOOTSTRAP_SERVERS)).thenReturn(true)
            whenever(withFallback(any())).thenReturn(this)
        }
        val eventHandler = VirtualNodeRPCOpsEventHandler(mock(), configRPCOps)
        val event = ConfigChangedEvent(
            setOf(RPC_CONFIG), mapOf(RPC_CONFIG to config, MESSAGING_CONFIG to config, BOOT_CONFIG to config)
        )

        eventHandler.processEvent(event, coordinator)

        verify(configRPCOps).createAndStartRpcSender(any())
    }

    @Test
    fun `sets coordinator to down and throws if RPC sender cannot be created`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configRPCOps = mock<VirtualNodeRPCOpsInternal>().apply {
            whenever(createAndStartRpcSender(any())).thenAnswer { throw IllegalStateException() }
        }
        val config = mock<SmartConfig>().apply {
            whenever(hasPath(KAFKA_BOOTSTRAP_SERVERS)).thenReturn(true)
            whenever(withFallback(any())).thenReturn(this)
        }

        val eventHandler = VirtualNodeRPCOpsEventHandler(mock(), configRPCOps)
        val event = ConfigChangedEvent(
            setOf(RPC_CONFIG), mapOf(RPC_CONFIG to config, MESSAGING_CONFIG to config, BOOT_CONFIG to config)
        )

        val e = assertThrows<VirtualNodeRPCOpsServiceException> {
            eventHandler.processEvent(event, coordinator)
        }

        verify(coordinator).updateStatus(ERROR)
        assertEquals(
            "Could not start the RPC sender for incoming HTTP RPC virtual node management requests", e.message
        )
    }

    @Test
    fun `does not throw if RPC sender config is not provided under RPC config`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configRPCOps = mock<VirtualNodeRPCOpsInternal>()
        val config = mock<SmartConfig>().apply {
            whenever(withFallback(any())).thenReturn(this)
        }

        val eventHandler = VirtualNodeRPCOpsEventHandler(mock(), configRPCOps)
        val event = ConfigChangedEvent(
            setOf(RPC_CONFIG), mapOf(RPC_CONFIG to config, MESSAGING_CONFIG to config, BOOT_CONFIG to config)
        )

        assertDoesNotThrow {
            eventHandler.processEvent(event, coordinator)
        }
    }

    @Test
    fun `sets status to UP if VirtualNodeRPCOps is running`() {
        val coordinator = mock<LifecycleCoordinator>()
        val configRPCOps = mock<VirtualNodeRPCOpsInternal>().apply {
            whenever(isRunning).thenReturn(true)
        }

        val eventHandler = VirtualNodeRPCOpsEventHandler(mock(), configRPCOps)

        val event = ConfigChangedEvent(emptySet(), emptyMap())

        eventHandler.processEvent(event, coordinator)

        verify(coordinator).updateStatus(UP)
    }
}
