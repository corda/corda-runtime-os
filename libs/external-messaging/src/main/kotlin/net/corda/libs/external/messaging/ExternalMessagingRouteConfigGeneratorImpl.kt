package net.corda.libs.external.messaging

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.external.messaging.entities.ExternalMessagingChannelsConfig
import net.corda.libs.external.messaging.entities.InactiveResponseType
import net.corda.libs.external.messaging.entities.Route
import net.corda.libs.external.messaging.entities.RouteConfiguration
import net.corda.libs.external.messaging.entities.Routes
import net.corda.libs.external.messaging.serialization.ExternalMessagingRouteConfigSerializerImpl
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.schema.configuration.ExternalMessagingConfig
import net.corda.virtualnode.HoldingIdentity

// Todo: This class probably should exist elsewhere
data class ExternalMessagingConfigDefaults(
    val receiveTopicPattern: String,
    val isActive: Boolean,
    val inactiveResponseType: InactiveResponseType
)


class ExternalMessagingRouteConfigGeneratorImpl(private val externalMessagingConfigDefaults: ExternalMessagingConfigDefaults) :
    ExternalMessagingRouteConfigGenerator {

    constructor(defaultConfig: SmartConfig) :
            this(
                // Todo: The following conversion should be placed in a more generic place
                ExternalMessagingConfigDefaults(
                    defaultConfig.getString(ExternalMessagingConfig.EXTERNAL_MESSAGING_RECEIVE_TOPIC_PATTERN),
                    defaultConfig.getBoolean(ExternalMessagingConfig.EXTERNAL_MESSAGING_ACTIVE),
                    defaultConfig.getEnum(
                        InactiveResponseType::class.java,
                        ExternalMessagingConfig.EXTERNAL_MESSAGING_INTERACTIVE_RESPONSE_TYPE
                    )
                )
            )

    // Todo
    // This function probably should return List<Route> instead of a list
    // The data should be then serialized when storing to the database
    override fun generateConfig(holdingId: HoldingIdentity, cpiId: CpiIdentifier, cpks: Set<CpkMetadata>): String {
        // Get the default configuration
        // cpi -> cpiMetadata.id
        // externalChannelConfig -> CpkMetadata cpiMetadata.cpks
        val routes = generateRoutes(holdingId, cpks)
        return ExternalMessagingRouteConfigSerializerImpl().serialize(
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
        val topicRoutes = cpks.map { cpkMetadata ->
            val mapper = jacksonObjectMapper()
            val externalChannelsConfig =
                mapper.readValue(cpkMetadata.externalChannelsConfig, ExternalMessagingChannelsConfig::class.java)

            externalChannelsConfig.channels.map { channelConfig ->
                val topicPattern =  externalMessagingConfigDefaults.receiveTopicPattern
                    .replace("\$HOLDING_ID\$",holdingId.shortHash.toString())
                    .replace("\$CHANNEL_NAME\$", channelConfig.name)

                Route(
                    channelConfig.name,
                    topicPattern,
                    externalMessagingConfigDefaults.isActive,
                    externalMessagingConfigDefaults.inactiveResponseType
                )
            }
        }.flatten()

        return topicRoutes
    }
}