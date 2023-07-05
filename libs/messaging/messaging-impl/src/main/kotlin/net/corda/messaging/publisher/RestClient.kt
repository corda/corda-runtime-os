package net.corda.messaging.publisher

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
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
            level = LogLevel.INFO
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 5)
            exponentialDelay()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun publish(records: List<Record<*, *>>): List<Record<*, R>> {
        logger.info("Making a REST request to $endpoint, record topic ${records.first().topic}")
        return runBlocking {
            records.flatMap<Record<*, *>, Record<*, R>> { record ->
                async {
                    val body = record.value?.run { avroSerializer.serialize(this) }
                    val response = client.post(endpoint) {
                        contentType(ContentType.Application.OctetStream)
                        setBody(body)
                    }
                    val responseBody: ByteArray = response.body()
                    val responseEvents = avroDeserializer.deserialize(responseBody) as? List<R>
                    logger.info("Got a response: ${responseEvents?.size}")
                    responseEvents?.map { Record(record.topic, record.key, it) }!!
                }.await()
            }
        }
    }
}