package net.corda.libs.external.messaging.entities

import com.fasterxml.jackson.annotation.JsonProperty

data class Route(
    @JsonProperty("channelName")
    val channelName:String,
    @JsonProperty("externalReceiveTopicName")
    val externalReceiveTopicName:String,
    @JsonProperty("active")
    val active:Boolean,
    @JsonProperty("inactiveResponseType")
    val inactiveResponseType: InactiveResponseType
)
