package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.MapReferenceResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.objenesis.strategy.StdInstantiatorStrategy

internal class LinkedHashMapEntrySerializerTest {
    @Test
    fun `LinkedHashMapEntry serializer returns correct iterator`() {
        val kryo = Kryo(MapReferenceResolver()).also { it.instantiatorStrategy = StdInstantiatorStrategy() }
        val output = Output(1024)
        val iterator = (0..1000).associateWith { "$it" }.iterator()
        val index = 100
        repeat(index) { iterator.next(); }
        val entry = iterator.next();
        LinkedHashMapEntrySerializer.write(kryo, output, entry)
        val tested = LinkedHashMapEntrySerializer.read(kryo, Input(output.buffer), entry.javaClass)

        assertThat(tested).isInstanceOf(entry.javaClass)
        assertThat(tested.key).isEqualTo(index)
        assertThat(tested.value).isEqualTo("$index")
    }
}
