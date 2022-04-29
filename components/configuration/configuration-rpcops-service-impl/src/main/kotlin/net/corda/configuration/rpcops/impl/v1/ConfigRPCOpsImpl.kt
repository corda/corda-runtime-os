package net.corda.configuration.rpcops.impl.v1

import com.typesafe.config.ConfigFactory
import net.corda.configuration.rpcops.ConfigRPCOpsServiceException
import net.corda.configuration.rpcops.impl.CLIENT_NAME_HTTP
import net.corda.configuration.rpcops.impl.GROUP_NAME
import net.corda.configuration.rpcops.impl.exception.ConfigVersionException
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.endpoints.v1.ConfigRPCOps
import net.corda.libs.configuration.endpoints.v1.types.HTTPUpdateConfigRequest
import net.corda.libs.configuration.endpoints.v1.types.HTTPUpdateConfigResponse
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas.Config.Companion.CONFIG_MGMT_REQUEST_TOPIC
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.versioning.Version
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration

/** An implementation of [ConfigRPCOpsInternal]. */
@Suppress("Unused")
@Component(service = [ConfigRPCOpsInternal::class, PluggableRPCOps::class], immediate = true)
internal class ConfigRPCOpsImpl @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory
) : ConfigRPCOpsInternal, PluggableRPCOps<ConfigRPCOps> {
    private companion object {
        // The configuration used for the RPC sender.
        private val RPC_CONFIG = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_HTTP,
            CONFIG_MGMT_REQUEST_TOPIC,
            ConfigurationManagementRequest::class.java,
            ConfigurationManagementResponse::class.java
        )
        val logger = contextLogger()
    }

    private val validator = configurationValidatorFactory.createConfigValidator()
    override val targetInterface = ConfigRPCOps::class.java
    override val protocolVersion = 1
    private var rpcSender: RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>? = null
    private var requestTimeout: Duration? = null
    override val isRunning get() = rpcSender != null && requestTimeout != null

    override fun start() = Unit

    override fun stop() {
        rpcSender?.close()
        rpcSender = null
    }

    override fun createAndStartRPCSender(config: SmartConfig) {
        rpcSender?.close()
        rpcSender = publisherFactory.createRPCSender(RPC_CONFIG, config).apply { start() }
    }

    override fun setTimeout(millis: Int) {
        this.requestTimeout = Duration.ofMillis(millis.toLong())
    }

    override fun updateConfig(request: HTTPUpdateConfigRequest): HTTPUpdateConfigResponse {
        validateRequestedConfig(request)

        val actor = CURRENT_RPC_CONTEXT.get().principal
        val rpcRequest = request.run {
            ConfigurationManagementRequest(
                section,
                config,
                ConfigurationSchemaVersion(schemaVersion.major, schemaVersion.minor),
                actor,
                version
            )
        }
        val response = sendRequest(rpcRequest)
        rpcRequest.updateActor

        return if (response.success) {
            HTTPUpdateConfigResponse(
                response.section, response.config, Version(
                    response.schemaVersion.majorVersion,
                    response.schemaVersion.minorVersion
                ), response.version
            )
        } else {
            val exception = response.exception
            if (exception == null) {
                logger.warn("Configuration Management request was unsuccessful but no exception was provided.")
                throw InternalServerException("Request was unsuccessful but no exception was provided.")
            }
            logger.warn("Remote request to update config responded with exception: ${exception.errorType}: ${exception.errorMessage}")
            throw ConfigVersionException(
                exception.errorType,
                exception.errorMessage,
                response.schemaVersion,
                response.config
            )
        }
    }

    /**
     * Validates that the [request] config can be parsed into a `Config` object and that its values are valid based on the defined
     * schema for this request.
     */
    private fun validateRequestedConfig(request: HTTPUpdateConfigRequest) = try {
        val config = request.config
        val smartConfig = SmartConfigFactory.create(ConfigFactory.parseString(config)).create(ConfigFactory.parseString(config))
        val updatedConfig = validator.validate(request.section, request.schemaVersion, smartConfig)
        logger.debug { "UpdatedConfig: $updatedConfig" }
    } catch (e: Exception) {
        val message = "Configuration \"${request.config}\" could not be validated. Valid JSON or HOCON expected. Cause: ${e.message}"
        throw BadRequestException(message)
    }

    /**
     * Sends the [request] to the configuration management topic on Kafka.
     *
     * @throws ConfigRPCOpsServiceException If the updated configuration could not be published.
     */
    @Suppress("ThrowsCount")
    private fun sendRequest(request: ConfigurationManagementRequest): ConfigurationManagementResponse {
        val nonNullRPCSender = rpcSender ?: throw ConfigRPCOpsServiceException(
            "Configuration update request could not be sent as no RPC sender has been created."
        )
        val nonNullRequestTimeout = requestTimeout ?: throw ConfigRPCOpsServiceException(
            "Configuration update request could not be sent as the request timeout has not been set."
        )
        return try {
            nonNullRPCSender.sendRequest(request).getOrThrow(nonNullRequestTimeout)
        } catch (e: Exception) {
            throw ConfigRPCOpsServiceException("Could not publish updated configuration.", e)
        }
    }
}
