package net.corda.membership.registration

/**
 * Exception thrown during membership registration if the registration request is invalid.
 */
class InvalidMembershipRegistrationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
