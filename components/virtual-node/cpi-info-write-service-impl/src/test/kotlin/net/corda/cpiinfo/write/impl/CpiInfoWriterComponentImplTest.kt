package net.corda.cpiinfo.write.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

internal class CpiInfoWriterComponentImplTest {
    @Test
    fun `messaging config received`() {
        val coordinator = mock<LifecycleCoordinator>()
        val coordinatorFactory = mock<LifecycleCoordinatorFactory>().also {
            whenever(it.createCoordinator(any(), any())).doReturn(coordinator)
        }

        val config = mock<SmartConfig>().apply {
            whenever(withFallback(any())).thenReturn(this)
        }

        val writer = CpiInfoWriterComponentImpl(coordinatorFactory, mock(), mock())
        val event = ConfigChangedEvent(
            setOf(ConfigKeys.MESSAGING_CONFIG),
            mapOf(
                ConfigKeys.REST_CONFIG to config,
                ConfigKeys.MESSAGING_CONFIG to config,
                ConfigKeys.BOOT_CONFIG to config
            )
        )
        writer.processEvent(event, coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `messaging config not received`() {
        val coordinator = mock<LifecycleCoordinator>()
        val coordinatorFactory = mock<LifecycleCoordinatorFactory>().also {
            whenever(it.createCoordinator(any(), any())).doReturn(coordinator)
        }

        val writer = CpiInfoWriterComponentImpl(coordinatorFactory, mock(), mock())
        val event = ConfigChangedEvent(emptySet(), emptyMap())
        writer.processEvent(event, coordinator)

        verify(coordinator, times(0)).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `put and remove`() {
        val coordinator = mock<LifecycleCoordinator>()
        val coordinatorFactory = mock<LifecycleCoordinatorFactory>().also {
            whenever(it.createCoordinator(any(), any())).doReturn(coordinator)
        }

        val config = mock<SmartConfig>().apply {
            whenever(withFallback(any())).thenReturn(this)
        }

        val publisher = mock<Publisher>()
        val publisherFactory = mock<PublisherFactory>().also {
            whenever(it.createPublisher(any(), any())).thenReturn(publisher)
        }
        val writer = CpiInfoWriterComponentImpl(coordinatorFactory, mock(), publisherFactory)
        val event = ConfigChangedEvent(
            setOf(ConfigKeys.MESSAGING_CONFIG),
            mapOf(
                ConfigKeys.REST_CONFIG to config,
                ConfigKeys.MESSAGING_CONFIG to config,
                ConfigKeys.BOOT_CONFIG to config
            )
        )
        writer.processEvent(event, coordinator)
        verify(coordinator).updateStatus(LifecycleStatus.UP)

        val cpiIdentifier = CpiIdentifier("test", "1.0", SecureHash.parse("ALGO:1234567890"))
        val cpiMetadata = CpiMetadata(
            cpiIdentifier,
            SecureHash.parse("ALGO:0987654321"),
            emptyList(),
            "",
            0,
            Instant.now()
        )

        writer.put(cpiIdentifier, cpiMetadata)
        val expectedPut = listOf(Record(Schemas.VirtualNode.CPI_INFO_TOPIC, cpiIdentifier.toAvro(), cpiMetadata.toAvro()))
        verify(publisher).publish(expectedPut)

        writer.remove(cpiIdentifier)
        val expectedRemove = listOf(Record(Schemas.VirtualNode.CPI_INFO_TOPIC, cpiIdentifier.toAvro(), null))
        verify(publisher).publish(expectedRemove)
    }
}
