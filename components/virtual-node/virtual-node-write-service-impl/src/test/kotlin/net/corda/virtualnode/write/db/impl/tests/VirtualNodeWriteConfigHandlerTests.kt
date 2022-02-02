package net.corda.virtualnode.write.db.impl.tests

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.virtualnode.write.VirtualNodeWriter
import net.corda.libs.virtualnode.write.VirtualNodeWriterFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.schema.configuration.ConfigKeys.BOOTSTRAP_SERVERS
import net.corda.schema.configuration.ConfigKeys.RPC_CONFIG
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.VirtualNodeWriteConfigHandler
import net.corda.virtualnode.write.db.impl.VirtualNodeWriteEventHandler
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests of [VirtualNodeWriteConfigHandler]. */
class VirtualNodeWriteConfigHandlerTests {

    @Test
    fun `sets coordinator to up and creates and starts virtual node writer if RPC config is provided`() {
        val config = mock<SmartConfig>().apply {
            whenever(hasPath(BOOTSTRAP_SERVERS)).thenReturn(true)
        }

        val coordinator = mock<LifecycleCoordinator>()
        val vnodeWriter = mock<VirtualNodeWriter>()
        val vnodeWriterFactory = mock<VirtualNodeWriterFactory>().apply {
            whenever(create(config, 0)).thenReturn(vnodeWriter)
        }
        val configHandler = VirtualNodeWriteConfigHandler(mock(), coordinator, vnodeWriterFactory)

        configHandler.onNewConfiguration(setOf(RPC_CONFIG), mapOf(RPC_CONFIG to config))

        verify(vnodeWriterFactory).create(config, 0)
        verify(vnodeWriter).start()
        verify(coordinator).updateStatus(UP)
    }

    @Test
    fun `sets coordinator to down and throws if virtual node writer is already set`() {
        val eventHandler = mock<VirtualNodeWriteEventHandler>().apply {
            whenever(virtualNodeWriter).thenReturn(mock())
        }
        val configHandler = VirtualNodeWriteConfigHandler(eventHandler, mock(), mock())
        val config = mock<SmartConfig>().apply {
            whenever(hasPath(BOOTSTRAP_SERVERS)).thenReturn(true)
        }

        val e = assertThrows<VirtualNodeWriteServiceException> {
            configHandler.onNewConfiguration(setOf(RPC_CONFIG), mapOf(RPC_CONFIG to config))
        }

        assertEquals(
            "An attempt was made to initialise the virtual node writer twice.",
            e.message
        )
    }

    @Test
    fun `sets coordinator to down and throws if virtual node writer cannot be created`() {
        val coordinator = mock<LifecycleCoordinator>()
        val vnodeWriterFactory = mock<VirtualNodeWriterFactory>().apply {
            whenever(create(any(), any())).thenAnswer { throw IllegalStateException() }
        }
        val configHandler = VirtualNodeWriteConfigHandler(mock(), coordinator, vnodeWriterFactory)
        val config = mock<SmartConfig>().apply {
            whenever(hasPath(BOOTSTRAP_SERVERS)).thenReturn(true)
        }

        val e = assertThrows<VirtualNodeWriteServiceException> {
            configHandler.onNewConfiguration(setOf(RPC_CONFIG), mapOf(RPC_CONFIG to config))
        }

        verify(coordinator).updateStatus(ERROR)
        assertEquals(
            "Could not start the virtual node writer for handling virtual node creation requests.",
            e.message
        )
    }

    @Test
    fun `does not throw if RPC sender config is not provided under RPC config`() {
        val vnodeWriterFactory = mock<VirtualNodeWriterFactory>().apply {
            whenever(create(any(), any())).thenAnswer { throw IllegalStateException() }
        }
        val configHandler = VirtualNodeWriteConfigHandler(mock(), mock(), vnodeWriterFactory)

        assertDoesNotThrow {
            configHandler.onNewConfiguration(setOf(RPC_CONFIG), mapOf(RPC_CONFIG to mock()))
        }
    }

    @Test
    fun `sets status to UP if VirtualNodeRPCOps is running`() {
        val coordinator = mock<LifecycleCoordinator>()
        val vnodeWriterFactory = mock<VirtualNodeWriterFactory>().apply {
            whenever(create(any(), any())).thenReturn(mock())
        }
        val configHandler = VirtualNodeWriteConfigHandler(mock(), coordinator, vnodeWriterFactory)
        val config = mock<SmartConfig>().apply {
            whenever(hasPath(BOOTSTRAP_SERVERS)).thenReturn(true)
        }

        configHandler.onNewConfiguration(setOf(RPC_CONFIG), mapOf(RPC_CONFIG to config))

        verify(coordinator).updateStatus(UP)
    }
}