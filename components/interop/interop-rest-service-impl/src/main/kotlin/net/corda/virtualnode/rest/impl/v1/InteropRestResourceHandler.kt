package net.corda.virtualnode.rest.impl.v1

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.interop.endpoints.v1.InteropRestResource
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.rest.PluggableRestResource
import net.corda.rest.asynchronous.v1.AsyncResponse
import net.corda.rest.response.ResponseEntity
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.debug
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.rest.common.VirtualNodeSender
import net.corda.virtualnode.rest.common.VirtualNodeSenderFactory
import net.corda.virtualnode.rest.impl.status.VirtualNodeStatusCacheService
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*

@Suppress("LongParameterList", "TooManyFunctions")
@Component(service = [PluggableRestResource::class])
internal class InteropRestResourceHandler(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = VirtualNodeSenderFactory::class)
    private val virtualNodeSenderFactory: VirtualNodeSenderFactory,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = VirtualNodeStatusCacheService::class)
    private val virtualNodeStatusCacheService: VirtualNodeStatusCacheService
) : InteropRestResource, PluggableRestResource<InteropRestResource>, Lifecycle {

//    @Suppress("Unused")
//    @Activate
//    constructor(
//        @Reference(service = LifecycleCoordinatorFactory::class)
//        coordinatorFactory: LifecycleCoordinatorFactory,
//        @Reference(service = ConfigurationReadService::class)
//        configurationReadService: ConfigurationReadService,
//        @Reference(service = VirtualNodeInfoReadService::class)
//        virtualNodeInfoReadService: VirtualNodeInfoReadService,
//        @Reference(service = VirtualNodeSenderFactory::class)
//        virtualNodeSenderFactory: VirtualNodeSenderFactory,
//        @Reference(service = CpiInfoReadService::class)
//        cpiInfoReadService: CpiInfoReadService,
//        @Reference(service = VirtualNodeStatusCacheService::class)
//        virtualNodeStatusCacheService: VirtualNodeStatusCacheService
//    ) : this(
//        coordinatorFactory,
//        configurationReadService,
//        virtualNodeInfoReadService,
//        virtualNodeSenderFactory,
//        cpiInfoReadService,
//        virtualNodeStatusCacheService
//    )

    private companion object {
        private val requiredKeys = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.REST_CONFIG)
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val SENDER = "SENDER"
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
        private const val VIRTUAL_NODE_ASYNC_OPERATION_CLIENT_ID = "VIRTUAL_NODE_ASYNC_OPERATION_CLIENT"
    }

    // RestResource values
    override val targetInterface: Class<InteropRestResource> = InteropRestResource::class.java
    override fun getInterOpGroups(): List<UUID> {
        TODO("Not yet implemented")
        //return readService.getAll()
    }

    override fun createInterOpIdentity(x500Name: String, groupId: UUID): ResponseEntity<AsyncResponse> {
        TODO("Not yet implemented")
//        val requestId = MessageBusUtils.tryWithExceptionHandling(logger, "Create interop identity") {
//            sendAsynchronousRequest(
//                Instant.now(),
//                x500Name,
//                currentCpi.fileChecksum.toHexString(),
//                targetCpi.fileChecksum.toHexString(),
//                restContextProvider.principal
//            )
//        }
//
//        return ResponseEntity.accepted(AsyncResponse(requestId))
    }

    override val protocolVersion = 1

    // Lifecycle
    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::virtualNodeInfoReadService,
        ::cpiInfoReadService,
        ::virtualNodeStatusCacheService
    )

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<InteropRestResource>()
    ) { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> coordinator.updateStatus(LifecycleStatus.DOWN)
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.ERROR -> {
                        coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
                        coordinator.postEvent(StopEvent(errored = true))
                    }

                    LifecycleStatus.UP -> {
                        // Receive updates to the REST and Messaging config
                        coordinator.createManagedResource(CONFIG_HANDLE) {
                            configurationReadService.registerComponentForUpdates(
                                coordinator,
                                requiredKeys
                            )
                        }
                    }

                    else -> logger.debug { "Unexpected status: ${event.status}" }
                }
                coordinator.updateStatus(event.status)
            }

            is ConfigChangedEvent -> {
                if (requiredKeys.all { it in event.config.keys } and event.keys.any { it in requiredKeys }) {
                    val restConfig = event.config.getConfig(ConfigKeys.REST_CONFIG)
                    val messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
                    val duration =
                        Duration.ofMillis(restConfig.getInt(ConfigKeys.REST_ENDPOINT_TIMEOUT_MILLIS).toLong())
                    // Make sender unavailable while we're updating
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                    coordinator.createManagedResource(SENDER) {
                        virtualNodeSenderFactory.createSender(
                            duration, messagingConfig, PublisherConfig(VIRTUAL_NODE_ASYNC_OPERATION_CLIENT_ID)
                        )
                    }

                    virtualNodeStatusCacheService.onConfiguration(messagingConfig)
                }
            }

//            is CustomEvent -> {
//                if ((event.payload as? CacheLoadCompleteEvent) != null) {
//                    coordinator.updateStatus(LifecycleStatus.UP)
//                }
//            }
        }
    }

    private fun sendAsynchronousRequest(
        requestTime: Instant,
        virtualNodeShortId: String,
        currentCpiFileChecksum: String,
        targetCpiFileChecksum: String,
        actor: String
    ): String {
        val requestId = generateUpgradeRequestId(virtualNodeShortId, currentCpiFileChecksum, targetCpiFileChecksum)

        sendAsync(
            virtualNodeShortId,
            VirtualNodeAsynchronousRequest(
                requestTime, requestId, VirtualNodeUpgradeRequest(virtualNodeShortId, targetCpiFileChecksum, actor)
            )
        )

        return requestId
    }

    /**
     * Virtual node upgrade request ID deterministically generated using the virtual node identifier, current CPI file
     * checksum and target CPI file checksum. This provides a level of idempotency preventing the same upgrade from
     * triggering more than once.
     */
    private fun generateUpgradeRequestId(
        virtualNodeShortId: String, currentCpiFileChecksum: String, targetCpiFileChecksum: String
    ): String {
        return virtualNodeShortId.take(12) + currentCpiFileChecksum.take(12) + targetCpiFileChecksum.take(12)
    }

    private fun sendAsync(key: String, request: VirtualNodeAsynchronousRequest) {
        val sender = lifecycleCoordinator.getManagedResource<VirtualNodeSender>(SENDER)
        check(sender != null) {
            "Sender not initialized, check component status for ${this.javaClass.name}"
        }

        return sender.sendAsync(key, request)
    }

    // Mandatory lifecycle methods - def to coordinator
    override val isRunning get() = lifecycleCoordinator.isRunning
    override fun start() = lifecycleCoordinator.start()
    override fun stop() = lifecycleCoordinator.stop()

}