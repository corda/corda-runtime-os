package net.corda.permissions.storage.reader.internal

import javax.persistence.EntityManagerFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.reader.factory.PermissionStorageReaderFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.permissions.management.cache.PermissionManagementCacheService
import net.corda.permissions.validation.cache.PermissionValidationCacheService
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.DB_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.RECONCILIATION_PERMISSION_SUMMARY_INTERVAL_MS
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace

@Suppress("LongParameterList")
class PermissionStorageReaderServiceEventHandler(
    private val permissionValidationCacheService: PermissionValidationCacheService,
    private val permissionManagementCacheService: PermissionManagementCacheService,
    private val permissionStorageReaderFactory: PermissionStorageReaderFactory,
    private val publisherFactory: PublisherFactory,
    private val configurationReadService: ConfigurationReadService,
    // injecting factory creator so that this always fetches one from source rather than re-use one that may have been
    //   re-configured.
    private val entityManagerFactoryCreator: () -> EntityManagerFactory,
) : LifecycleEventHandler {

    private companion object {
        const val CLIENT_NAME = "user.permissions.management"
        val log = contextLogger()
    }

    @VisibleForTesting
    internal var registrationHandle: RegistrationHandle? = null

    @VisibleForTesting
    internal var permissionStorageReader: PermissionStorageReader? = null

    @VisibleForTesting
    internal var publisher: Publisher? = null

    @VisibleForTesting
    internal var crsSub: AutoCloseable? = null

    @VisibleForTesting
    internal var reconciliationTaskIntervalMs: Long? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ConfigChangedEvent -> onConfigChangedEvent(event, coordinator)
            is ReconcilePermissionSummaryEvent -> onReconcilePermissionSummaryEvent(coordinator)
            is StopEvent -> onStopEvent(coordinator)
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        log.info("Start event received, following dependencies for status updates.")
        registrationHandle?.close()
        registrationHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<PermissionManagementCacheService>(),
                LifecycleCoordinatorName.forComponent<PermissionValidationCacheService>(),
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
            )
        )
        permissionValidationCacheService.start()
        permissionManagementCacheService.start()
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        log.info("Registration status change event received: ${event.status}.")
        when (event.status) {
            LifecycleStatus.UP -> {
                crsSub = configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                )
            }
            LifecycleStatus.DOWN -> {
                permissionStorageReader?.stop()
                permissionStorageReader = null
                crsSub?.close()
                crsSub = null
            }
            LifecycleStatus.ERROR -> {
                coordinator.stop()
                crsSub?.close()
                crsSub = null
            }
        }
    }

    private fun onConfigChangedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        log.info("Configuration change event received for keys ${event.config.keys.joinToString()}")
        onConfigurationUpdated(event.config.toMessagingConfig())
        scheduleNextReconciliationTask(coordinator)
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun onReconcilePermissionSummaryEvent(coordinator: LifecycleCoordinator) {
        permissionStorageReader?.let {
            try {
                it.reconcilePermissionSummaries()
            } catch (e: Exception) {
                log.warn("Exception during permission summary reconciliation task.", e)
            }
        } ?: log.trace { "Skipping Permission Summary Reconciliation Task because PermissionStorageReader is null." }
        scheduleNextReconciliationTask(coordinator)
    }

    private fun onStopEvent(coordinator: LifecycleCoordinator) {
        log.info("Stop Event received")
        permissionValidationCacheService.stop()
        permissionManagementCacheService.stop()
        publisher?.close()
        publisher = null
        permissionStorageReader?.stop()
        permissionStorageReader = null
        registrationHandle?.close()
        registrationHandle = null
        coordinator.cancelTimer(PermissionStorageReaderServiceEventHandler::class.simpleName!!)
    }

    @VisibleForTesting
    internal fun onConfigurationUpdated(messagingConfig: SmartConfig) {

        reconciliationTaskIntervalMs = messagingConfig
            .getConfig(DB_CONFIG)
            .getLong(RECONCILIATION_PERMISSION_SUMMARY_INTERVAL_MS)

        log.info("Permission summary reconciliation interval set to $reconciliationTaskIntervalMs ms.")

        publisher?.close()
        publisher = publisherFactory.createPublisher(
            publisherConfig = PublisherConfig(clientId = CLIENT_NAME),
            kafkaConfig = messagingConfig
        ).also {
            it.start()
        }

        permissionStorageReader?.stop()
        permissionStorageReader = permissionStorageReaderFactory.create(
            checkNotNull(permissionValidationCacheService.permissionValidationCache) {
                "The ${PermissionValidationCacheService::class.java} should be up and ready to provide the cache"
            },
            checkNotNull(permissionManagementCacheService.permissionManagementCache) {
                "The ${permissionManagementCacheService::class.java} should be up and ready to provide the cache"
            },
            checkNotNull(publisher) { "The ${Publisher::class.java} must be initialised" },
            entityManagerFactoryCreator()
        ).also {
            it.start()
        }
    }

    private fun scheduleNextReconciliationTask(coordinator: LifecycleCoordinator) {
        coordinator.setTimer(
            PermissionStorageReaderServiceEventHandler::class.simpleName!!,
            reconciliationTaskIntervalMs!!
        ) { key ->
            ReconcilePermissionSummaryEvent(key)
        }
    }

}

internal data class ReconcilePermissionSummaryEvent(override val key: String) : TimerEvent