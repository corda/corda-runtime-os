package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.objenesis.strategy.StdInstantiatorStrategy

internal class LinkedHashMapEntrySerializerTest {
    @Test
    fun `LinkedHashMapEntry serializer returns correct iterator`() {
        val kryo = Kryo().also { it.instantiatorStrategy = StdInstantiatorStrategy() }
        val output = Output(1024)
        val iterator = mapOf(0 to "0", 1 to "1", 2 to "2", 3 to "boo").iterator()
        val entry = iterator.next();
        LinkedHashMapEntrySerializer.write(kryo, output, entry)
        val tested = LinkedHashMapEntrySerializer.read(kryo, Input(output.buffer), entry.javaClass)

        assertThat(tested).isInstanceOf(entry.javaClass)
        assertThat(tested.key).isEqualTo(0)
        assertThat(tested.value).isEqualTo("0")
    }
}
