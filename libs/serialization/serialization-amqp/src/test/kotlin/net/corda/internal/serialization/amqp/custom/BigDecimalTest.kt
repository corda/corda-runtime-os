package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
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

}

