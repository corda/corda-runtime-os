package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.common.RegistrationStatusDetails
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.impl.persistence.service.handler.RegistrationStatusHelper.toStatus

internal abstract class BaseRequestStatusHandler<REQUEST, RESPONSE>(persistenceHandlerServices: PersistenceHandlerServices) :
    BasePersistenceHandler<REQUEST, RESPONSE>(persistenceHandlerServices) {
    private companion object {
        const val DEFAULT_REGISTRATION_PROTOCOL_VERSION = 1
    }
    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )
    }

    fun RegistrationRequestEntity.toDetails(): RegistrationStatusDetails {
        val context = keyValuePairListDeserializer.deserialize(this.context)
        val registrationProtocolVersion = context?.items?.firstOrNull {
            it.key == "registrationProtocolVersion"
        }?.value?.toIntOrNull() ?: DEFAULT_REGISTRATION_PROTOCOL_VERSION
        return RegistrationStatusDetails.newBuilder()
            .setRegistrationSent(this.created)
            .setRegistrationLastModified(this.lastModified)
            .setRegistrationStatus(this.status.toStatus())
            .setRegistrationId(this.registrationId)
            .setRegistrationProtocolVersion(registrationProtocolVersion)
            .setMemberProvidedContext(context)
            .setReason(this.reason)
            .build()
    }
}
