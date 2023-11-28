package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.kryoserialization.DefaultKryoCustomizer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class LinkedEntrySetSerializerTest {

    @Test
    fun `LinkedEntrySet serializer returns correct value`() {
        // Original Test Case - Reported Example
        val map = linkedMapOf("1" to "a", "2" to null)
        val entries = map.entries

        val kryo = Kryo()

        DefaultKryoCustomizer.customize(
            kryo,
            emptyMap(),
            ClassSerializer(mock())
        )

        val output = Output(200)
        kryo.writeClassAndObject(output, entries)
        output.close()

        val input = Input(output.buffer)
        val testedEntries = kryo.readClassAndObject(input)
        input.close()

        Assertions.assertThat(testedEntries).isEqualTo(entries)
    }

    @Test
    fun `LinkedEntrySet serializer grants elements order`() {
        val map = LinkedHashMap<Any, Any?>()
        val randomValues = arrayOf("a", "b", "c", 1, 2, 3, null)

        repeat(500) {
            val r = (0 until randomValues.size).random()
            val r2 = (0 until randomValues.size - 1).random()
            if (it % 2 == 0) {
                map[it] = randomValues[r]
            } else {
                map[randomValues[r2]!!] = randomValues[r]
            }
        }

        val entries = map.entries

        val kryo = Kryo()

        DefaultKryoCustomizer.customize(
            kryo,
            emptyMap(),
            ClassSerializer(mock())
        )

        val output = Output(3000)
        kryo.writeClassAndObject(output, entries)
        output.close()

        val input = Input(output.buffer)
        val testedEntries = kryo.readClassAndObject(input)
        input.close()

        @Suppress("UNCHECKED_CAST")
        Assertions.assertThat(
            (testedEntries as Set<Map.Entry<*, *>>).toList()
        ).isEqualTo(entries.toList())
    }
}
