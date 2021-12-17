package net.corda.configuration.write.impl

import net.corda.configuration.write.ConfigWriteServiceException
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

class ConfigWriteEventHandlerTests {
    @Test
    fun `when starting processing, throws and sets coordinator status to error if cluster database cannot be reached`() {
        val erroringDBUtils = mock<DBUtils>().apply {
            whenever(checkClusterDatabaseConnection(any())).thenAnswer { throw SQLException() }
        }

        var coordinatorStatus = DOWN
        val coordinatorStatusCaptor = argumentCaptor<LifecycleStatus>()
        val mockCoordinator = mock<LifecycleCoordinator>().apply {
            whenever(updateStatus(coordinatorStatusCaptor.capture(), any())).thenAnswer {
                coordinatorStatus = coordinatorStatusCaptor.firstValue
                null
            }
        }

        val eventHandler = ConfigWriteEventHandler(DummyConfigWriterFactory(), erroringDBUtils)
        val e = assertThrows<ConfigWriteServiceException> {
            eventHandler.processEvent(StartProcessingEvent(mock(), 0), mockCoordinator)
        }
        assertEquals("Could not connect to cluster database.", e.message)
        assertEquals(ERROR, coordinatorStatus)
    }

    @Test
    fun `when starting processing, throws and sets coordinator status to error if config writer cannot be created`() {
        val erroringConfigWriterFactory = mock<PersistentConfigWriterFactory>().apply {
            whenever(create(any(), any())).thenAnswer { throw PersistentConfigWriterException("") }
        }

        var coordinatorStatus = DOWN
        val coordinatorStatusCaptor = argumentCaptor<LifecycleStatus>()
        val mockCoordinator = mock<LifecycleCoordinator>().apply {
            whenever(updateStatus(coordinatorStatusCaptor.capture(), any())).thenAnswer {
                coordinatorStatus = coordinatorStatusCaptor.firstValue
                null
            }
        }

        val eventHandler = ConfigWriteEventHandler(erroringConfigWriterFactory, mock())
        val e = assertThrows<ConfigWriteServiceException> {
            eventHandler.processEvent(StartProcessingEvent(mock(), 0), mockCoordinator)
        }
        assertEquals("Could not subscribe to config management requests.", e.message)
        assertEquals(ERROR, coordinatorStatus)
    }

    @Test
    fun `when starting processing, throws and sets coordinator status to error if config writer cannot be started`() {
        val erroringConfigWriter = mock<PersistentConfigWriter>().apply {
            whenever(start()).thenAnswer { throw PersistentConfigWriterException("") }
        }
        val mockConfigWriterFactory = mock<PersistentConfigWriterFactory>().apply {
            whenever(create(any(), any())).thenReturn(erroringConfigWriter)
        }

        var coordinatorStatus = DOWN
        val coordinatorStatusCaptor = argumentCaptor<LifecycleStatus>()
        val mockCoordinator = mock<LifecycleCoordinator>().apply {
            whenever(updateStatus(coordinatorStatusCaptor.capture(), any())).thenAnswer {
                coordinatorStatus = coordinatorStatusCaptor.firstValue
                null
            }
        }

        val eventHandler = ConfigWriteEventHandler(mockConfigWriterFactory, mock())
        val e = assertThrows<ConfigWriteServiceException> {
            eventHandler.processEvent(StartProcessingEvent(mock(), 0), mockCoordinator)
        }
        assertEquals("Could not subscribe to config management requests.", e.message)
        assertEquals(ERROR, coordinatorStatus)
    }

    @Test
    fun `starting processing sets coordinator status to up`() {
        var coordinatorStatus = DOWN
        val coordinatorStatusCaptor = argumentCaptor<LifecycleStatus>()
        val mockCoordinator = mock<LifecycleCoordinator>().apply {
            whenever(updateStatus(coordinatorStatusCaptor.capture(), any())).thenAnswer {
                coordinatorStatus = coordinatorStatusCaptor.firstValue
                null
            }
        }

        val eventHandler = ConfigWriteEventHandler(DummyConfigWriterFactory(), mock())
        eventHandler.processEvent(StartProcessingEvent(mock(), 0), mockCoordinator)
        assertEquals(UP, coordinatorStatus)
    }

    @Test
    fun `starting processing starts config writer`() {
        val configWriterFactory = DummyConfigWriterFactory()

        val eventHandler = ConfigWriteEventHandler(configWriterFactory, mock())
        eventHandler.processEvent(StartProcessingEvent(mock(), 0), mock())
        assertTrue(configWriterFactory.configWriter!!.isRunning)
    }

    @Test
    fun `cannot start processing twice`() {
        val eventHandler = ConfigWriteEventHandler(DummyConfigWriterFactory(), mock())
        eventHandler.processEvent(StartProcessingEvent(mock(), 0), mock())

        val e = assertThrows<ConfigWriteServiceException> {
            eventHandler.processEvent(StartProcessingEvent(mock(), 0), mock())
        }
        assertEquals("An attempt was made to start processing twice.", e.message)
    }

    @Test
    fun `stopping sets coordinator status to down`() {
        var coordinatorStatus = UP
        val coordinatorStatusCaptor = argumentCaptor<LifecycleStatus>()
        val mockCoordinator = mock<LifecycleCoordinator>().apply {
            whenever(updateStatus(coordinatorStatusCaptor.capture(), any())).thenAnswer {
                coordinatorStatus = coordinatorStatusCaptor.firstValue
                null
            }
        }

        val eventHandler = ConfigWriteEventHandler(DummyConfigWriterFactory(), mock())
        eventHandler.processEvent(StopEvent(), mockCoordinator)
        assertEquals(DOWN, coordinatorStatus)
    }

    @Test
    fun `stopping stops config writer`() {
        val configWriterFactory = DummyConfigWriterFactory()

        val eventHandler = ConfigWriteEventHandler(configWriterFactory, mock())
        eventHandler.processEvent(StartProcessingEvent(mock(), 0), mock())
        eventHandler.processEvent(StopEvent(), mock())
        assertFalse(configWriterFactory.configWriter!!.isRunning)
    }
}

/** A dummy [PersistentConfigWriter] for test purposes. */
class DummyConfigWriter : PersistentConfigWriter {
    override var isRunning = false

    override fun start() {
        isRunning = true
    }

    override fun stop() {
        isRunning = false
    }
}

/** A dummy [PersistentConfigWriterFactory] for test purposes. */
class DummyConfigWriterFactory : PersistentConfigWriterFactory {
    var configWriter: PersistentConfigWriter? = null

    override fun create(config: SmartConfig, instanceId: Int) = DummyConfigWriter().also { configWriter ->
        this.configWriter = configWriter
    }
}