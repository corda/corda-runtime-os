package net.corda.libs.statemanager.impl.dto

import java.time.Instant

/**
 * DTO for states in the state manager.
 */
data class StateDto(
    val key: String,
    val state: ByteArray?,
    var version: Int,
    var metadata: String? = null,
    var modifiedTime: Instant
)