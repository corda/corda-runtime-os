package net.corda.p2p.linkmanager.tracker

import java.time.Instant

internal data class TrackedMessageState(
    val messageId: String,
    val timeStamp: Instant,
    val persisted: Boolean,
)
