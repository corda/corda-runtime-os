package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.LinkedList

internal class LinkedListItrSerializerTest {
    @Test
    fun `LinkedListItr serializer returns correct iterator`() {
        val kryo = Kryo().apply { isRegistrationRequired = false }
        val output = Output(100)
        val iterator = LinkedList(listOf(0, 1, "2", "boo")).listIterator(2)
        LinkedListItrSerializer.write(kryo, output, iterator)
        val tested = LinkedListItrSerializer.read(kryo, Input(output.buffer), iterator.javaClass)

        assertThat(tested).isInstanceOf(iterator::class.java)
        // Iterator should still be pointing to "2"
        assertThat(tested.next()).isEqualTo("2")
        assertThat(tested.next()).isEqualTo("boo")
    }
}
