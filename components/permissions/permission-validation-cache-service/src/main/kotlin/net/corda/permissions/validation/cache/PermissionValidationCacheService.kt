package net.corda.permissions.validation.cache

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.libs.permissions.validation.cache.factory.PermissionValidationCacheFactory
import net.corda.libs.permissions.validation.cache.factory.PermissionValidationCacheTopicProcessorFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.permissions.validation.cache.internal.PermissionSummaryTopicSnapshotReceived
import net.corda.schema.Schemas.Permissions.PERMISSIONS_USER_SUMMARY_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

@Component(service = [PermissionValidationCacheService::class])
class PermissionValidationCacheService @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionValidationCacheFactory::class)
    private val permissionValidationCacheFactory: PermissionValidationCacheFactory,
    @Reference(service = PermissionValidationCacheTopicProcessorFactory::class)
    private val permissionValidationCacheTopicProcessorFactory: PermissionValidationCacheTopicProcessorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
) : Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<PermissionValidationCacheService> { event, _ -> eventHandler(event) }

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val CONSUMER_GROUP = "PERMISSION_VALIDATION_SERVICE"
    }

    /**
     * Instance of the cache used in this service.
     */
    val permissionValidationCacheRef = AtomicReference<PermissionValidationCache?>(null)

    @Volatile
    private var permissionSummarySubscription: CompactedSubscription<String, UserPermissionSummary>? = null

    @Volatile
    private var configHandle: AutoCloseable? = null

    @Volatile
    private var configRegistration: RegistrationHandle? = null

    @Volatile
    private var permissionSummarySnapshotReceived: Boolean = false

    /**
     * Cache need to be retained even between cycles of configuration change when underlying
     * subscription restarts.
     */
    private val permissionSummaryData = ConcurrentHashMap<String, UserPermissionSummary>()

    private fun eventHandler(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                log.info("Received start event, following configuration read service for status updates.")
                configRegistration?.close()
                configRegistration =
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Registration status change received: ${event.status.name}.")
                if (event.status == LifecycleStatus.UP) {
                    if (configHandle == null) {
                        log.info("Registering for configuration updates.")
                        configHandle = configurationReadService.registerComponentForUpdates(
                            coordinator, setOf(BOOT_CONFIG, MESSAGING_CONFIG))
                    }
                } else {
                    downTransition()
                }
            }
            is ConfigChangedEvent -> {
                downTransition()
                createAndStartSubscriptionsAndCache(event.config.getConfig(MESSAGING_CONFIG))
            }
            // Let's set the component as UP when it has received all the snapshots it needs
            is PermissionSummaryTopicSnapshotReceived -> {
                log.info("Permission summary topic snapshot received.")
                permissionSummarySnapshotReceived = true
                setStatusUp()
            }
            is StopEvent -> {
                log.info("Stop event received, stopping dependencies and setting status to DOWN.")
                configRegistration?.close()
                configRegistration = null
                downTransition()
                permissionValidationCacheRef.get()?.stop()
                permissionValidationCacheRef.set(null)
            }
        }
    }

    private fun downTransition() {
        log.info("Performing down transition")
        coordinator.updateStatus(LifecycleStatus.DOWN)

        configHandle?.close()
        configHandle = null
        permissionSummarySubscription?.close()
        permissionSummarySubscription = null
        permissionSummarySnapshotReceived = false
    }

    private fun setStatusUp() {
        log.info("Permission validation cache service has received all snapshots, setting status UP.")
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun createAndStartSubscriptionsAndCache(config: SmartConfig) {
        permissionSummarySubscription?.let {
            log.info("Closing permissionSummarySubscription: ${it.subscriptionName}")
            it.close()
        }
        permissionSummarySubscription =
            createPermissionSummarySubscription(permissionSummaryData, config).also { it.start() }

        permissionValidationCacheRef.get()?.stop()
        permissionValidationCacheRef.set(permissionValidationCacheFactory.createPermissionValidationCache(
            permissionSummaryData
        )
            .also { it.start() })
    }

    private fun createPermissionSummarySubscription(
        permissionSummaryData: ConcurrentHashMap<String, UserPermissionSummary>,
        kafkaConfig: SmartConfig
    ): CompactedSubscription<String, UserPermissionSummary> {
        return subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(CONSUMER_GROUP, PERMISSIONS_USER_SUMMARY_TOPIC),
            permissionValidationCacheTopicProcessorFactory.createPermissionSummaryTopicProcessor(permissionSummaryData) {
                coordinator.postEvent(PermissionSummaryTopicSnapshotReceived())
            },
            kafkaConfig
        )
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
