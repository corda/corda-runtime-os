package net.corda.membership.registration

/**
 * Exception thrown during membership registration if the service is not ready to handle the
 * request at this point of time.
 */
class NotReadyMembershipRegistrationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
