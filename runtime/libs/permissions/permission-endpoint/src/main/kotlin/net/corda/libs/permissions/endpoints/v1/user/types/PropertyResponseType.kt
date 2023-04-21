package net.corda.libs.permissions.endpoints.v1.user.types

import java.time.Instant

/**
 * Response type representing a Property to be returned to the caller.
 */
data class PropertyResponseType(
    /**
     * Timestamp when this property was last changed.
     */
    val lastChangedTimestamp: Instant,

    /**
     * The property key.
     */
    val key: String,

    /**
     * The property value.
     */
    val value: String,
)