package net.corda.p2p.linkmanager.tracker

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

internal data class TrackedMessageState(
    @JsonProperty("id")
    val messageId: String,
    @JsonProperty("ts")
    val timeStamp: Instant,
    @JsonProperty("p")
    val persisted: Boolean,
)
