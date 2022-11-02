package net.cordapp.testing.smoketests.flow

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable

class AmqpSerializationTestFlow : RPCStartableFlow {

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaSerializable
    data class SerializableClass(val pair: Pair<String, String>)

    override fun call(requestBody: RPCRequestData): String = try {
        val pair = SerializableClass(Pair("A", "B"))

        val serializedBytes = serializationService.serialize(pair)
        val deserialize = serializationService.deserialize(serializedBytes, SerializableClass::class.java)

        deserialize.toString()
    } catch (e: Exception) {
        e.toString()
    }
}
