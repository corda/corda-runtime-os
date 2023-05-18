package net.corda.rocks.db.impl

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.messaging.api.subscription.data.DataProcessor
import net.corda.rocks.db.api.QueryProcessor
import net.corda.rocks.db.api.QueryProcessorBuilder

class QueryProcessorBuilderImpl<K : Any, V : Any> : QueryProcessorBuilder<K, V> {
    override fun buildQueryProcessor(
        keyDeserializer: CordaAvroDeserializer<K>,
        valueDeserializer: CordaAvroDeserializer<V>,
        dataProcessor: DataProcessor<K, V>,
        table: String
    ): QueryProcessor {
        return QueryProcessorImpl(keyDeserializer, valueDeserializer, dataProcessor, table)
    }
}
