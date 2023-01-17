package net.corda.permissions.management.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.factory.PermissionManagerFactory
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.permissions.management.cache.PermissionManagementCacheService
import net.corda.permissions.validation.PermissionValidationService
import net.corda.permissions.validation.cache.PermissionValidationCacheService
import net.corda.schema.configuration.ConfigKeys
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicReference

internal class PermissionManagementServiceEventHandlerTest {
    private val permissionManagementCacheRef = AtomicReference(mock<PermissionManagementCache>())
    private val permissionValidationCacheRef = AtomicReference(mock<PermissionValidationCache>())
    private val permissionManagementCacheService = mock<PermissionManagementCacheService>()
    private val permissionValidationCacheService = mock<PermissionValidationCacheService>()
    private val permissionValidationService = mock<PermissionValidationService>()

    private val config = mock<SmartConfig>()
    private val rpcSender = mock<RPCSender<PermissionManagementRequest, PermissionManagementResponse>>()
    private val publisherFactory = mock<PublisherFactory>()

    private val configurationReadService = mock<ConfigurationReadService>()

    private val permissionManager = mock<PermissionManager>()
    private val permissionManagerFactory = mock<PermissionManagerFactory>()

    private val registrationHandle = mock<RegistrationHandle>()
    private val coordinator = mock<LifecycleCoordinator>()

    private val handler = PermissionManagementServiceEventHandler(
        publisherFactory,
        permissionManagementCacheService,
        permissionValidationCacheService,
        permissionValidationService,
        permissionManagerFactory,
        configurationReadService
    )

    @BeforeEach
    fun setUp() {
        whenever(permissionManagementCacheService.permissionManagementCacheRef)
            .thenReturn(permissionManagementCacheRef)

        whenever(permissionValidationCacheService.permissionValidationCacheRef)
            .thenReturn(permissionValidationCacheRef)

        whenever(publisherFactory.createRPCSender(any<RPCConfig<PermissionManagementRequest, PermissionManagementResponse>>(), any()))
            .thenReturn(rpcSender)

        whenever(
            permissionManagerFactory.createPermissionManager(
                config,
                rpcSender,
                permissionManagementCacheRef,
                permissionValidationCacheRef
            )
        )
            .thenReturn(permissionManager)

        whenever(
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<PermissionManagementCacheService>(),
                    LifecycleCoordinatorName.forComponent<PermissionValidationCacheService>(),
                    LifecycleCoordinatorName.forComponent<PermissionValidationService>(),
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                )
            )
        ).thenReturn(registrationHandle)
    }

    @Test
    fun `processing a start event follows dependencies`() {
        assertNull(handler.registrationHandle)

        handler.processEvent(StartEvent(), coordinator)

        verify(coordinator).followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<PermissionManagementCacheService>(),
                LifecycleCoordinatorName.forComponent<PermissionValidationCacheService>(),
                LifecycleCoordinatorName.forComponent<PermissionValidationService>(),
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
            )
        )

        verify(permissionValidationService).start()
        verify(permissionManagementCacheService).start()
        assertNotNull(handler.registrationHandle)
    }

    @Test
    fun `when cache and config read are UP, register for config updates with callback`() {
        handler.processEvent(StartEvent(), coordinator)

        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)

        verify(configurationReadService).registerComponentForUpdates(
            coordinator, setOf(
                ConfigKeys.BOOT_CONFIG,
                ConfigKeys.MESSAGING_CONFIG,
                ConfigKeys.REST_CONFIG
            )
        )
    }

    @Test
    fun `processing status DOWN change for cache or config will stop permission manager`() {
        handler.permissionManager = permissionManager

        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)

        verify(permissionManager).stop()
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `processing UP status update subscribes for configuration updates`() {
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)

        verify(configurationReadService).registerComponentForUpdates(
            coordinator, setOf(
                ConfigKeys.BOOT_CONFIG,
                ConfigKeys.MESSAGING_CONFIG,
                ConfigKeys.REST_CONFIG
            )
        )
    }

    @Test
    fun `processing DOWN status update stops and nulls the permission manager and sets status to DOWN`() {
        handler.permissionManager = permissionManager

        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)

        verify(permissionManager).stop()
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)

        assertNull(handler.permissionManager)
    }

    @Test
    fun `processing ERROR status update stops coordinator and sets status to ERROR`() {
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.ERROR), coordinator)

        verify(coordinator).stop()
        verify(coordinator).updateStatus(LifecycleStatus.ERROR)
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

        verify(rpcSender).close()
        verify(permissionManager).stop()
        verify(registrationHandle).close()
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }
}
