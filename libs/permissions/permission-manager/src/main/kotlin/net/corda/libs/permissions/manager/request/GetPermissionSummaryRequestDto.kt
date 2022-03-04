package net.corda.libs.permissions.manager.request

/**
 * Request object for getting a Permission summary for a user.
 */
data class GetPermissionSummaryRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * ID of the user to find permissions.
     */
    val userLogin: String
)