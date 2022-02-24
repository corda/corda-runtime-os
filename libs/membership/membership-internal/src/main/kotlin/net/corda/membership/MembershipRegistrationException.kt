package net.corda.membership

/**
 * Exception thrown during membership registration.
 */
class MembershipRegistrationException(message: String, cause: Throwable? = null) : Exception(message, cause)
