package net.corda.libs.statemanager

/**
 * A state managed via the state manager.
 */
data class State<S>(
    /**
     * The typed state itself.
     */
    val state: S?,

    /**
     * Identifier for the state.
     */
    val key: String,

    /**
     * Version of the state.
     */
    val version: Int,

    /**
     * Time when the state was last modified.
     */
    val modifiedTime: Long,

    /**
     * Key/value pairs that map to a JSON.
     */
    val metadata: MutableMap<String, String>?,
)