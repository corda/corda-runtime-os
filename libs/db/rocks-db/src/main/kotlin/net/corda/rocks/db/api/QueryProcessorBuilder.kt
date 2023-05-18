package net.corda.rocks.db.api

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.messaging.api.subscription.data.DataProcessor

interface QueryProcessorBuilder<K : Any, V : Any> {

    /**
     * Build a processor capable of reading key value bytes from a rocks db table,
     * deserialize them to real avro objects,
     * and then process them with the [dataProcessor] provided by the client
     */
    fun buildQueryProcessor(
        keyDeserializer: CordaAvroDeserializer<K>,
        valueDeserializer: CordaAvroDeserializer<V>,
        dataProcessor: DataProcessor<K, V>,
        table: String
    ): QueryProcessor
}
