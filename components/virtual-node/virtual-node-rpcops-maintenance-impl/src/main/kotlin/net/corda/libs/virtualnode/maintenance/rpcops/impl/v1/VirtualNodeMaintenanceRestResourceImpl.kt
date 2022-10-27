package net.corda.libs.virtualnode.maintenance.rpcops.impl.v1

import java.time.Duration
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.PropertyKeys
import net.corda.data.virtualnode.VirtualNodeDBResetRequest
import net.corda.data.virtualnode.VirtualNodeDBResetResponse
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.security.CURRENT_REST_CONTEXT
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
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
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.rpcops.common.VirtualNodeSender
import net.corda.virtualnode.rpcops.common.VirtualNodeSenderFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.lang.Exception

@Suppress("unused")
@Component(service = [PluggableRestResource::class])
class VirtualNodeMaintenanceRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = CpiUploadRPCOpsService::class)
    private val cpiUploadRPCOpsService: CpiUploadRPCOpsService,
    @Reference(service = VirtualNodeSenderFactory::class)
    private val virtualNodeSenderFactory: VirtualNodeSenderFactory,
) : VirtualNodeMaintenanceRestResource, PluggableRestResource<VirtualNodeMaintenanceRestResource>, Lifecycle {

    companion object {
        private val requiredKeys = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.REST_CONFIG)
        private val logger = contextLogger()

        private const val REGISTRATION = "REGISTRATION"
        private const val SENDER = "SENDER"
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
    }

    override val targetInterface: Class<VirtualNodeMaintenanceRestResource> = VirtualNodeMaintenanceRestResource::class.java
    override val protocolVersion: Int = 1

    private val clock = UTCClock()
    private val dependentComponents = DependentComponents.of(
        ::cpiUploadRPCOpsService
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
                        logger.warn("posting stop event on ${coordinator.name} due to RegistrationStatusChangeEvent")
                        coordinator.postEvent(StopEvent(errored = true))
                    }
                    LifecycleStatus.UP -> {
                        // Receive updates to the RPC and Messaging config
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
                    val rpcConfig = event.config.getConfig(ConfigKeys.REST_CONFIG)
                    val messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
                    val duration = Duration.ofMillis(rpcConfig.getInt(ConfigKeys.REST_ENDPOINT_TIMEOUT_MILLIS).toLong())
                    // Make sender unavailable while we're updating
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                    coordinator.createManagedResource(SENDER) {
                        virtualNodeSenderFactory.createSender(duration, messagingConfig)
                    }
                    coordinator.updateStatus(LifecycleStatus.UP)
                }
            }
        }
    }

    override fun forceCpiUpload(upload: HttpFileUpload): CpiUploadRestResource.CpiUploadResponse {
        logger.info("Force uploading CPI: ${upload.fileName}")
        if (!isRunning) throw IllegalStateException(
            "${this.javaClass.simpleName} is not running! Its status is: ${lifecycleCoordinator.status}"
        )
        val cpiUploadRequestId = cpiUploadRPCOpsService.cpiUploadManager.uploadCpi(
            upload.fileName, upload.content,
            mapOf(PropertyKeys.FORCE_UPLOAD to true.toString())
        )
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
        val resp: VirtualNodeManagementResponse = sendAndReceive(request)
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
        if (!isRunning) throw IllegalStateException(
            "${this.javaClass.simpleName} is not running! Its status is: ${lifecycleCoordinator.status}"
        )

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
