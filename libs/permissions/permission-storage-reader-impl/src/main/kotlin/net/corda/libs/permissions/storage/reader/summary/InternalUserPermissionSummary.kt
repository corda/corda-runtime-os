package net.corda.libs.permissions.storage.reader.summary

import net.corda.permissions.query.dto.InternalPermissionQueryDto
import java.time.Instant

/**
 * Internal permission summary object holding a list of permission query DTOs summarizing the permissions read from data storage for each
 * user. Includes when the summary was calculated.
 */
data class InternalUserPermissionSummary(
    val loginName: String,
    val enabled: Boolean,
    val permissions: List<InternalPermissionQueryDto>,
    val lastUpdateTimestamp: Instant
)
