package net.corda.libs.statemanager.api

import java.time.Instant

/**
 * A state managed via the state manager.
 * Methods [equals] and [hashCode] are manually overridden due to the inconsistencies when comparing arrays, see
 * https://blog.jetbrains.com/kotlin/2015/09/feedback-request-limitations-on-data-classes/ for details.
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
    val version: Int = VERSION_INITIAL_VALUE,

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
        const val VERSION_INITIAL_VALUE: Int = 0
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

data class StateMetadata(
    val stateKey: String,
    val key: String,
    val value: String,
    val version: Int,
    val modifiedTime: Instant = Instant.now(),
)
