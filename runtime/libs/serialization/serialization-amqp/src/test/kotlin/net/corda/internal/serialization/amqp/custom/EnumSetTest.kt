package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import net.corda.v5.base.annotations.CordaSerializable
import org.junit.jupiter.api.Test
import java.util.EnumSet

class EnumSetTest {

    @CordaSerializable
    enum class Abc {
        A,
        B,
        C
    }

    @Test
    fun empty() {
        serializeDeserializeAssert(EnumSet.noneOf(Abc::class.java))
    }

    @Test
    fun oneEnum() {
        serializeDeserializeAssert(EnumSet.of(Abc.A))
    }

    @Test
    fun threeEnums() {
        serializeDeserializeAssert(EnumSet.of(Abc.A, Abc.B, Abc.C))
    }
}
