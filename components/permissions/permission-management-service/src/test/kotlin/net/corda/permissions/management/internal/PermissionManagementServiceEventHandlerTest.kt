package net.corda.permissions.management.internal

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.factory.PermissionManagerFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.permissions.cache.PermissionCacheService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class PermissionManagementServiceEventHandlerTest {
    private val permissionCache = mock<PermissionCache>()
    private val permissionCacheService = mock<PermissionCacheService>()

    private val rpcSender = mock<RPCSender<PermissionManagementRequest, PermissionManagementResponse>>()
    private val publisherFactory = mock<PublisherFactory>()

    private val permissionManager = mock<PermissionManager>()
    private val permissionManagerFactory = mock<PermissionManagerFactory>()

    private val registrationHandle = mock<RegistrationHandle>()
    private val coordinator = mock<LifecycleCoordinator>()

    private val handler = PermissionManagementServiceEventHandler(publisherFactory, permissionCacheService, permissionManagerFactory)

    @BeforeEach
    fun setUp() {
        whenever(permissionCacheService.permissionCache)
            .thenReturn(permissionCache)
        whenever(publisherFactory.createRPCSender(any<RPCConfig<PermissionManagementRequest, PermissionManagementResponse>>(), any()))
            .thenReturn(rpcSender)
        whenever(permissionManagerFactory.create(rpcSender, permissionCache))
            .thenReturn(permissionManager)
        whenever(coordinator.followStatusChangesByName(any())).thenReturn(registrationHandle)
    }

    @Test
    fun `processing a start event creates and starts the rpc sender`() {
        assertNull(handler.rpcSender)

        handler.processEvent(StartEvent(), coordinator)

        assertNotNull(handler.rpcSender)

        verify(rpcSender).start()
    }

    @Test
    fun `processing a start event causes the service to follow permission cache status changes`() {
        assertNull(handler.registrationHandle)

        handler.processEvent(StartEvent(), coordinator)

        assertNotNull(handler.registrationHandle)
    }

    @Test
    fun `processing an UP event from the permission cache when the service is started starts the permission manager`() {
        assertNull(handler.permissionManager)

        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)

        assertNotNull(handler.permissionManager)
        verify(permissionManager).start()
    }

    @Test
    fun `processing an UP event from the permission cache when the service is not started throws an exception`() {
        assertThrows<IllegalStateException> {
            handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        }
    }

    @Test
    fun `processing an UP event from the permission cache when the service is started updates the service's status to UP`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `processing a DOWN event from the permission cache when the service is started stops the permission manager`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)

        assertNotNull(handler.permissionManager)

        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)

        assertNull(handler.permissionManager)
        verify(permissionManager).stop()
    }

    @Test
    fun `processing a DOWN event from the permission cache when the service is started updates the service's status to DOWN`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `processing a stop event stops the service's dependencies`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)

        assertNotNull(handler.registrationHandle)
        assertNotNull(handler.permissionManager)
        assertNotNull(handler.rpcSender)

        handler.processEvent(StopEvent(), coordinator)

        assertNull(handler.registrationHandle)
        assertNull(handler.permissionManager)
        assertNull(handler.rpcSender)

        verify(rpcSender).stop()
        verify(permissionManager).stop()
        verify(registrationHandle).close()
    }

    @Test
    fun `processing a stop event updates the service's status to DOWN`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(StopEvent(), coordinator)
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }
}