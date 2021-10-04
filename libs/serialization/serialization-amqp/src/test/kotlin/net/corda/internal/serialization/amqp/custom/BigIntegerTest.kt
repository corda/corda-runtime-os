package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
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
}

