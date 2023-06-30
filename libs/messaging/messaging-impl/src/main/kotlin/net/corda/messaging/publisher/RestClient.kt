package net.corda.messaging.publisher

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.messaging.api.records.Record

class RestClient(
    private val endpoint: String,
    private val avroSerializer: CordaAvroSerializer<Any>,
    private val avroDeserializer: CordaAvroDeserializer<Any>
) {

    private val client = HttpClient(CIO)

    fun publish(records: List<Record<*, *>>): List<Record<*, *>> {
        return runBlocking {
            records.map { record ->
                async {
                    val body = avroSerializer.serialize(record)
                    val response = client.get(endpoint) {
                        setBody(body)
                    }
                    avroDeserializer.deserialize(response.body<ByteArray>()) as? Record<*, *>
                }
            }.mapNotNull {
                it.await()
            }
        }
    }
}