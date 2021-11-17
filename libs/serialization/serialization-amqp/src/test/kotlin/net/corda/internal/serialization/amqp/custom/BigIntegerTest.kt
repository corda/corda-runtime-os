package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.factory
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.TypeNotation
import net.corda.internal.serialization.amqp.testutils.testSerializationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.math.BigInteger

class BigIntegerTest {
    @Test
    fun zero() {
        serializeDeserializeAssert(BigInteger.ZERO)
    }

    @Test
    fun one() {
        serializeDeserializeAssert(BigInteger.ONE)
    }

    @Test
    fun two() {
        serializeDeserializeAssert(BigInteger.TWO)
    }

    @Test
    fun ten() {
        serializeDeserializeAssert(BigInteger.TEN)
    }

    @Test
    fun veryLongNumber() {
        serializeDeserializeAssert(
            BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TEN)
        )
    }

    @Test
    fun negativeValue() {
        serializeDeserializeAssert(BigInteger("-1"))
    }

    @Test
    fun testSerializerIsRegisteredForSubclass() {
        val number = BigInteger("1234567890")
        val schemas = SerializationOutput(factory).serializeAndReturnSchema(number, testSerializationContext)
        assertThat(schemas.schema.types.map(TypeNotation::name)).doesNotContain(number::class.java.name)

        val serializer = factory.findCustomSerializer(number::class.java, number::class.java)
            ?: fail("No custom serializer found")
        assertThat(serializer.type).isSameAs(number::class.java)
    }
}
