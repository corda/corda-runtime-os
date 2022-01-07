package net.corda.configuration.rpcops.impl.v1

import net.corda.configuration.rpcops.impl.v1.types.HTTPUpdateConfigRequest
import net.corda.configuration.rpcops.impl.v1.types.HTTPUpdateConfigResponse
import net.corda.data.ExceptionEnvelope
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.httprpc.PluggableRPCOps
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow
import org.osgi.service.component.annotations.Component
import java.time.Duration

/** An implementation of [ConfigRPCOps]. */
@Suppress("Unused")
@Component(service = [ConfigRPCOps::class, PluggableRPCOps::class], immediate = true)
class ConfigRPCOpsImpl : ConfigRPCOps, PluggableRPCOps<ConfigRPCOps> {
    override val targetInterface = ConfigRPCOps::class.java
    override val protocolVersion = 1
    private val requestTimeout = Duration.ofMillis(10000L)

    override var rpcSender: RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>? = null

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
    }

    // TODo - Joel - Describe.
    private fun sendRequest(request: ConfigurationManagementRequest): ConfigurationManagementResponse {
        // TODO - Joel - Handle rpcSender being null.
        return rpcSender?.sendRequest(request)?.getOrThrow(requestTimeout)!!
    }
}