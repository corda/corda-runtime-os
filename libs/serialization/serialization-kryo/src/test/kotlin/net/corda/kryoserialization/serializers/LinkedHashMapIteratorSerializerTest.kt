package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.v5.base.util.uncheckedCast
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.objenesis.strategy.StdInstantiatorStrategy

internal class LinkedHashMapIteratorSerializerTest {
    @Test
    fun `LinkedHashMapIterator serializer returns correct iterator`() {
        val kryo = Kryo().also { it.instantiatorStrategy = StdInstantiatorStrategy() }
        val output = Output(25 * 1024)
        val iterator = (0..1000).associateWith { "$it" }.iterator()
        val index = 100
        repeat(index) { iterator.next(); }
        LinkedHashMapIteratorSerializer.write(kryo, output, iterator)
        val tested = LinkedHashMapIteratorSerializer.read(kryo, Input(output.buffer), iterator.javaClass)

        assertThat(tested).isInstanceOf(iterator::class.java)
        // Iterator should still be pointing to 'index'
        val next: Map.Entry<Int, String> = uncheckedCast(tested.next())
        assertThat(next.key).isEqualTo(index)
        assertThat(next.value).isEqualTo("$index")
        val otherNext: Map.Entry<Int, String> = uncheckedCast(tested.next())
        // Iterator should be pointing to 'index + 1'
        assertThat(otherNext.key).isEqualTo(index+1)
        assertThat(otherNext.value).isEqualTo("${index+1}")
    }
}
