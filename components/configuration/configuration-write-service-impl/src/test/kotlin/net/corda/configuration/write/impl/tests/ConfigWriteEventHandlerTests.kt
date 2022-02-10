package net.corda.configuration.write.impl.tests

import net.corda.configuration.write.ConfigWriteServiceException
import net.corda.configuration.write.impl.ConfigWriteEventHandler
import net.corda.configuration.write.impl.StartProcessingEvent
import net.corda.configuration.write.impl.writer.ConfigWriter
import net.corda.configuration.write.impl.writer.ConfigWriterException
import net.corda.configuration.write.impl.writer.ConfigWriterFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus.DOWN
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.lifecycle.StopEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests of [ConfigWriteEventHandler]. */
class ConfigWriteEventHandlerTests {
    /** Returns a mock [ConfigWriter] and a mock [ConfigWriterFactory] that returns it. */
    private fun getConfigWriterAndFactory(): Pair<ConfigWriter, ConfigWriterFactory> {
        val configWriter = mock<ConfigWriter>()
        return configWriter to mock<ConfigWriterFactory>().apply {
            whenever(create(any(), any())).thenReturn(configWriter)
        }
    }

    @Test
    fun `StartProcessing event throws and sets coordinator status to error if config writer cannot be created`() {
        val erroringConfigWriterFactory = mock<ConfigWriterFactory>().apply {
            whenever(create(any(), any())).thenThrow(ConfigWriterException(""))
        }
        val eventHandler = ConfigWriteEventHandler(erroringConfigWriterFactory)
        val coordinator = mock<LifecycleCoordinator>()

        val e = assertThrows<ConfigWriteServiceException> {
            eventHandler.processEvent(StartProcessingEvent(mock(), 0), coordinator)
        }
        assertEquals("Could not subscribe to config management requests.", e.message)
        verify(coordinator).updateStatus(eq(ERROR), any())
    }

    @Test
    fun `StartProcessing event throws and sets coordinator status to error if config writer cannot be started`() {
        val erroringConfigWriter = mock<ConfigWriter>().apply {
            whenever(start()).thenThrow(ConfigWriterException(""))
        }
        val configWriterFactory = mock<ConfigWriterFactory>().apply {
            whenever(create(any(), any())).thenReturn(erroringConfigWriter)
        }
        val eventHandler = ConfigWriteEventHandler(configWriterFactory)
        val coordinator = mock<LifecycleCoordinator>()

        val e = assertThrows<ConfigWriteServiceException> {
            eventHandler.processEvent(StartProcessingEvent(mock(), 0), coordinator)
        }
        assertEquals("Could not subscribe to config management requests.", e.message)
        verify(coordinator).updateStatus(eq(ERROR), any())
    }

    @Test
    fun `StartProcessing event sets coordinator status to up`() {
        val eventHandler = ConfigWriteEventHandler(getConfigWriterAndFactory().second)
        val coordinator = mock<LifecycleCoordinator>()
        eventHandler.processEvent(StartProcessingEvent(mock(), 0), coordinator)

        verify(coordinator).updateStatus(eq(UP), any())
    }

    @Test
    fun `StartProcessing event starts config writer`() {
        val (configWriter, configWriterFactory) = getConfigWriterAndFactory()
        val eventHandler = ConfigWriteEventHandler(configWriterFactory)
        eventHandler.processEvent(StartProcessingEvent(mock(), 0), mock())

        verify(configWriter).start()
    }

    @Test
    fun `throws if StartProcessing event received twice`() {
        val eventHandler = ConfigWriteEventHandler(getConfigWriterAndFactory().second)
        eventHandler.processEvent(StartProcessingEvent(mock(), 0), mock())

        val e = assertThrows<ConfigWriteServiceException> {
            eventHandler.processEvent(StartProcessingEvent(mock(), 0), mock())
        }
        assertEquals("An attempt was made to start processing twice.", e.message)
    }

    @Test
    fun `Stop event sets coordinator status to down`() {
        val eventHandler = ConfigWriteEventHandler(getConfigWriterAndFactory().second)
        val coordinator = mock<LifecycleCoordinator>()
        eventHandler.processEvent(StopEvent(), coordinator)

        verify(coordinator).updateStatus(eq(DOWN), any())
    }

    @Test
    fun `Stop event stops config writer`() {
        val (configWriter, configWriterFactory) = getConfigWriterAndFactory()
        val eventHandler = ConfigWriteEventHandler(configWriterFactory)
        eventHandler.processEvent(StartProcessingEvent(mock(), 0), mock())
        eventHandler.processEvent(StopEvent(), mock())

        verify(configWriter).stop()
    }
}