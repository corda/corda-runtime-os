package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.custom.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.EnumSet

class EnumSetTest {

    enum class Abc {
        A,
        B,
        C
    }

    @Test
    @Disabled("Will fix in CORE-2684")
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
