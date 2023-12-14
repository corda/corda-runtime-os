package net.corda.libs.virtualnode.maintenance.rest.impl.v1

import net.corda.chunking.Constants.Companion.CHUNK_FILENAME_KEY
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpi.upload.endpoints.service.CpiUploadService
import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.PropertyKeys
import net.corda.data.virtualnode.VirtualNodeDBResetRequest
import net.corda.data.virtualnode.VirtualNodeDBResetResponse
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRestResource
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
import net.corda.rest.HttpFileUpload
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.InternalServerException
import net.corda.rest.messagebus.MessageBusUtils.tryWithExceptionHandling
import net.corda.rest.security.CURRENT_REST_CONTEXT
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.debug
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.rest.common.VirtualNodeSender
import net.corda.virtualnode.rest.common.VirtualNodeSenderFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Duration

@Suppress("unused")
@Component(service = [PluggableRestResource::class])
class VirtualNodeMaintenanceRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = CpiUploadService::class)
    private val cpiUploadService: CpiUploadService,
    @Reference(service = VirtualNodeSenderFactory::class)
    private val virtualNodeSenderFactory: VirtualNodeSenderFactory,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider
) : VirtualNodeMaintenanceRestResource, PluggableRestResource<VirtualNodeMaintenanceRestResource>, Lifecycle {

    companion object {
        private val requiredKeys = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.REST_CONFIG)
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val REGISTRATION = "REGISTRATION"
        private const val SENDER = "SENDER"
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
        private const val VIRTUAL_NODE_MAINTENANCE_ASYNC_OPERATION_CLIENT_ID =
            "VIRTUAL_NODE_MAINTENANCE_ASYNC_OPERATION_CLIENT"
    }

    override val targetInterface: Class<VirtualNodeMaintenanceRestResource> =
        VirtualNodeMaintenanceRestResource::class.java

    override val protocolVersion get() = platformInfoProvider.localWorkerPlatformVersion

    private val clock = UTCClock()
    private val dependentComponents = DependentComponents.of(
        ::cpiUploadService
    )

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<VirtualNodeMaintenanceRestResource>()
    ) { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
        when (event) {
            is StartEvent -> {
                configurationReadService.start()
                coordinator.createManagedResource(REGISTRATION) {
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
                }
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
                            duration,
                            messagingConfig,
                            PublisherConfig(
                                VIRTUAL_NODE_MAINTENANCE_ASYNC_OPERATION_CLIENT_ID
                            )
                        )
                    }
                    coordinator.updateStatus(LifecycleStatus.UP)
                }
            }
        }
    }

    override fun forceCpiUpload(upload: HttpFileUpload): CpiUploadRestResource.CpiUploadResponse {
        logger.info("Force uploading CPI: ${upload.fileName}")
        if (!isRunning) {
            throw IllegalStateException(
                "${this.javaClass.simpleName} is not running! Its status is: ${lifecycleCoordinator.status}"
            )
        }
        val properties = mapOf<String, String?>(PropertyKeys.FORCE_UPLOAD to true.toString(), CHUNK_FILENAME_KEY to upload.fileName)
        val cpiUploadRequestId = tryWithExceptionHandling(logger, "Force CPI upload") {
            cpiUploadService.cpiUploadManager.uploadCpi(
                upload.content,
                properties
            )
        }
        return CpiUploadRestResource.CpiUploadResponse(cpiUploadRequestId.requestId)
    }

    override fun resyncVirtualNodeDb(virtualNodeShortId: String) {
        logger.info("Requesting synchronization of vault schema for virtual node '$virtualNodeShortId' with its current CPI.")

        val instant = clock.instant()
        val actor = CURRENT_REST_CONTEXT.get().principal
        val request = VirtualNodeManagementRequest(
            instant,
            VirtualNodeDBResetRequest(
                listOf(virtualNodeShortId),
                actor
            )
        )
        val resp = tryWithExceptionHandling(logger, "Re-sync vNode DB") {
            sendAndReceive(request)
        }
        when (val resolvedResponse = resp.responseType) {
            is VirtualNodeDBResetResponse -> Unit // We don't want to do anything with this
            is VirtualNodeManagementResponseFailure -> throw handleFailure(resolvedResponse.exception)
            else -> throw UnknownMaintenanceResponseTypeException(resp.responseType::class.java.name)
        }
    }

    /**
     * Sends the [request] to the virtual topic on bus.
     *
     * @property request is a [VirtualNodeManagementRequest]. This an envelope around the intended request
     * @throws CordaRuntimeException If the sender wasn't initialized or the request fails.
     * @return [VirtualNodeManagementResponse] which is an envelope around the actual response.
     *  This response corresponds to the [VirtualNodeManagementRequest] received by the function
     * @see VirtualNodeManagementRequest
     * @see VirtualNodeManagementResponse
     */
    @Suppress("ThrowsCount")
    private fun sendAndReceive(request: VirtualNodeManagementRequest): VirtualNodeManagementResponse {
        if (!isRunning) {
            throw IllegalStateException(
                "${this.javaClass.simpleName} is not running! Its status is: ${lifecycleCoordinator.status}"
            )
        }

        val sender = lifecycleCoordinator.getManagedResource<VirtualNodeSender>(SENDER)
            ?: throw CordaRuntimeException("Sender not initialized, check component status for ${this.javaClass.name}")

        return sender.sendAndReceive(request)
    }

    private fun handleFailure(exception: ExceptionEnvelope?): Exception {
        if (exception == null) {
            logger.warn("Configuration Management request was unsuccessful but no exception was provided.")
            return InternalServerException("Request was unsuccessful but no exception was provided.")
        }
        logger.warn(
            "Remote request failed with exception of type ${exception.errorType}: ${exception.errorMessage}"
        )
        return InternalServerException(exception.errorMessage)
    }

    // Mandatory lifecycle methods - def to coordinator
    override val isRunning get() = lifecycleCoordinator.isRunning
    override fun start() = lifecycleCoordinator.start()
    override fun stop() = lifecycleCoordinator.stop()
}
