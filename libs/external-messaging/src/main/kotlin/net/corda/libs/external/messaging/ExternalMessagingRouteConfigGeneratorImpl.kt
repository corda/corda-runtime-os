package net.corda.libs.external.messaging

import net.corda.libs.external.messaging.entities.Route
import net.corda.libs.external.messaging.entities.RouteConfiguration
import net.corda.libs.external.messaging.entities.Routes
import net.corda.libs.external.messaging.serialization.ExternalMessagingChannelConfigSerializer
import net.corda.libs.external.messaging.serialization.ExternalMessagingRouteConfigSerializer
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo

class ExternalMessagingRouteConfigGeneratorImpl(
    private val externalMessagingConfigProvider: ExternalMessagingConfigProvider,
    private val routeConfigSerializer: ExternalMessagingRouteConfigSerializer,
    private val channelsConfigSerializer: ExternalMessagingChannelConfigSerializer
) : ExternalMessagingRouteConfigGenerator {

    /**
     * The method generates the configuration for external messaging.
     * N.B.: Please keep in mind that any historical configuration is lost when this method is called.
     */
    override fun generateNewConfig(
        holdingId: HoldingIdentity,
        cpiId: CpiIdentifier,
        cpks: Collection<CpkMetadata>
    ): String? {
        return generateConfig(holdingId, cpiId, cpks, emptyList())
    }

    /**
     * The method generates the configuration for external messaging.
     * N.B.: Please keep in mind that the historical configuration is preserved when this method is called.
     */
    override fun generateUpgradeConfig(
        virtualNode: VirtualNodeInfo,
        cpiId: CpiIdentifier,
        cpks: Collection<CpkMetadata>
    ): String? {
        if (virtualNode.externalMessagingRouteConfig == null) {
            // Before the upgrade there was no configuration for the external messaging route.
            // Create the configuration from scratch
            return generateNewConfig(virtualNode.holdingIdentity, cpiId, cpks)
        }

        // There is already configuration for the external messaging route.
        // Generate the new configuration but keep record of the previous one.
        val externalMessagingRouteConfig = routeConfigSerializer.deserialize(virtualNode.externalMessagingRouteConfig!!)

        return generateConfig(
            virtualNode.holdingIdentity,
            cpiId,
            cpks,
            listOf(externalMessagingRouteConfig.currentRoutes) + externalMessagingRouteConfig.previousVersionRoutes
        )
    }

    private fun generateConfig(
        holdingId: HoldingIdentity,
        cpiId: CpiIdentifier,
        cpks: Collection<CpkMetadata>,
        previousVersionRoutes: List<Routes>
    ): String? {
        val routes = generateRoutes(holdingId, cpks)

        if (routes.isEmpty()) {
            return null
        }

        return routeConfigSerializer.serialize(
            RouteConfiguration(
                Routes(cpiId, routes),
                previousVersionRoutes
            )
        )
    }

    private fun generateRoutes(
        holdingId: HoldingIdentity,
        cpks: Collection<CpkMetadata>
    ): List<Route> {
        val topicRoutes = cpks.mapNotNull { it.externalChannelsConfig }.map { externalChannelsConfigJsonStr ->

            val externalChannelsConfig = channelsConfigSerializer.deserialize(externalChannelsConfigJsonStr)

            externalChannelsConfig.channels.map { channelConfig ->
                val defaultConfig = externalMessagingConfigProvider.getDefaults()
                val topicPattern = defaultConfig.receiveTopicPattern
                    .replace("\$HOLDING_ID", holdingId.shortHash.toString())
                    .replace("\$CHANNEL_NAME", channelConfig.name)

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
