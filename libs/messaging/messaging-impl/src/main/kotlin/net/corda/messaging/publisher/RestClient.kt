package net.corda.messaging.publisher

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.event.FlowEvent
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory

class RestClient<R : Any>(
    private val endpoint: String,
    private val avroSerializer: CordaAvroSerializer<Any>,
    private val avroDeserializer: CordaAvroDeserializer<Any>
) {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.name)
    }

    private val client = HttpClient(CIO) {
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.HEADERS
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun publish(records: List<Record<String, *>>): List<Record<String, R>> {
        logger.info("Making a REST request to $endpoint, record topic ${records.first().topic}")
        return runBlocking {
            records.map { record ->
                async {
                    val body = record.value?.run { avroSerializer.serialize(this) }
                    val response = client.get(endpoint) {
                        setBody(body)
                    }
                    val deserializedResponse = avroDeserializer.deserialize(response.body<ByteArray>()) as? R
                    logger.info("Got a response: $deserializedResponse")
                    Record(record.topic, record.key, deserializedResponse)
                }
            }.map {
                it.await()
            }
        }
    }
}