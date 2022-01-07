package net.corda.configuration.rpcops.impl.v1.impl

import net.corda.configuration.rpcops.impl.ConfigRPCOpsRPCSender
import net.corda.configuration.rpcops.impl.v1.ConfigRPCOps
import net.corda.configuration.rpcops.impl.v1.types.UpdateConfigResponse
import net.corda.configuration.rpcops.impl.v1.types.UpdateConfigRequest
import net.corda.data.ExceptionEnvelope
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.httprpc.PluggableRPCOps
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** An implementation of [ConfigRPCOps]. */
@Suppress("Unused")
@Component(service = [ConfigRPCOps::class, PluggableRPCOps::class], immediate = true)
class ConfigRPCOpsImpl @Activate constructor(
    @Reference(service = ConfigRPCOpsRPCSender::class)
    private val configRPCOpsRpcSender: ConfigRPCOpsRPCSender
) : ConfigRPCOps, PluggableRPCOps<ConfigRPCOps> {
    override val targetInterface = ConfigRPCOps::class.java
    override val protocolVersion = 1

    override fun updateConfig(updateConfigRequest: UpdateConfigRequest): UpdateConfigResponse {
        // TODO - Joel - Build the request based on the argument.
        val req = ConfigurationManagementRequest("blah", 1, "lah", 2, "hah")
        val resp = configRPCOpsRpcSender.sendRequest(req)

        // TODO - Joel - Retries if version is wrong, or at least flag it.

        val status = resp.status
        if (status is ExceptionEnvelope) {
            // TODO - Joel - Use properties of exception envelope to throw meaningful exception.
            status.errorMessage
            status.errorType
            resp.currentConfiguration
            resp.currentVersion
        }

        // TODO - Joel - Return proper response object.
        return UpdateConfigResponse(resp.currentConfiguration)
    }
}