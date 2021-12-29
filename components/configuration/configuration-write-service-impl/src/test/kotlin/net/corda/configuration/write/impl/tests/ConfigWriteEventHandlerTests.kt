package net.corda.configuration.write.impl.tests

import net.corda.configuration.write.ConfigWriteServiceException
import net.corda.configuration.write.impl.ConfigWriteEventHandler
import net.corda.configuration.write.impl.StartProcessingEvent
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.write.ConfigWriter
import net.corda.libs.configuration.write.ConfigWriterException
import net.corda.libs.configuration.write.ConfigWriterFactory
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
import javax.persistence.EntityManagerFactory

/** Tests of [ConfigWriteEventHandler]. */
class ConfigWriteEventHandlerTests {
    private val instanceId = 0

    @Test
    fun `StartProcessing event throws and sets coordinator status to error if config writer cannot be created`() {
        val erroringConfigWriterFactory = DummyConfigWriterFactory(DummyConfigWriter(), isCreationFails = true)

        val boxedStatus = BoxedStatus(DOWN)
        val mockCoordinator = createMockCoordinator(boxedStatus)

        val eventHandler = ConfigWriteEventHandler(erroringConfigWriterFactory)
        val e = assertThrows<ConfigWriteServiceException> {
            eventHandler.processEvent(StartProcessingEvent(mock(), instanceId, mock()), mockCoordinator)
        }
        assertEquals("Could not subscribe to config management requests.", e.message)
        assertEquals(ERROR, boxedStatus.status)
    }

    @Test
    fun `StartProcessing event throws and sets coordinator status to error if config writer cannot be started`() {
        val erroringConfigWriter = DummyConfigWriter(isStartFails = true)
        val configWriterFactory = DummyConfigWriterFactory(erroringConfigWriter)

        val boxedStatus = BoxedStatus(DOWN)
        val mockCoordinator = createMockCoordinator(boxedStatus)

        val eventHandler = ConfigWriteEventHandler(configWriterFactory)
        val e = assertThrows<ConfigWriteServiceException> {
            eventHandler.processEvent(StartProcessingEvent(mock(), instanceId, mock()), mockCoordinator)
        }
        assertEquals("Could not subscribe to config management requests.", e.message)
        assertEquals(ERROR, boxedStatus.status)
    }

    @Test
    fun `StartProcessing event sets coordinator status to up`() {
        val boxedStatus = BoxedStatus(DOWN)
        val mockCoordinator = createMockCoordinator(boxedStatus)

        val eventHandler = ConfigWriteEventHandler(DummyConfigWriterFactory(DummyConfigWriter()))
        eventHandler.processEvent(StartProcessingEvent(mock(), instanceId, mock()), mockCoordinator)
        assertEquals(UP, boxedStatus.status)
    }

    @Test
    fun `StartProcessing event starts config writer`() {
        val configWriter = DummyConfigWriter()
        val configWriterFactory = DummyConfigWriterFactory(configWriter)

        val eventHandler = ConfigWriteEventHandler(configWriterFactory)
        eventHandler.processEvent(StartProcessingEvent(mock(), instanceId, mock()), mock())
        assertTrue(configWriter.isRunning)
    }

    @Test
    fun `throws if StartProcessing event received twice`() {
        val eventHandler = ConfigWriteEventHandler(DummyConfigWriterFactory(DummyConfigWriter()))
        eventHandler.processEvent(StartProcessingEvent(mock(), instanceId, mock()), mock())

        val e = assertThrows<ConfigWriteServiceException> {
            eventHandler.processEvent(StartProcessingEvent(mock(), instanceId, mock()), mock())
        }
        assertEquals("An attempt was made to start processing twice.", e.message)
    }

    @Test
    fun `Stop event sets coordinator status to down`() {
        val boxedStatus = BoxedStatus(UP)
        val mockCoordinator = createMockCoordinator(boxedStatus)

        val eventHandler = ConfigWriteEventHandler(DummyConfigWriterFactory(DummyConfigWriter()))
        eventHandler.processEvent(StopEvent(), mockCoordinator)
        assertEquals(DOWN, boxedStatus.status)
    }

    @Test
    fun `Stop event stops config writer`() {
        val configWriter = DummyConfigWriter()
        val configWriterFactory = DummyConfigWriterFactory(DummyConfigWriter())

        val eventHandler = ConfigWriteEventHandler(configWriterFactory)
        eventHandler.processEvent(StartProcessingEvent(mock(), instanceId, mock()), mock())
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

    /** A mutable wrapper around [status]. */
    private class BoxedStatus(var status: LifecycleStatus)

    /**
     * A [ConfigWriterFactory] that creates [DummyConfigWriter]s.
     *
     * Throws [ConfigWriterException] if [isCreationFails].
     */
    private class DummyConfigWriterFactory(
        private val configWriter: ConfigWriter,
        private val isCreationFails: Boolean = false,
    ) : ConfigWriterFactory {
        override fun create(config: SmartConfig, instanceId: Int, entityManagerFactory: EntityManagerFactory) =
            if (isCreationFails) {
                throw ConfigWriterException("")
            } else {
                configWriter
            }
    }

    /**
     * A [ConfigWriter] that tracks whether it has been started and stopped.
     *
     * Throws [ConfigWriterException] if [isStartFails].
     */
    private class DummyConfigWriter(private val isStartFails: Boolean = false) : ConfigWriter {
        override var isRunning = false

        override fun start() {
            if (isStartFails) {
                throw ConfigWriterException("")
            }

            isRunning = true
        }

        override fun stop() {
            isRunning = false
        }
    }
}