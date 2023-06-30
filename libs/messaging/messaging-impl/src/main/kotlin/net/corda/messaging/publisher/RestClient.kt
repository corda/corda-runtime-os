package net.corda.messaging.publisher

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.jetty.*
import io.ktor.client.request.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record

class RestClient(
    private val config: SmartConfig,
    private val avroSerializer: CordaAvroSerializer<Any>,
    private val avroDeserializer: CordaAvroDeserializer<Any>
) {

    private val client = HttpClient(Jetty)

    fun publish(records: List<Record<*, *>>): List<Record<*, *>> {
        runBlocking {
            records.map { record ->
                launch {
                    val body = avroSerializer.serialize(record)
                    val response = client.get("/url") {
                        setBody(body)
                    }
                    avroDeserializer.deserialize(response.body<ByteArray>()) as? Record<*, *>
                }
            }
        }
    }
}