package net.corda.libs.permission

import net.corda.lifecycle.Lifecycle
import java.time.Instant

interface PermissionService : Lifecycle {
    fun authorizeUser(
            requestId: String,
            requestUrl: String,
            loginName: String,
            timeoutTimestamp: Instant
    ): Boolean
}