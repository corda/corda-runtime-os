package net.corda.permissions.management

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.factory.PermissionManagerFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.permissions.cache.PermissionCacheService
import net.corda.permissions.management.internal.PermissionManagementServiceEventHandler
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PermissionManagementService::class])
class PermissionManagementService @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionCacheService::class)
    private val permissionCacheService: PermissionCacheService,
    @Reference(service = PermissionManagerFactory::class)
    private val permissionManagerFactory: PermissionManagerFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
) : Lifecycle {

    private val handler = PermissionManagementServiceEventHandler(
        publisherFactory,
        permissionCacheService,
        permissionManagerFactory,
        configurationReadService
    )
    private val coordinator = coordinatorFactory.createCoordinator<PermissionManagementService>(handler)

    val permissionManager: PermissionManager
        get() {
            checkNotNull(handler.permissionManager) {
                "Permission Manager is null. Getter should be called only after service is UP."
            }
            return handler.permissionManager!!
        }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}