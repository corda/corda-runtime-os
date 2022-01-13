package net.corda.configuration.rpcops.impl.v1

import net.corda.configuration.rpcops.ConfigRPCOpsServiceException
import net.corda.configuration.rpcops.impl.CLIENT_NAME_HTTP
import net.corda.configuration.rpcops.impl.GROUP_NAME
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.HttpApiException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.endpoints.v1.ConfigRPCOps
import net.corda.libs.configuration.endpoints.v1.types.HTTPUpdateConfigRequest
import net.corda.libs.configuration.endpoints.v1.types.HTTPUpdateConfigResponse
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.schema.Schemas.Config.Companion.CONFIG_MGMT_REQUEST_TOPIC
import net.corda.v5.base.concurrent.getOrThrow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration

/** An implementation of [ConfigRPCOpsInternal]. */
@Suppress("Unused")
@Component(service = [ConfigRPCOpsInternal::class, PluggableRPCOps::class], immediate = true)
internal class ConfigRPCOpsImpl @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
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
    }

    override val targetInterface = ConfigRPCOps::class.java
    override val protocolVersion = 1
    private var rpcSender: RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>? = null
    private var requestTimeout: Duration? = null
    override val isRunning = rpcSender != null && requestTimeout != null

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
        val actor = CURRENT_RPC_CONTEXT.get().principal
        val rpcRequest = request.run { ConfigurationManagementRequest(section, config, schemaVersion, actor, version) }
        val response = sendRequest(rpcRequest)

        return if (response.success) {
            HTTPUpdateConfigResponse(response.section, response.config, response.schemaVersion, response.version)
        } else {
            val exception = response.exception
                ?: throw HttpApiException("Request was unsuccessful but no exception was provided.", 500)
            // TODO - CORE-3304 - Return richer exception (e.g. containing the config and version currently in the DB).
            throw HttpApiException("${exception.errorType}: ${exception.errorMessage}", 500)
        }
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