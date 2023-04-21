package net.corda.membership.registration

/**
 * Exception thrown during membership registration.
 */
class GroupPolicyGenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)