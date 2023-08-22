package net.corda.membership.lib.registration

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.membership.lib.ContextDeserializationException
import net.corda.membership.lib.deserializeContext
import java.util.UUID

object RegistrationRequestHelpers {

    /**
     * Returns the pre-auth token from the registration request [RegistrationRequestDetails] if it is present.
     *
     * @throws IllegalArgumentException if value is present but is not a valid UUID
     * @throws ContextDeserializationException if deserialization failed
     */
    @Throws(IllegalArgumentException::class, ContextDeserializationException::class)
    fun RegistrationRequestDetails.getPreAuthToken(deserializer: CordaAvroDeserializer<KeyValuePairList>): UUID? {
        return registrationContext.data.array().deserializeContext(deserializer)[PRE_AUTH_TOKEN]?.let {
            UUID.fromString(it)
        }
    }
}
