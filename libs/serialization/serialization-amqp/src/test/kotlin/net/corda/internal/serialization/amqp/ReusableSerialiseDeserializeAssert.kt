package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.testutils.deserialize
import net.corda.internal.serialization.amqp.testutils.serialize
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.amqp.testutils.testSerializationContext
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.serialization.SerializationContext
import kotlin.test.assertEquals

class ReusableSerialiseDeserializeAssert {
    companion object {
        // Build factory
        val factory = testDefaultFactory().also { registerCustomSerializers(it) }

        inline fun <reified T : Any> serializeDeserialize(instance: T, withFactory: SerializerFactory = factory): T {
            // Serialize
            val bytes = SerializationOutput(withFactory).serialize(instance)

            // Deserialize
            return DeserializationInput(withFactory).deserialize(bytes)
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

        inline fun <reified T : Any> serializeDeserializeEnvelopeAssert(
            instance: T,
            withFactory: SerializerFactory = factory,
            context: SerializationContext = testSerializationContext,
            noinline streamValidation: ((Envelope) -> Unit)? = null
        ) {
            // Serialize
            val bytes = SerializationOutput(withFactory).serialize(instance)

            // Extract Envelope
            val envelope = DeserializationInput(withFactory).getEnvelope(bytes, context)

            // Run validation function
            streamValidation?.invoke(envelope)
        }

        fun <T : Any> verifyEnvelope(obj: T, envVerBody: (Envelope) -> Unit) = envVerBody(obj as Envelope)
    }
}