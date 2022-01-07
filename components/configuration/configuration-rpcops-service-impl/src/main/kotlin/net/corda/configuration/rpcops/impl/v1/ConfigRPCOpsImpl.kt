package net.corda.configuration.rpcops.impl.v1

import net.corda.configuration.rpcops.impl.v1.types.HTTPUpdateConfigRequest
import net.corda.configuration.rpcops.impl.v1.types.HTTPUpdateConfigResponse
import net.corda.data.ExceptionEnvelope
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.httprpc.PluggableRPCOps
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
class ConfigRPCOpsImpl @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : ConfigRPCOps, PluggableRPCOps<ConfigRPCOps> {
    private companion object {
        // TODO - Joel - Describe.
        private val rpcConfig = RPCConfig(
            "config.management",
            "config.manager.client",
            CONFIG_MGMT_REQUEST_TOPIC,
            ConfigurationManagementRequest::class.java,
            ConfigurationManagementResponse::class.java
        )
    }

    override val targetInterface = ConfigRPCOps::class.java
    override val protocolVersion = 1
    private val requestTimeout = Duration.ofMillis(10000L)
    private var rpcSender: RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>? = null

    override fun start(config: SmartConfig) {
        rpcSender?.close()
        rpcSender = publisherFactory.createRPCSender(rpcConfig, config).apply { start() }
    }

    override fun updateConfig(req: HTTPUpdateConfigRequest): HTTPUpdateConfigResponse {
        // TODO - Joel - Work out how to determine update actor.
        val resp = sendRequest(req.toRPCRequest("todo - joel"))

        // TODO - Joel - Retry if version is wrong, or at least flag it.

        val status = resp.status
        if (status is ExceptionEnvelope) {
            // TODO - Joel - Use properties of exception envelope to throw meaningful exception.
            status.errorMessage
            status.errorType
            resp.currentConfiguration
            resp.currentVersion
        }

        // TODO - Joel - Return proper response object.
        return HTTPUpdateConfigResponse(resp.currentConfiguration)
    }

    override fun close() {
        rpcSender?.close()
        rpcSender = null
    }

    // TODo - Joel - Describe.
    private fun sendRequest(request: ConfigurationManagementRequest): ConfigurationManagementResponse {
        // TODO - Joel - Handle rpcSender being null.
        return rpcSender?.sendRequest(request)?.getOrThrow(requestTimeout)!!
    }
}