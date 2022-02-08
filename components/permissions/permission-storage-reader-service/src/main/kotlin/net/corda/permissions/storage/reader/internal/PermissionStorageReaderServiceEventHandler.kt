package net.corda.permissions.storage.reader.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.storage.common.ConfigKeys.BOOTSTRAP_CONFIG
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
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.permissions.cache.PermissionCacheService
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import javax.persistence.EntityManagerFactory

@Suppress("LongParameterList")
class PermissionStorageReaderServiceEventHandler(
    private val permissionCacheService: PermissionCacheService,
    private val permissionStorageReaderFactory: PermissionStorageReaderFactory,
    private val publisherFactory: PublisherFactory,
    private val configurationReadService: ConfigurationReadService,
    // injecting factory creator so that this always fetches one from source rather than re-use one that may have been
    //   re-configured.
    private val entityManagerFactoryCreator: () -> EntityManagerFactory,
) : LifecycleEventHandler {

    private companion object {
        // Is this right?
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

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                log.info("Start Event received")
                registrationHandle?.close()
                registrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<PermissionCacheService>(),
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                    )
                )
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Status Change Event received: $event")
                when (event.status) {
                    LifecycleStatus.UP -> {
                        crsSub = configurationReadService.registerForUpdates(::onConfigurationUpdated)
                        coordinator.updateStatus(LifecycleStatus.UP)
                    }
                    LifecycleStatus.DOWN -> {
                        permissionStorageReader?.stop()
                        permissionStorageReader = null
                        coordinator.updateStatus(LifecycleStatus.DOWN)
                        crsSub?.close()
                        crsSub = null
                    }
                    LifecycleStatus.ERROR -> {
                        coordinator.updateStatus(LifecycleStatus.ERROR)
                        coordinator.stop()
                        crsSub?.close()
                        crsSub = null
                    }
                }
            }
            is StopEvent -> {
                log.info("Stop Event received")
                publisher?.close()
                publisher = null
                permissionStorageReader?.stop()
                permissionStorageReader = null
                registrationHandle?.close()
                registrationHandle = null
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    @VisibleForTesting
    internal fun onConfigurationUpdated(
        changedKeys: Set<String>,
        currentConfigurationSnapshot: Map<String, SmartConfig>
    ) {
        log.info("Component received configuration update event, changedKeys: $changedKeys")

        if (BOOTSTRAP_CONFIG in changedKeys) {

            val bootstrapConfig = checkNotNull(currentConfigurationSnapshot[BOOTSTRAP_CONFIG])
            publisher = publisherFactory.createPublisher(
                publisherConfig = PublisherConfig(clientId = CLIENT_NAME),
                kafkaConfig = bootstrapConfig
            ).also {
                it.start()
            }

            permissionStorageReader = permissionStorageReaderFactory.create(
                checkNotNull(permissionCacheService.permissionCache) {
                    "The ${PermissionCacheService::class.java} should be up and ready to provide the cache"
                },
                checkNotNull(publisher) { "The ${Publisher::class.java} must be initialised" },
                entityManagerFactoryFactory()
            ).also {
                it.start()
            }
        }
    }
}