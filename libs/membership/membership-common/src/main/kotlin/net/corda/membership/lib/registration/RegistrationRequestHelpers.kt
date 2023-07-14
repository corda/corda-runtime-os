package net.corda.membership.lib.registration

import net.corda.data.membership.common.RegistrationRequestDetails
import java.util.UUID

object RegistrationRequestHelpers {

    /**
     * Returns the pre-auth token from the registration request [RegistrationRequestDetails] if it is present.
     *
     * @throws IllegalArgumentException if value is present but is not a valid UUID
     */
    @Throws(IllegalArgumentException::class)
    fun RegistrationRequestDetails.getPreAuthToken(
    ): UUID? {
        return registrationContext?.items?.find {
            it.key == PRE_AUTH_TOKEN
        }?.let {
            UUID.fromString(it.value)
        }
    }
}
