package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.data.KeyValuePairList

internal abstract class BaseRequestStatusHandler<REQUEST, RESPONSE>(persistenceHandlerServices: PersistenceHandlerServices) :
    BasePersistenceHandler<REQUEST, RESPONSE>(persistenceHandlerServices) {
    val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java,
        )
    }
}
