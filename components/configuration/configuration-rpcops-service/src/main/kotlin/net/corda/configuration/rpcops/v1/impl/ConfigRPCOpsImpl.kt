package net.corda.configuration.rpcops.v1.impl

import net.corda.configuration.rpcops.v1.ConfigRPCOps
import net.corda.configuration.rpcops.v1.types.ConfigResponseType
import net.corda.configuration.rpcops.v1.types.UpdateConfigType
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
    override var rpcSender: RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>? = null

    // TODO - Joel - Set this timeout based on the configuration.
    private val requestTimeout = Duration.ofMillis(10000L)

    override fun updateConfig(updateConfigType: UpdateConfigType): ConfigResponseType {
        println("JJJ - in rpc ops impl $this")
        // TODO - Joel - If rpcSender is null, throw exception like `ResourceNotFoundException`.
        val notNullRPCSender = rpcSender ?: return ConfigResponseType("rpc sender not set up")

        val req = ConfigurationManagementRequest("blah", 1, "jah", 2, "hah")
        val resp = notNullRPCSender.sendRequest(req).getOrThrow(requestTimeout)

        val response = resp.response
        if (response is ExceptionEnvelope) {
            // TODO - Joel - Use properties of exception envelope to throw meaningful exception.
            response.errorMessage
            response.errorType
            resp.currentConfiguration
            resp.currentVersion
        }

        // TODO - Joel - Return proper response object.
        return ConfigResponseType(resp.currentConfiguration)
    }
}