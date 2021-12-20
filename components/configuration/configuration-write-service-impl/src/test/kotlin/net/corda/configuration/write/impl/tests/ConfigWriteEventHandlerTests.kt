package net.corda.configuration.write.impl.tests

import net.corda.configuration.write.ConfigWriteServiceException
import net.corda.configuration.write.impl.ConfigWriteEventHandler
import net.corda.configuration.write.impl.DBUtils
import net.corda.configuration.write.impl.StartProcessingEvent
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.write.persistent.PersistentConfigWriter
import net.corda.libs.configuration.write.persistent.PersistentConfigWriterException
import net.corda.libs.configuration.write.persistent.PersistentConfigWriterFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.LifecycleStatus.DOWN
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.lifecycle.StopEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.SQLException

/** Tests of [ConfigWriteEventHandler]. */
class ConfigWriteEventHandlerTests {
    @Test
    fun `for StartProcessing event, throws and sets coordinator status to error if cluster database cannot be reached`() {
        val erroringDBUtils = mock<DBUtils>().apply {
            whenever(checkClusterDatabaseConnection(any())).thenAnswer { throw SQLException() }
        }

        val boxedStatus = BoxedStatus(DOWN)
        val mockCoordinator = createMockCoordinator(boxedStatus)

        val eventHandler = ConfigWriteEventHandler(DummyConfigWriterFactory(DummyConfigWriter()), erroringDBUtils)
        val e = assertThrows<ConfigWriteServiceException> {
            eventHandler.processEvent(StartProcessingEvent(mock(), 0), mockCoordinator)
        }
        assertEquals("Could not connect to cluster database.", e.message)
        assertEquals(ERROR, boxedStatus.status)
    }

    @Test
    fun `for StartProcessing event, throws and sets coordinator status to error if config writer cannot be created`() {
        val erroringConfigWriterFactory = mock<PersistentConfigWriterFactory>().apply {
            whenever(create(any(), any())).thenAnswer { throw PersistentConfigWriterException("") }
        }

        val boxedStatus = BoxedStatus(DOWN)
        val mockCoordinator = createMockCoordinator(boxedStatus)

        val eventHandler = ConfigWriteEventHandler(erroringConfigWriterFactory, mock())
        val e = assertThrows<ConfigWriteServiceException> {
            eventHandler.processEvent(StartProcessingEvent(mock(), 0), mockCoordinator)
        }
        assertEquals("Could not subscribe to config management requests.", e.message)
        assertEquals(ERROR, boxedStatus.status)
    }

    @Test
    fun `for StartProcessing event, throws and sets coordinator status to error if config writer cannot be started`() {
        val erroringConfigWriter = mock<PersistentConfigWriter>().apply {
            whenever(start()).thenAnswer { throw PersistentConfigWriterException("") }
        }
        val mockConfigWriterFactory = mock<PersistentConfigWriterFactory>().apply {
            whenever(create(any(), any())).thenReturn(erroringConfigWriter)
        }

        val boxedStatus = BoxedStatus(DOWN)
        val mockCoordinator = createMockCoordinator(boxedStatus)

        val eventHandler = ConfigWriteEventHandler(mockConfigWriterFactory, mock())
        val e = assertThrows<ConfigWriteServiceException> {
            eventHandler.processEvent(StartProcessingEvent(mock(), 0), mockCoordinator)
        }
        assertEquals("Could not subscribe to config management requests.", e.message)
        assertEquals(ERROR, boxedStatus.status)
    }

    @Test
    fun `StartProcessing event sets coordinator status to up`() {
        val boxedStatus = BoxedStatus(DOWN)
        val mockCoordinator = createMockCoordinator(boxedStatus)

        val eventHandler = ConfigWriteEventHandler(DummyConfigWriterFactory(DummyConfigWriter()), mock())
        eventHandler.processEvent(StartProcessingEvent(mock(), 0), mockCoordinator)
        assertEquals(UP, boxedStatus.status)
    }

    @Test
    fun `StartProcessing event starts config writer`() {
        val configWriter = DummyConfigWriter()
        val configWriterFactory = DummyConfigWriterFactory(configWriter)

        val eventHandler = ConfigWriteEventHandler(configWriterFactory, mock())
        eventHandler.processEvent(StartProcessingEvent(mock(), 0), mock())
        assertTrue(configWriter.isRunning)
    }

    @Test
    fun `throws if StartProcessing event received twice`() {
        val eventHandler = ConfigWriteEventHandler(DummyConfigWriterFactory(DummyConfigWriter()), mock())
        eventHandler.processEvent(StartProcessingEvent(mock(), 0), mock())

        val e = assertThrows<ConfigWriteServiceException> {
            eventHandler.processEvent(StartProcessingEvent(mock(), 0), mock())
        }
        assertEquals("An attempt was made to start processing twice.", e.message)
    }

    @Test
    fun `Stop event sets coordinator status to down`() {
        val boxedStatus = BoxedStatus(UP)
        val mockCoordinator = createMockCoordinator(boxedStatus)

        val eventHandler = ConfigWriteEventHandler(DummyConfigWriterFactory(DummyConfigWriter()), mock())
        eventHandler.processEvent(StopEvent(), mockCoordinator)
        assertEquals(DOWN, boxedStatus.status)
    }

    @Test
    fun `Stop event stops config writer`() {
        val configWriter = DummyConfigWriter()
        val configWriterFactory = DummyConfigWriterFactory(DummyConfigWriter())

        val eventHandler = ConfigWriteEventHandler(configWriterFactory, mock())
        eventHandler.processEvent(StartProcessingEvent(mock(), 0), mock())
        eventHandler.processEvent(StopEvent(), mock())
        assertFalse(configWriter.isRunning)
    }

    /** A [LifecycleCoordinator] that updates [boxedStatus] in response to lifecycle events. */
    private fun createMockCoordinator(boxedStatus: BoxedStatus): LifecycleCoordinator {
        val coordinatorStatusCaptor = argumentCaptor<LifecycleStatus>()
        return mock<LifecycleCoordinator>().apply {
            whenever(updateStatus(coordinatorStatusCaptor.capture(), any())).thenAnswer {
                boxedStatus.status = coordinatorStatusCaptor.firstValue
                null
            }
        }
    }

    /** A mutable wrapper around a [status]. */
    private class BoxedStatus(var status: LifecycleStatus)

    /** A [PersistentConfigWriterFactory] that creates [DummyConfigWriter]s. */
    private class DummyConfigWriterFactory(
        private val configWriter: PersistentConfigWriter
    ) : PersistentConfigWriterFactory {
        override fun create(config: SmartConfig, instanceId: Int) = configWriter
    }

    /** A [PersistentConfigWriter] that tracks whether it has been started and stopped. */
    private class DummyConfigWriter : PersistentConfigWriter {
        override var isRunning = false

        override fun start() {
            isRunning = true
        }

        override fun stop() {
            isRunning = false
        }
    }
}