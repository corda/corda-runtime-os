package net.corda.membership.lib.registration

import net.corda.data.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import java.util.UUID

object RegistrationRequestHelpers {

    /**
     * Returns the pre-auth token from the registration request if it is present.
     *
     * @throws IllegalArgumentException if value is present but is not a valid UUID
     */
    @Throws(IllegalArgumentException::class)
    fun RegistrationRequest.getPreAuthToken(
        keyValuePairDeserializer: CordaAvroDeserializer<KeyValuePairList>
    ): UUID? {
        return keyValuePairDeserializer.deserialize(registrationContext.data.array())?.items?.find {
            it.key == PRE_AUTH_TOKEN
        }?.let {
            UUID.fromString(it.value)
        }
    }
}
