package net.corda.libs.permissions.manager.response

import java.time.Instant

/**
 * Response object containing information for a Property.
 */
data class PropertyResponseDto(
    val lastChangedTimestamp: Instant,
    val key: String,
    val value: String,
)