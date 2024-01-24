package net.corda.messaging.mediator.slim

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.messagebus.api.CordaTopicPartition

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class TopicOffset(
    @JsonProperty("topicPartition")
    val topicPartition: CordaTopicPartition,
    @JsonProperty("offset")
    val offset: Long
)
