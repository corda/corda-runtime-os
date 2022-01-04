package net.corda.configuration.rpcops.v1.impl

import net.corda.configuration.rpcops.ConfigRPCOpsRPCSender
import net.corda.configuration.rpcops.v1.ConfigRPCOps
import net.corda.configuration.rpcops.v1.types.ConfigResponseType
import net.corda.configuration.rpcops.v1.types.UpdateConfigType
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

    override fun updateConfig(updateConfigType: UpdateConfigType): ConfigResponseType {
        val req = ConfigurationManagementRequest("blah", 1, "lah", 2, "hah")
        val resp = configRPCOpsRpcSender.sendRequest(req)

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