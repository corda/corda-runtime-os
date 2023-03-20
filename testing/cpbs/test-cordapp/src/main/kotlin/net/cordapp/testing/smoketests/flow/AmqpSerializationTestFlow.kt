package net.cordapp.testing.smoketests.flow

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable

class AmqpSerializationTestFlow : ClientStartableFlow {

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaSerializable
    data class SerializableClass(val pair: Pair<String, String>)

    override fun call(requestBody: ClientRequestBody): String = try {
        val pair = SerializableClass(Pair("A", "B"))

        val serializedBytes = serializationService.serialize(pair)
        val deserialize = serializationService.deserialize(serializedBytes, SerializableClass::class.java)

        deserialize.toString()
    } catch (e: Exception) {
        e.toString()
    }
}
