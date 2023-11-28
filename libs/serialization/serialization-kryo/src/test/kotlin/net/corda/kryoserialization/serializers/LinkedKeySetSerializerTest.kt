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
        // Need to serialize map and keyset together in the same call, as a second call resets all of Kryo's reference
        // resolvers, preventing the deserialized objects from having the correct references.
        kryo.writeClassAndObject(output, map to keySet)
        output.close()

        val input = Input(output.buffer)
        val (newMap, testedEntries) = kryo.readClassAndObject(input) as Pair<Map<String, *>, MutableSet<*>>
        input.close()

        assertThat(testedEntries).isEqualTo(keySet)
        assertThat(newMap).isEqualTo(map)
        testedEntries.remove("b")
        assertThat(newMap).doesNotContainKey("b")
    }
}
