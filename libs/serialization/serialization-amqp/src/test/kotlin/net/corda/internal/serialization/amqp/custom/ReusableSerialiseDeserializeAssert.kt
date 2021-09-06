package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.testutils.deserialize
import net.corda.internal.serialization.amqp.testutils.serialize
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.registerCustomSerializers
import kotlin.test.assertEquals

class ReusableSerialiseDeserializeAssert {
    companion object {
        // Build factory
        val factory = testDefaultFactory().also { registerCustomSerializers(it) }

        inline fun <reified T : Any> serializeDeserialize(instance: T): T {
            // Serialize
            val bytes = SerializationOutput(factory).serialize(instance)

            // Deserialize
            val deserialized = DeserializationInput(factory).deserialize(bytes)

            return deserialized
        }


        inline fun <reified T : Any> serializeDeserializeAssert(instance: T): T {
            // Serialize
            // Deserialize
            val deserialized = serializeDeserialize(instance)

            // Check
            assertEquals(instance, deserialized)

            return deserialized
        }
    }}