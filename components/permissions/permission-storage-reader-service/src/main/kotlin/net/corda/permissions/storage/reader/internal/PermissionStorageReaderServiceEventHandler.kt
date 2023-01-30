package net.corda.permissions.storage.reader.internal

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
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
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.permissions.management.cache.PermissionManagementCacheService
import net.corda.permissions.validation.cache.PermissionValidationCacheService
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.RECONCILIATION_CONFIG
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_PERMISSION_SUMMARY_INTERVAL_MS
import net.corda.utilities.VisibleForTesting
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory
import javax.persistence.EntityManagerFactory

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
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @VisibleForTesting
    @Volatile
    internal var registrationHandle: RegistrationHandle? = null

    @VisibleForTesting
    @Volatile
    internal var permissionStorageReader: PermissionStorageReader? = null

    @VisibleForTesting
    @Volatile
    internal var publisher: Publisher? = null

    @VisibleForTesting
    @Volatile
    internal var crsSub: AutoCloseable? = null

    @VisibleForTesting
    @Volatile
    internal var reconciliationTaskIntervalMs: Long? = null

    private val timerKey = PermissionStorageReaderServiceEventHandler::class.simpleName!!

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
        registrationHandle?.close()
        registrationHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<PermissionManagementCacheService>(),
                LifecycleCoordinatorName.forComponent<PermissionValidationCacheService>(),
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                LifecycleCoordinatorName.forComponent<DbConnectionManager>()
            )
        )
        permissionValidationCacheService.start()
        permissionManagementCacheService.start()
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        when (event.status) {
            LifecycleStatus.UP -> {
                crsSub = configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(BOOT_CONFIG, MESSAGING_CONFIG, RECONCILIATION_CONFIG)
                )
            }
            LifecycleStatus.DOWN -> {
                permissionStorageReader?.close()
                permissionStorageReader = null
                crsSub?.close()
                crsSub = null
                coordinator.updateStatus(LifecycleStatus.DOWN)
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
        onConfigurationUpdated(event.config)
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
        permissionValidationCacheService.stop()
        permissionManagementCacheService.stop()
        publisher?.close()
        publisher = null
        permissionStorageReader?.close()
        permissionStorageReader = null
        registrationHandle?.close()
        registrationHandle = null
        coordinator.cancelTimer(timerKey)
    }

    @VisibleForTesting
    internal fun onConfigurationUpdated(config: Map<String, SmartConfig>) {
        val messagingConfig = config.getConfig(MESSAGING_CONFIG)
        val reconciliationConfig = config.getConfig(RECONCILIATION_CONFIG)

        reconciliationTaskIntervalMs = reconciliationConfig.getLong(RECONCILIATION_PERMISSION_SUMMARY_INTERVAL_MS)

        log.info("Permission summary reconciliation interval set to $reconciliationTaskIntervalMs ms.")

        publisher?.close()
        publisher = publisherFactory.createPublisher(
            publisherConfig = PublisherConfig(clientId = CLIENT_NAME),
            messagingConfig = messagingConfig
        ).also {
            it.start()
        }

        permissionStorageReader?.close()
        permissionStorageReader = permissionStorageReaderFactory.create(
            permissionValidationCacheService.permissionValidationCacheRef,
            permissionManagementCacheService.permissionManagementCacheRef,
            checkNotNull(publisher) { "The ${Publisher::class.java} must be initialised" },
            entityManagerFactoryCreator()
        ).also {
            it.start()
        }
    }

    private fun scheduleNextReconciliationTask(coordinator: LifecycleCoordinator) {
        coordinator.setTimer(
            timerKey,
            reconciliationTaskIntervalMs!!
        ) { key ->
            ReconcilePermissionSummaryEvent(key)
        }
    }

}

internal data class ReconcilePermissionSummaryEvent(override val key: String) : TimerEvent
