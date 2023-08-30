package net.corda.libs.statemanager.impl.dto

import java.time.Instant

/**
 * DTO for states in the state manager.
 */
data class StateDto(
    val key: String,
    val state: ByteArray,
    var version: Int,
    var metadata: String,
    var modifiedTime: Instant
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StateDto

        if (key != other.key) return false
        if (!state.contentEquals(other.state)) return false
        if (version != other.version) return false
        if (metadata != other.metadata) return false
        if (modifiedTime != other.modifiedTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + state.contentHashCode()
        result = 31 * result + version
        result = 31 * result + metadata.hashCode()
        result = 31 * result + modifiedTime.hashCode()

        return result
    }
}
