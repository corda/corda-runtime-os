package net.corda.virtualnode.write.db.impl.tests

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.virtualnode.write.VirtualNodeWriter
import net.corda.libs.virtualnode.write.VirtualNodeWriterFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus.DOWN
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.virtualnode.write.db.impl.VirtualNodeWriteEventHandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests of [VirtualNodeWriteEventHandler]. */
class VirtualNodeWriteEventHandlerTests {
    private val componentsToFollow = setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>())

    @Test
    fun `follows the configuration read service upon starting`() {
        val configReadService = mock<ConfigurationReadService>()
        val coordinator = mock<LifecycleCoordinator>()
        val eventHandler = VirtualNodeWriteEventHandler(configReadService, mock())

        eventHandler.processEvent(StartEvent(), coordinator)

        verify(coordinator).followStatusChangesByName(componentsToFollow)
    }

    @Test
    fun `closes the existing configuration update handle if one exists upon starting`() {
        val configReadService = mock<ConfigurationReadService>()
        val (coordinator, registrationHandle) = getCoordinatorAndRegistrationHandle()
        val eventHandler = VirtualNodeWriteEventHandler(configReadService, mock())

        eventHandler.processEvent(StartEvent(), coordinator)
        eventHandler.processEvent(StartEvent(), coordinator)

        verify(registrationHandle).close()
    }

    @Test
    fun `registers for configuration updates when the configuration read service comes up`() {
        val configReadService = mock<ConfigurationReadService>()
        val (coordinator, registrationHandle) = getCoordinatorAndRegistrationHandle()
        val eventHandler = VirtualNodeWriteEventHandler(configReadService, mock())

        eventHandler.processEvent(StartEvent(), coordinator)
        eventHandler.processEvent(RegistrationStatusChangeEvent(registrationHandle, UP), coordinator)

        verify(configReadService).registerForUpdates(any())
    }

    @Test
    fun `closes the existing registration handle if one exists when the configuration read service comes up`() {
        val (configReadService, updateHandle) = getConfigReadServiceAndUpdateHandle()
        val (coordinator, registrationHandle) = getCoordinatorAndRegistrationHandle()
        val eventHandler = VirtualNodeWriteEventHandler(configReadService, mock())

        eventHandler.processEvent(StartEvent(), coordinator)
        eventHandler.processEvent(RegistrationStatusChangeEvent(registrationHandle, UP), coordinator)
        eventHandler.processEvent(RegistrationStatusChangeEvent(registrationHandle, UP), coordinator)

        verify(updateHandle).close()
    }

    @Test
    fun `does not register for configuration updates when another component comes up`() {
        val configReadService = mock<ConfigurationReadService>()
        val eventHandler = VirtualNodeWriteEventHandler(configReadService, mock())

        eventHandler.processEvent(StartEvent(), mock())
        eventHandler.processEvent(RegistrationStatusChangeEvent(mock(), UP), mock())

        verify(configReadService, times(0)).registerForUpdates(any())
    }

    @Test
    fun `does not register for configuration updates when the configuration read service goes down or errors`() {
        val configReadService = mock<ConfigurationReadService>()
        val (coordinator, registrationHandle) = getCoordinatorAndRegistrationHandle()
        val eventHandler = VirtualNodeWriteEventHandler(configReadService, mock())

        eventHandler.processEvent(StartEvent(), coordinator)
        eventHandler.processEvent(RegistrationStatusChangeEvent(registrationHandle, DOWN), coordinator)
        eventHandler.processEvent(RegistrationStatusChangeEvent(registrationHandle, ERROR), coordinator)

        verify(configReadService, times(0)).registerForUpdates(any())
    }

    @Test
    fun `closes all resources and sets status to DOWN upon stopping`() {
        val (configReadService, updateHandle) = getConfigReadServiceAndUpdateHandle()
        val (coordinator, registrationHandle) = getCoordinatorAndRegistrationHandle()
        val eventHandler = VirtualNodeWriteEventHandler(configReadService, mock())

        val virtualNodeWriter = mock<VirtualNodeWriter>()
        eventHandler.virtualNodeWriter = virtualNodeWriter

        eventHandler.processEvent(StartEvent(), coordinator)
        eventHandler.processEvent(RegistrationStatusChangeEvent(registrationHandle, UP), coordinator)
        eventHandler.processEvent(StopEvent(), coordinator)

        verify(virtualNodeWriter).stop()
        verify(registrationHandle).close()
        verify(updateHandle).close()
    }

    @Test
    fun `closes all resources and sets status to ERROR if the configuration read service errors`() {
        val vnodeWriterFactory = mock<VirtualNodeWriterFactory>()
        val (configReadService, updateHandle) = getConfigReadServiceAndUpdateHandle()
        val eventHandler = VirtualNodeWriteEventHandler(configReadService, vnodeWriterFactory)
        
        val registrationHandle = mock<RegistrationHandle>()
        val eventCaptor = argumentCaptor<LifecycleEvent>()
        val coordinator = mock<LifecycleCoordinator>().apply {
            whenever(followStatusChangesByName(componentsToFollow)).thenReturn(registrationHandle)
            whenever(postEvent(eventCaptor.capture())).then {
                eventHandler.processEvent(eventCaptor.firstValue, this)
            }
        }
        val virtualNodeWriter = mock<VirtualNodeWriter>()
        eventHandler.virtualNodeWriter = virtualNodeWriter

        eventHandler.processEvent(StartEvent(), coordinator)
        eventHandler.processEvent(RegistrationStatusChangeEvent(registrationHandle, UP), coordinator)
        eventHandler.processEvent(RegistrationStatusChangeEvent(registrationHandle, ERROR), coordinator)

        verify(virtualNodeWriter).stop()
        verify(registrationHandle).close()
        verify(updateHandle).close()
    }

    /** Creates a [ConfigurationReadService] that returns a static update handle for any registration for updates. */
    private fun getConfigReadServiceAndUpdateHandle(): Pair<ConfigurationReadService, AutoCloseable> {
        val updateHandle = mock<AutoCloseable>()
        val configReadService = mock<ConfigurationReadService>().apply {
            whenever(registerForUpdates(any())).thenReturn(updateHandle)
        }
        return configReadService to updateHandle
    }

    /** Creates a [LifecycleCoordinator] that returns a static registration handle for any status-change follow. */
    private fun getCoordinatorAndRegistrationHandle(): Pair<LifecycleCoordinator, RegistrationHandle> {
        val registrationHandle = mock<RegistrationHandle>()
        val coordinator = mock<LifecycleCoordinator>().apply {
            whenever(followStatusChangesByName(componentsToFollow)).thenReturn(registrationHandle)
        }
        return coordinator to registrationHandle
    }
}