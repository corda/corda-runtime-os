package net.corda.libs.virtualnode.endpoints.v1.types.external.messaging

data class Route(
    val channelName: String,
    val externalReceiveTopicName: String,
    val active: Boolean,
    val inactiveResponseType: InactiveResponseType
)
