package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.kryoserialization.DefaultKryoCustomizer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class LinkedKeySetSerializerTest {

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `linked key set serializer returns correct value`() {
        val map = LinkedHashMap<String, Int>()
        map["a"] = 1
        map["b"] = 2
        val keySet = map.keys

        val kryo = Kryo()
        DefaultKryoCustomizer.customize(
            kryo,
            emptyMap(),
            ClassSerializer(mock())
        )

        val output = Output(200)
        kryo.writeClassAndObject(output, map)
        kryo.writeClassAndObject(output, keySet)
        output.close()

        val input = Input(output.buffer)
        val newMap = kryo.readClassAndObject(input) as Map<String, *>
        val testedEntries = kryo.readClassAndObject(input) as MutableSet<*>
        input.close()

        assertThat(testedEntries).isEqualTo(keySet)
        assertThat(newMap).isEqualTo(map)
        testedEntries.remove("b")
        assertThat(newMap).doesNotContainKey("b")
    }
}