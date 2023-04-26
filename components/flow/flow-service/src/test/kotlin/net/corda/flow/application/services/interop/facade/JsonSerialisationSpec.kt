package net.corda.flow.application.services.interop.facade

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.v5.application.interop.facade.FacadeRequest
import net.corda.v5.application.interop.facade.FacadeResponse
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.ZonedDateTime

class JsonSerialisationSpec : DescribeSpec({

    val mapper = ObjectMapper().registerKotlinModule().configure(SerializationFeature.INDENT_OUTPUT, true)

    fun assertRoundtripsCorrectly(request: FacadeRequest) {
        val json = mapper.writeValueAsString(request)
        val deserialised = mapper.readValue(json, FacadeRequest::class.java)
        deserialised shouldBe request
    }

    fun assertRoundtripsCorrectly(response: FacadeResponse) {
        val json = mapper.writeValueAsString(response)
        val deserialised = mapper.readValue(json, FacadeResponse::class.java)
        deserialised shouldBe response
    }

    describe("The JSON serialiser") {

        val facade = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/serialisation-test-facade.json")!!)

        it("serialises bytes to a base64 encoded string") {
            val exchangeBytes = facade.method("exchange-bytes")
            val bytesIn = exchangeBytes.inParameter<ByteBuffer>("bytes")
            val bytesOut = exchangeBytes.outParameter<ByteBuffer>("bytes")

            val digest = MessageDigest.getInstance("SHA-256").apply {
                update("Goodbyte, cruel world!".toByteArray())
            }.digest()

            val request = exchangeBytes.request(bytesIn.of(ByteBuffer.wrap("Hello, world!".toByteArray())))
            val response = exchangeBytes.response(bytesOut.of(ByteBuffer.wrap(digest)))

            assertRoundtripsCorrectly(request)
            assertRoundtripsCorrectly(response)
        }

        it("serialises json to embedded json") {
            val exchangeJson = facade.method("exchange-json")
            val jsonIn = exchangeJson.inParameter<String>("json")
            val jsonOut = exchangeJson.outParameter<String>("json")

            val request = exchangeJson.request(jsonIn.of(mapper.writeValueAsString(listOf(1, 2, 3))))
            val response = exchangeJson.response(
                jsonOut.of(mapper.writeValueAsString(
                    mapOf(
                        "a" to "apple",
                        "c" to "carrot",
                        "b" to "banana"
                    )
                )
            )
            )

            assertRoundtripsCorrectly(request)
            assertRoundtripsCorrectly(response)
        }

        it("serialises a timestamp to a formatted string") {
            val exchangeTimestamp = facade.method("exchange-timestamp")
            val timestampIn = exchangeTimestamp.inParameter<ZonedDateTime>("timestamp")
            val timestampOut = exchangeTimestamp.outParameter<ZonedDateTime>("timestamp")

            val request = exchangeTimestamp.request(timestampIn.of(ZonedDateTime.now()))
            val response = exchangeTimestamp.response(timestampOut.of(ZonedDateTime.now()))

            assertRoundtripsCorrectly(request)
            assertRoundtripsCorrectly(response)
        }
    }
})