package net.corda.libs.external.messaging.entities

import com.fasterxml.jackson.annotation.JsonProperty

enum class ChannelType(val value: String) {
    @JsonProperty("send")
    SEND("send"),
    @JsonProperty("send-receive")
    SEND_RECEIVE("send-receive");

    override fun toString(): String {
        return value
    }
}