package net.corda.libs.external.messaging.serialization

import net.corda.libs.external.messaging.entities.ExternalMessagingChannelsConfig

interface ExternalMessagingChannelConfigSerializer {
    fun deserialize(channelsConfig: String): ExternalMessagingChannelsConfig
}
