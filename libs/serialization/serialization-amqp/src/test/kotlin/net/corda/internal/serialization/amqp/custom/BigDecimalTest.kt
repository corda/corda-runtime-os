package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.factory
import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.TypeNotation
import net.corda.internal.serialization.amqp.testutils.testSerializationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.math.BigDecimal

class BigDecimalTest {
    @Test
    fun zero() {
        serializeDeserializeAssert(BigDecimal.ZERO)
    }

    @Test
    fun one() {
        serializeDeserializeAssert(BigDecimal.ONE)
    }

    @Test
    fun ten() {
        serializeDeserializeAssert(BigDecimal.TEN)
    }

    @Test
    fun veryLongNumber() {
        serializeDeserializeAssert(BigDecimal(Long.MAX_VALUE).multiply(BigDecimal.TEN))
    }

    @Test
    fun decimalValue() {
        serializeDeserializeAssert(BigDecimal("0.3"))
    }

    @Test
    fun negativeValue() {
        serializeDeserializeAssert(BigDecimal("-1"))
    }

    @Test
    fun testSerializerIsRegisteredForSubclass() {
        val number = BigDecimal("1234567890.987654321")
        val schemas = SerializationOutput(factory).serializeAndReturnSchema(number, testSerializationContext)
        assertThat(schemas.schema.types.map(TypeNotation::name)).doesNotContain(number::class.java.name)

        val serializer = factory.findCustomSerializer(number::class.java, number::class.java)
            ?: fail("No custom serializer found")
        assertThat(serializer.type).isSameAs(number::class.java)
    }
}
