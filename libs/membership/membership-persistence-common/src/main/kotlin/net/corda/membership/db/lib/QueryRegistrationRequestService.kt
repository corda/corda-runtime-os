package net.corda.membership.db.lib

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.membership.datamodel.RegistrationRequestEntity
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.LockModeType

class QueryRegistrationRequestService(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) {
    private companion object {
        val logger = LoggerFactory.getLogger(QueryRegistrationRequestService::class.java)
    }
    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java,
        )
    }
    fun get(
        em: EntityManager,
        registrationId: String,
    ): RegistrationRequestDetails? {
        return em.find(
            RegistrationRequestEntity::class.java,
            registrationId,
            LockModeType.PESSIMISTIC_WRITE,
        )?.toDetails(keyValuePairListDeserializer)
    }
}
