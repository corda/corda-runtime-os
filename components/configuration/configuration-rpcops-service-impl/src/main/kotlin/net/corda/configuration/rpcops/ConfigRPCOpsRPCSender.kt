package net.corda.configuration.rpcops

import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow
import org.osgi.service.component.annotations.Component
import java.time.Duration

// TODO - Joel - Put this back into `ConfigRPCOpsImpl`.
@Component(service = [ConfigRPCOpsRPCSender::class])
class ConfigRPCOpsRPCSender {
    // TODO - Joel - Set this timeout based on the configuration.
    private val requestTimeout = Duration.ofMillis(10000L)

    var rpcSender: RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>? = null

    fun sendRequest(request: ConfigurationManagementRequest) =
        // TODO - Joel - Handle rpcSender being null.
        rpcSender?.sendRequest(request)?.getOrThrow(requestTimeout)!!
}