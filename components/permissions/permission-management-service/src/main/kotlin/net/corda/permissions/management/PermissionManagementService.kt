package net.corda.permissions.management

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.manager.BasicAuthenticationService
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.factory.PermissionManagerFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.permissions.management.cache.PermissionManagementCacheService
import net.corda.permissions.management.internal.PermissionManagementServiceEventHandler
import net.corda.permissions.validation.PermissionValidationService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This component encapsulates all the necessary dependencies required for permission management including the permission validation
 * service. This is required because
 */
@Component(service = [PermissionManagementService::class])
class PermissionManagementService @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionManagementCacheService::class)
    private val permissionManagementCacheService: PermissionManagementCacheService,
    @Reference(service = PermissionValidationService::class)
    private val permissionValidationService: PermissionValidationService,
    @Reference(service = PermissionManagerFactory::class)
    private val permissionManagerFactory: PermissionManagerFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
) : Lifecycle {

    private val handler = PermissionManagementServiceEventHandler(
        publisherFactory,
        permissionManagementCacheService,
        permissionValidationService,
        permissionManagerFactory,
        configurationReadService
    )
    private val coordinator = coordinatorFactory.createCoordinator<PermissionManagementService>(handler)

    /**
     * Manager for performing permission management operations on the permission system.
     */
    val permissionManager: PermissionManager
        get() {
            checkNotNull(handler.permissionManager) {
                "Permission Manager is null. Getter should be called only after service is UP."
            }
            return handler.permissionManager!!
        }

    /**
     * Validator for performing permission validation operations using the permission system.
     */
    val permissionValidator: PermissionValidator
        get() {
            checkNotNull(handler.permissionValidator) {
                "Permission Validator is null. Getter should be called only after service is UP."
            }
            return handler.permissionValidator!!
        }

    /**
     * Service that exposes functionality to perform basic authentication using the permission system.
     */
    val basicAuthenticationService: BasicAuthenticationService
        get() {
            checkNotNull(handler.basicAuthenticationService) {
                "Permission basic authenticator is null. Getter should be called only after service is UP."
            }
            return handler.basicAuthenticationService!!
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