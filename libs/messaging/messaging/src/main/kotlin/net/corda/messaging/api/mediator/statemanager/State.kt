package net.corda.messaging.api.mediator.statemanager

import java.time.Instant

/**
 * A state managed via the state manager.
 */
data class State(
    /**
     * Identifier for the state.
     */
    val key: String,

    /**
     * The actual value of the state.
     */
    val value: ByteArray,

    /**
     * Version of the state.
     */
    val version: Int = INITIAL_VERSION,

    /**
     * Arbitrary Map of primitive types that can be used to store and query data associated with the state.
     */
    val metadata: Metadata = Metadata(),

    /**
     * Time when the state was last modified.
     */
    val modifiedTime: Instant = Instant.now(),
) {

    companion object {
        const val INITIAL_VERSION = -1
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as State

        if (key != other.key) return false
        if (!value.contentEquals(other.value)) return false
        if (version != other.version) return false
        if (metadata != other.metadata) return false
        if (modifiedTime != other.modifiedTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + value.contentHashCode()
        result = 31 * result + version
        result = 31 * result + metadata.hashCode()
        result = 31 * result + modifiedTime.hashCode()

        return result
    }
}