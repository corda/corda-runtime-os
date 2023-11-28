package net.corda.libs.external.messaging.serialization

import net.corda.libs.external.messaging.entities.RouteConfiguration

interface ExternalMessagingRouteConfigSerializer {
    fun serialize(routeConfiguration: RouteConfiguration): String

    fun deserialize(routeConfiguration: String): RouteConfiguration
}
