package net.corda.permissions.management.internal

import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.configuration.SmartConfig
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class PermissionManagementServiceEventHandlerTest {
    private val permissionCache = mock<PermissionCache>()
    private val permissionCacheService = mock<PermissionCacheService>()

    private val rpcSender = mock<RPCSender<PermissionManagementRequest, PermissionManagementResponse>>()
    private val publisherFactory = mock<PublisherFactory>()

    private val configurationReadService = mock<ConfigurationReadService>()

    private val permissionManager = mock<PermissionManager>()
    private val permissionManagerFactory = mock<PermissionManagerFactory>()

    private val registrationHandle = mock<RegistrationHandle>()
    private val coordinator = mock<LifecycleCoordinator>()

    private val handler = PermissionManagementServiceEventHandler(
        publisherFactory,
        permissionCacheService,
        permissionManagerFactory,
        configurationReadService
    )

    private val config: SmartConfig = mock()

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
    fun `processing a start event creates registers for updates from permission cache and configuration`() {
        assertNull(handler.registrationHandle)

        handler.processEvent(StartEvent(), coordinator)

        assertNotNull(handler.registrationHandle)
        verify(configurationReadService).registerForUpdates(any())
    }

    @Test
    fun `processing an UP event from the permission cache validates the cache isn't null`() {
        whenever(permissionCacheService.permissionCache).thenReturn(null)
        assertNull(handler.permissionManager)

        handler.processEvent(StartEvent(), coordinator)

        assertThrows<java.lang.IllegalStateException> {
            handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        }
    }

    @Test
    fun `processing UP event from permission cache when rpcSender is ready creates the permission manager and sets state to UP`() {
        handler.rpcSender = rpcSender
        handler.permissionManager = null

        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)

        verify(permissionManagerFactory).create(rpcSender, permissionCache)
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `processing DOWN event stops and nulls the permission manager and sets status to DOWN`() {
        handler.permissionManager = permissionManager

        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)

        verify(permissionManager).stop()
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)

        assertNull(handler.permissionManager)
    }

    @Test
    fun `processing a stop event stops the service's dependencies and sets status to DOWN`() {
        handler.rpcSender = rpcSender
        handler.permissionManager = permissionManager
        handler.registrationHandle = registrationHandle

        handler.processEvent(StopEvent(), coordinator)

        assertNull(handler.registrationHandle)
        assertNull(handler.permissionManager)
        assertNull(handler.rpcSender)

        verify(rpcSender).stop()
        verify(permissionManager).stop()
        verify(registrationHandle).close()
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }
}