package net.corda.configuration.rpcops.impl.v1

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationGetService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.rpcops.impl.CLIENT_NAME_HTTP
import net.corda.configuration.rpcops.impl.GROUP_NAME
import net.corda.configuration.rpcops.impl.exception.ConfigException
import net.corda.configuration.rpcops.impl.exception.ConfigRPCOpsException
import net.corda.configuration.rpcops.impl.exception.ConfigVersionConflictException
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.response.ResponseEntity
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.endpoints.v1.ConfigRPCOps
import net.corda.libs.configuration.endpoints.v1.types.ConfigSchemaVersion
import net.corda.libs.configuration.endpoints.v1.types.GetConfigResponse
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigParameters
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigResponse
import net.corda.libs.configuration.exception.WrongConfigVersionException
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.exception.CordaRPCAPIPartitionException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas.Config.Companion.CONFIG_MGMT_REQUEST_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.VisibleForTesting
import net.corda.utilities.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.versioning.Version
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration

/** An implementation of [ConfigRPCOps]. */
@Suppress("Unused")
@Component(service = [PluggableRPCOps::class])
internal class ConfigRPCOpsImpl @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory,
    @Reference(service = ConfigurationGetService::class)
    private val configurationGetService: ConfigurationGetService
) : ConfigRPCOps, PluggableRPCOps<ConfigRPCOps>, Lifecycle {
    private companion object {
        // The configuration used for the RPC sender.
        val RPC_CONFIG = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_HTTP,
            CONFIG_MGMT_REQUEST_TOPIC,
            ConfigurationManagementRequest::class.java,
            ConfigurationManagementResponse::class.java
        )
        val logger = contextLogger()

        const val REGISTRATION = "REGISTRATION"
        const val SENDER = "SENDER"
        const val CONFIG_HANDLE = "CONFIG_HANDLE"

        val requiredKeys = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.REST_CONFIG)
    }

    private val validator = configurationValidatorFactory.createConfigValidator()

    override val targetInterface = ConfigRPCOps::class.java
    override val protocolVersion = 1

    private var requestTimeout: Duration? = null
    private var rpcSender: RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>? = null

    // Lifecycle
    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<ConfigRPCOps> { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
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
                    val rpcConfig = event.config.getConfig(ConfigKeys.REST_CONFIG)
                    val messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
                    setTimeout(rpcConfig.getInt(ConfigKeys.REST_ENDPOINT_TIMEOUT_MILLIS))
                    // Make sender unavailable while we're updating
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                    coordinator.createManagedResource(SENDER) {
                        createAndStartRPCSender(messagingConfig)
                        object : Resource {
                            override fun close() {
                                rpcSender?.close()
                            }
                        }
                    }
                    coordinator.updateStatus(LifecycleStatus.UP)
                }
            }
        }

    @VisibleForTesting
    internal fun createAndStartRPCSender(messagingConfig: SmartConfig) {
        rpcSender?.close()
        rpcSender = publisherFactory.createRPCSender(RPC_CONFIG, messagingConfig).apply { start() }
    }

    @VisibleForTesting
    internal fun setTimeout(millis: Int) {
        this.requestTimeout = Duration.ofMillis(millis.toLong())
    }

    override fun updateConfig(request: UpdateConfigParameters): ResponseEntity<UpdateConfigResponse> {
        validateRequestedConfig(request)

        val actor = CURRENT_RPC_CONTEXT.get().principal
        val rpcRequest = request.run {
            ConfigurationManagementRequest(
                section,
                config.escapedJson,
                ConfigurationSchemaVersion(schemaVersion.major, schemaVersion.minor),
                actor,
                version
            )
        }
        val response = sendRequest(rpcRequest)

        return if (response.success) {
            ResponseEntity.accepted(UpdateConfigResponse(
                response.section, response.config, ConfigSchemaVersion(
                    response.schemaVersion.majorVersion,
                    response.schemaVersion.minorVersion
                ), response.version
            ))
        } else {
            val exception = response.exception
            if (exception == null) {
                logger.warn("Configuration Management request was unsuccessful but no exception was provided.")
                throw InternalServerException("Request was unsuccessful but no exception was provided.")
            }
            logger.warn("Remote request to update config responded with exception: ${exception.errorType}: ${exception.errorMessage}")

            when (exception.errorType) {
                WrongConfigVersionException::class.java.name -> throw ConfigVersionConflictException(
                    exception.errorType,
                    exception.errorMessage,
                    response.schemaVersion,
                    response.config)
                else -> throw ConfigException(
                    exception.errorType,
                    exception.errorMessage,
                    response.schemaVersion,
                    response.config
                )
            }
        }
    }

    override fun get(section: String): GetConfigResponse {
        val config = configurationGetService.get(section)
            ?: throw ResourceNotFoundException(
                "Configuration for section '${section} not found."
            )
        return GetConfigResponse(
            section,
            config.source,
            config.value,
            ConfigSchemaVersion(config.schemaVersion.majorVersion, config.schemaVersion.minorVersion),
            config.version
        )
    }

    /**
     * Validates that the [request] config can be parsed into a `Config` object and that its values are valid based on the defined
     * schema for this request.
     */
    private fun validateRequestedConfig(request: UpdateConfigParameters) = try {
        val config = request.config.escapedJson
        val smartConfig = SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.parseString(config))
        val updatedConfig = validator.validate(
            request.section,
            Version(request.schemaVersion.major, request.schemaVersion.minor),
            smartConfig
        )
        logger.debug { "UpdatedConfig: $updatedConfig" }
    } catch (e: Exception) {
        val message =
            "Configuration \"${request.config.escapedJson}\" could not be validated. " +
                    "Valid JSON or HOCON expected. Cause: ${e.message}"
        throw BadRequestException(message)
    }

    /**
     * Sends the [request] to the configuration management topic on Kafka.
     *
     * @throws ConfigRPCOpsException If the updated configuration could not be published.
     */
    @Suppress("ThrowsCount")
    private fun sendRequest(request: ConfigurationManagementRequest): ConfigurationManagementResponse {
        val nonNullRPCSender = rpcSender ?: throw ConfigRPCOpsException(
            "Configuration update request could not be sent as no RPC sender has been created."
        )
        val nonNullRequestTimeout = requestTimeout ?: throw ConfigRPCOpsException(
            "Configuration update request could not be sent as the request timeout has not been set."
        )
        return try {
            nonNullRPCSender.sendRequest(request).getOrThrow(nonNullRequestTimeout)
        } catch (ex: CordaRPCAPIPartitionException) {
            logger.warn("Partition event when getting response from db worker for update config message", ex)
            //TODO - https://r3-cev.atlassian.net/browse/CORE-7930
            ConfigurationManagementResponse(true, null, request.section, request.config, request.schemaVersion, request.version+1)
        } catch (e: Exception) {
            throw ConfigRPCOpsException("Could not publish updated configuration.", e)
        }
    }

    // Mandatory lifecycle methods - default to coordinator
    override val isRunning get() = lifecycleCoordinator.isRunning
    override fun start() = lifecycleCoordinator.start()
    override fun stop() = lifecycleCoordinator.stop()
}
