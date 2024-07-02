package net.corda.membership.impl.rest.v1

import net.corda.rest.exception.ExceptionDetails
import net.corda.rest.exception.InvalidInputDataException
import java.util.UUID

internal fun parseRegistrationRequestId(requestId: String): UUID {
    return try {
        UUID.fromString(requestId)
    } catch (e: IllegalArgumentException) {
        throw InvalidInputDataException(
            title = "'$requestId' is not a valid registration request ID.",
            details = mapOf("registrationRequestId" to requestId),
            exceptionDetails = ExceptionDetails(e::class.java.name, "${e.message}")
        )
    }
}
