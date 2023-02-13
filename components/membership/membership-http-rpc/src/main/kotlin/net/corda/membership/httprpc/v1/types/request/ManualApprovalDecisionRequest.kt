package net.corda.membership.httprpc.v1.types.request

/**
 * Reason for declining a registration request.
 */
data class ManualDeclinationReason(val reason: String)
