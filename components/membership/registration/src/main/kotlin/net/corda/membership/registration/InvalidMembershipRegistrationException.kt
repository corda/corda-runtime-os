package net.corda.membership.registration

/**
 * Exception thrown during membership registration if the registration request can not be approved.
 */
class InvalidMembershipRegistrationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
