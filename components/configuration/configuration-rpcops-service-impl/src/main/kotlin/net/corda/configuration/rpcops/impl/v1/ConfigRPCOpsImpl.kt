package net.corda.configuration.rpcops.impl.v1

import net.corda.configuration.rpcops.ConfigRPCOpsServiceException
import net.corda.configuration.rpcops.impl.CLIENT_NAME_HTTP
import net.corda.configuration.rpcops.impl.GROUP_NAME
import net.corda.configuration.rpcops.impl.v1.types.HTTPUpdateConfigRequest
import net.corda.configuration.rpcops.impl.v1.types.HTTPUpdateConfigResponse
import net.corda.data.ExceptionEnvelope
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.HttpApiException
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.schema.Schemas.Config.Companion.CONFIG_MGMT_REQUEST_TOPIC
import net.corda.v5.base.concurrent.getOrThrow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration

/** An implementation of [ConfigRPCOps]. */
@Suppress("Unused")
@Component(service = [ConfigRPCOps::class, PluggableRPCOps::class], immediate = true)
internal class ConfigRPCOpsImpl @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : ConfigRPCOps, PluggableRPCOps<ConfigRPCOps> {
    private companion object {
        // The configuration used for the RPC sender.
        private val rpcConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_HTTP,
            CONFIG_MGMT_REQUEST_TOPIC,
            ConfigurationManagementRequest::class.java,
            ConfigurationManagementResponse::class.java
        )
    }

    override val targetInterface = ConfigRPCOps::class.java
    override val protocolVersion = 1
    // TODO - Joel - Make this configurable.
    private val requestTimeout = Duration.ofMillis(10000L)
    // The RPC sender that handles incoming configuration management requests.
    private var rpcSender: RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>? = null

    override fun start(config: SmartConfig) {
        rpcSender?.close()
        rpcSender = publisherFactory.createRPCSender(rpcConfig, config).apply { start() }
    }

    override fun updateConfig(req: HTTPUpdateConfigRequest): HTTPUpdateConfigResponse {
        // TODO - Joel - Work out how to determine update actor.
        val resp = sendRequest(req.toRPCRequest("todo - joel"))

        val status = resp.status
        return if (status is ExceptionEnvelope) {
            throw HttpApiException("${status.errorType}: ${status.errorMessage}", 500)
        } else {
            HTTPUpdateConfigResponse(true)
        }
    }

    override fun close() {
        rpcSender?.close()
        rpcSender = null
    }

    /**
     * Sends the [request] to the configuration management topic on Kafka.
     *
     * @throws ConfigRPCOpsServiceException If the RPC sender is not started.
     * @throws Exception If an exception is thrown when attempting to get the future returned by the request.
     */
    private fun sendRequest(request: ConfigurationManagementRequest): ConfigurationManagementResponse {
        val nonNullRPCSender = rpcSender ?: throw ConfigRPCOpsServiceException(
            "Configuration update request could not be sent as the RPC sender has not been started.")
        return nonNullRPCSender.sendRequest(request).getOrThrow(requestTimeout)
    }
}