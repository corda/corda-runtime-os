package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.testutils.deserialize
import net.corda.internal.serialization.amqp.testutils.serialize
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.registerCustomSerializers
import kotlin.test.assertEquals

class ReusableSerialiseDeserializeAssert {
    companion object {
        // Build factory
        val factory = testDefaultFactory().also { registerCustomSerializers(it) }

        inline fun <reified T : Any> serializeDeserialize(instance: T, withFactory: SerializerFactory = factory): T {
            // Serialize
            val bytes = SerializationOutput(withFactory).serialize(instance)

            // Deserialize
            val deserialized = DeserializationInput(withFactory).deserialize(bytes)

            return deserialized
        }


        inline fun <reified T : Any> serializeDeserializeAssert(
            instance: T,
            withFactory: SerializerFactory = factory
        ): T {
            // Serialize
            // Deserialize
            val deserialized = serializeDeserialize(instance, withFactory)

            // Check
            assertEquals(instance, deserialized)

            return deserialized
        }
    }
}