package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.util.BitSet

class BitSetTest {
    private var one = 1
    private val oneHundred = 100
    private val sixtyFour = 64

    @Test
    fun empty() {
        serializeDeserializeAssert(BitSet())
    }
    @Test
    fun oneFalse() {
        val instance = BitSet(one)
        serializeDeserializeAssert(instance)
    }
    @Test
    fun oneTrue() {
        val instance = BitSet(one)
        instance[0] = true
        serializeDeserializeAssert(instance)
    }
    @Test
    fun sixtyFourFalse() {
        val instance = BitSet(sixtyFour)
        serializeDeserializeAssert(instance)
    }
    @Test
    fun sixtyFourTrue() {
        val instance = BitSet(sixtyFour)
        for (i in 0 until sixtyFour)
            instance[i] = true
        serializeDeserializeAssert(instance)
    }
    @Test
    fun oneHundredFalse() {
        val instance = BitSet(oneHundred)
        serializeDeserializeAssert(instance)
    }
    @Test
    fun oneHundredTrue() {
        val instance = BitSet(oneHundred)
        for (i in 0 until oneHundred)
            instance[i] = true
        serializeDeserializeAssert(instance)
    }
}

