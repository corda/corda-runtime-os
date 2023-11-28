package net.corda.libs.external.messaging.serialization

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.libs.external.messaging.entities.ExternalMessagingChannelsConfig

class ExternalMessagingChannelConfigSerializerImpl : ExternalMessagingChannelConfigSerializer {
    val mapper = jacksonObjectMapper()

    override fun deserialize(channelsConfig: String): ExternalMessagingChannelsConfig {
        return mapper.readValue(channelsConfig, ExternalMessagingChannelsConfig::class.java)
    }
}
