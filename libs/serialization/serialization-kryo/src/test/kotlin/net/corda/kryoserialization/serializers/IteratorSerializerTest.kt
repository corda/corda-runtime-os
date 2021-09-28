package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import net.corda.kryoserialization.TestClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class IteratorSerializerTest {
    @Disabled
    @Test
    fun `Iterator serializer returns correct iterator`() {
        val kryo = Kryo()
        val output = Output(2048)
        val iterator = listOf(TestClass(1, "boo")).listIterator()
        val serializer = IteratorSerializer(
            TestClass::class.java,
            CompatibleFieldSerializer(kryo, TestClass::class.java)
        )
        serializer.write(kryo, output, iterator)
        val tested = serializer.read(kryo, Input(output.buffer), Iterator::class.java)

        assertThat(tested).isInstanceOf(iterator::class.java)
        assertThat(tested.next()).isEqualTo(1)
    }
}
