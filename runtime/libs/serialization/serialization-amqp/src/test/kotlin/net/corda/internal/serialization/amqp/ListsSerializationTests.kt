package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeEnvelopeAssert
import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.verifyEnvelope
import net.corda.v5.base.annotations.CordaSerializable
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.NotSerializableException
import kotlin.collections.ArrayList

@Timeout(value = 30)
class ListsSerializationTests {

    @Test
    fun `check list can be serialized as root of serialization graph`() {
        serializeDeserializeAssert(emptyList<Int>())
        serializeDeserializeAssert(listOf(1))
        serializeDeserializeAssert(listOf(1, 2))
    }

    @CordaSerializable
    interface Parent

    data class Child(val value: Int) : Parent

    @CordaSerializable
    data class CovariantContainer<out T : Parent>(val payload: List<T>)

    @Test
    fun `check covariance`() {
        val payload = ArrayList<Child>()
        payload.add(Child(1))
        payload.add(Child(2))
        val container = CovariantContainer(payload)

        fun verifyEnvelopeBody(envelope: Envelope) {
            envelope.schema.types.single { typeNotation ->
                typeNotation.name == "java.util.List<${Parent::class.java.name}>"
            }
        }

        serializeDeserializeEnvelopeAssert(container) { envelope -> verifyEnvelope(envelope, ::verifyEnvelopeBody) }
    }

    @CordaSerializable
    data class WrongPayloadType(val payload: ArrayList<Int>)

    @Test
    fun `check throws for forbidden declared type`() {
        val payload = ArrayList<Int>()
        payload.add(1)
        payload.add(2)
        val wrongPayloadType = WrongPayloadType(payload)
        assertThatThrownBy { serializeDeserializeAssert(wrongPayloadType) }
            .isInstanceOf(NotSerializableException::class.java)
            .hasMessageContaining("Cannot derive collection type for declared type")
    }
}