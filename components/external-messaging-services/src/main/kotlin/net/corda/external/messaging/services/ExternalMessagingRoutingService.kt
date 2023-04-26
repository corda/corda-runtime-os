package net.corda.external.messaging.services

import net.corda.external.messaging.entities.VerifiedRoute
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.external.messaging.entities.Route

/**
 * The [ExternalMessagingRoutingService] provides an API for requesting external messaging [Route] configuration
 */
interface ExternalMessagingRoutingService {

    fun onConfigChange(config: Map<String, SmartConfig>)

    fun getRoute(holdingIdentity:String, channelName:String): VerifiedRoute?
}

