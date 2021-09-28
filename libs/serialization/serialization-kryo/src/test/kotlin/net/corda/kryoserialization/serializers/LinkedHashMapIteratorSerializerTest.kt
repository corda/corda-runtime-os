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
        val output = Output(1024)
        val iterator = mapOf(0 to "0", 1 to "1", 2 to "2", 3 to "boo").iterator()
        iterator.next(); iterator.next()
        LinkedHashMapIteratorSerializer.write(kryo, output, iterator)
        val tested = LinkedHashMapIteratorSerializer.read(kryo, Input(output.buffer), iterator.javaClass)

        assertThat(tested).isInstanceOf(iterator::class.java)
        // Iterator should still be pointing to "2"
        val next: Map.Entry<Int, String> = uncheckedCast(tested.next())
        assertThat(next.key).isEqualTo(2)
        assertThat(next.value).isEqualTo("2")
        val otherNext: Map.Entry<Int, String> = uncheckedCast(tested.next())
        assertThat(otherNext.key).isEqualTo(3)
        assertThat(otherNext.value).isEqualTo("boo")
    }
}
