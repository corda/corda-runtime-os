package net.corda.libs.statemanager.api

import java.time.Instant

/**
 * A state managed via the state manager.
 */
data class State<S>(
    /**
     * The typed state itself.
     */
    val state: S,

    /**
     * Identifier for the state.
     */
    val key: String,

    /**
     * Arbitrary Map of primitive types that can be used to store and query extra data associated with the state
     * that must generally not be part of the state itself.
     */
    val metadata: Metadata<Any> = Metadata(),

    /**
     * Version of the state.
     */
    val version: Int = -1,

    /**
     * Time when the state was last modified.
     */
    val modifiedTime: Instant = Instant.now(),
)
