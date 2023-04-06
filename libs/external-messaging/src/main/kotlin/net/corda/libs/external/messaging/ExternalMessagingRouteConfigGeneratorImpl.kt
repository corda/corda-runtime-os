package net.corda.libs.external.messaging

import net.corda.libs.external.messaging.entities.Route
import net.corda.libs.external.messaging.entities.RouteConfiguration
import net.corda.libs.external.messaging.entities.Routes
import net.corda.libs.external.messaging.serialization.ExternalMessagingChannelConfigSerializer
import net.corda.libs.external.messaging.serialization.ExternalMessagingRouteConfigSerializer
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.virtualnode.HoldingIdentity

class ExternalMessagingRouteConfigGeneratorImpl(
    private val externalMessagingConfigProvider: ExternalMessagingConfigProvider,
    private val routeConfigSerializer: ExternalMessagingRouteConfigSerializer,
    private val channelsConfigSerializer: ExternalMessagingChannelConfigSerializer
) : ExternalMessagingRouteConfigGenerator {

    override fun generateConfig(holdingId: HoldingIdentity, cpiId: CpiIdentifier, cpks: Set<CpkMetadata>): String {
        // Get the default configuration
        // cpi -> cpiMetadata.id
        // externalChannelConfig -> CpkMetadata cpiMetadata.cpks
        val routes = generateRoutes(holdingId, cpks)
        return routeConfigSerializer.serialize(
            RouteConfiguration(
                Routes(cpiId, routes),
                emptyList()
            )
        )
    }

    private fun generateRoutes(
        holdingId: HoldingIdentity,
        cpks: Set<CpkMetadata>
    ): List<Route> {
        val topicRoutes = cpks.mapNotNull { it.externalChannelsConfig }.map { externalChannelsConfigJsonStr ->

            val externalChannelsConfig = channelsConfigSerializer.deserialize(externalChannelsConfigJsonStr)

            externalChannelsConfig.channels.map { channelConfig ->
                val defaultConfig = externalMessagingConfigProvider.getDefaults()
                val topicPattern = defaultConfig.receiveTopicPattern

                    .replace("\$HOLDING_ID\$", holdingId.shortHash.toString())
                    .replace("\$CHANNEL_NAME\$", channelConfig.name)

                Route(
                    channelConfig.name,
                    topicPattern,
                    defaultConfig.isActive,
                    defaultConfig.inactiveResponseType
                )
            }
        }.flatten()

        return topicRoutes
    }
}