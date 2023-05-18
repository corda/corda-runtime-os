package net.corda.rocks.db.impl

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.messaging.api.subscription.data.DataProcessor
import net.corda.rocks.db.api.QueryProcessor
import net.corda.utilities.trace
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class QueryProcessorImpl<K : Any, V : Any>(
    private val keyDeserializer: CordaAvroDeserializer<K>,
    private val valueDeserializer: CordaAvroDeserializer<V>,
    private val dataProcessor: DataProcessor<K, V>,
    private val table: String
) : QueryProcessor {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    override fun process(keyBytes: ByteArray, valueBytes: ByteArray) {
        val key = keyDeserializer.deserialize(keyBytes) ?: throw CordaRuntimeException("Failed to deserialize key")
        val value = valueDeserializer.deserialize(valueBytes) ?: throw CordaRuntimeException("Failed to deserialize value")
        logger.trace { "Iterating throw rocks table $table. Key [$key} and value [$value]" }
        dataProcessor.process(key, value)
    }
}
