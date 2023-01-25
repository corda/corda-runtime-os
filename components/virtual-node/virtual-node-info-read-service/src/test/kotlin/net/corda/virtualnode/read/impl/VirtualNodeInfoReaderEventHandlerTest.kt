package net.corda.virtualnode.read.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.schema.configuration.ConfigKeys
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class VirtualNodeInfoReaderEventHandlerTest {
    @Test
    fun `messaging config received`() {
        val coordinator = mock<LifecycleCoordinator>()
        val config = mock<SmartConfig>().apply {
            whenever(withFallback(any())).thenReturn(this)
        }

        val writer = VirtualNodeInfoReaderEventHandler(mock(), mock(), mock())
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

        val writer = VirtualNodeInfoReaderEventHandler(mock(), mock(), mock())
        val event = ConfigChangedEvent(emptySet(), emptyMap())
        writer.processEvent(event, coordinator)

        verify(coordinator, times(0)).updateStatus(LifecycleStatus.UP)
    }
}
