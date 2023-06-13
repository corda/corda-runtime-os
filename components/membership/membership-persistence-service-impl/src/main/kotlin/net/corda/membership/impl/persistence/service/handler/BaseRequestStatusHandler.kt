package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.impl.persistence.service.handler.RegistrationStatusHelper.toStatus
import net.corda.membership.lib.retrieveSignatureSpec
import java.nio.ByteBuffer

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

    fun RegistrationRequestEntity.toDetails(): RegistrationRequestDetails {
        val memberContext = keyValuePairListDeserializer.deserialize(this.memberContext)
        val registrationProtocolVersion = memberContext?.items?.firstOrNull {
            it.key == "registrationProtocolVersion"
        }?.value?.toIntOrNull() ?: DEFAULT_REGISTRATION_PROTOCOL_VERSION
        return RegistrationRequestDetails.newBuilder()
            .setRegistrationSent(this.created)
            .setRegistrationLastModified(this.lastModified)
            .setRegistrationStatus(this.status.toStatus())
            .setRegistrationId(this.registrationId)
            .setHoldingIdentityId(this.holdingIdentityShortHash)
            .setRegistrationProtocolVersion(registrationProtocolVersion)
            .setMemberProvidedContext(memberContext)
            .setMemberSignature(
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(this.memberContextSignatureKey),
                    ByteBuffer.wrap(this.memberContextSignatureContent)
                )
            )
            .setMemberSignatureSpec(retrieveSignatureSpec(this.memberContextSignatureSpec))
            .setRegistrationContext(keyValuePairListDeserializer.deserialize(registrationContext))
            .setRegistrationContextSignature(
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(registrationContextSignatureKey),
                    ByteBuffer.wrap(registrationContextSignatureContent)
                )
            )
            .setRegistrationContextSignatureSpec(retrieveSignatureSpec(registrationContextSignatureSpec))
            .setReason(this.reason)
            .setSerial(this.serial)
            .build()
    }
}
