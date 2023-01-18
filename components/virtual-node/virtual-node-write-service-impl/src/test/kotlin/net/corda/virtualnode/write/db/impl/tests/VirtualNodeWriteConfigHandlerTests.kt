package net.corda.virtualnode.write.db.impl.tests

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.REST_CONFIG
import net.corda.schema.configuration.MessagingConfig.Bus.KAFKA_BOOTSTRAP_SERVERS
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.VirtualNodeWriteEventHandler
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriter
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriterFactory
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
            whenever(hasPath(KAFKA_BOOTSTRAP_SERVERS)).thenReturn(true)
            whenever(withFallback(any())).thenReturn(this)
        }

        val coordinator = mock<LifecycleCoordinator>()
        val vnodeWriter = mock<VirtualNodeWriter>()
        val vnodeWriterFactory = mock<VirtualNodeWriterFactory>().apply {
            whenever(create(config)).thenReturn(vnodeWriter)
        }

        val eventHandler = VirtualNodeWriteEventHandler(mock(), vnodeWriterFactory)
        val event = ConfigChangedEvent(
            setOf(REST_CONFIG, MESSAGING_CONFIG),
            mapOf(REST_CONFIG to config, BOOT_CONFIG to config, MESSAGING_CONFIG to config)
        )
        eventHandler.processEvent(event, coordinator)

        verify(vnodeWriterFactory).create(config)
        verify(vnodeWriter).start()
        verify(coordinator).updateStatus(UP)
    }

    @Test
    fun `sets coordinator to down and throws if virtual node writer cannot be created`() {
        val coordinator = mock<LifecycleCoordinator>()
        val vnodeWriterFactory = mock<VirtualNodeWriterFactory>().apply {
            whenever(create(any())).thenAnswer { throw IllegalStateException() }
        }

        val eventHandler = VirtualNodeWriteEventHandler(mock(), vnodeWriterFactory)
        val config = mock<SmartConfig>().apply {
            whenever(hasPath(KAFKA_BOOTSTRAP_SERVERS)).thenReturn(true)
            whenever(withFallback(any())).thenReturn(this)
        }

        val e = assertThrows<VirtualNodeWriteServiceException> {
            val event = ConfigChangedEvent(
                setOf(REST_CONFIG, MESSAGING_CONFIG),
                mapOf(REST_CONFIG to config, BOOT_CONFIG to config, MESSAGING_CONFIG to config)
            )
            eventHandler.processEvent(event, coordinator)
        }

        verify(coordinator).updateStatus(ERROR)
        assertEquals(
            "Could not start the virtual node writer for handling virtual node creation requests.",
            e.message
        )
    }

    @Test
    fun `sets status to UP if VirtualNodeRPCOps is running`() {
        val coordinator = mock<LifecycleCoordinator>()
        val vnodeWriterFactory = mock<VirtualNodeWriterFactory>().apply {
            whenever(create(any())).thenReturn(mock())
        }
        val eventHandler = VirtualNodeWriteEventHandler(mock(), vnodeWriterFactory)

        val config = mock<SmartConfig>().apply {
            whenever(hasPath(KAFKA_BOOTSTRAP_SERVERS)).thenReturn(true)
            whenever(withFallback(any())).thenReturn(this)
        }

        val event = ConfigChangedEvent(
            setOf(REST_CONFIG, MESSAGING_CONFIG),
            mapOf(REST_CONFIG to config, BOOT_CONFIG to config, MESSAGING_CONFIG to config)
        )

        eventHandler.processEvent(event, coordinator)

        verify(coordinator).updateStatus(UP)
    }
}
