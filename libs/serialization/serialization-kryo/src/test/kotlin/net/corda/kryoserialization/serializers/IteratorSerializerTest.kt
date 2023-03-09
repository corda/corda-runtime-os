package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import com.esotericsoftware.kryo.util.MapReferenceResolver
import net.corda.kryoserialization.DefaultKryoCustomizer
import net.corda.kryoserialization.resolver.CordaClassResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

internal class IteratorSerializerTest {
    @Test
    fun `Iterator serializer returns correct iterator`() {
        val list = mutableListOf(1, 2)
        // this iterator will expect an old modCount
        // we'll serialize a correct iterator then deserialize back this one
        val oldIt = list.listIterator()
        list.addAll(listOf(3, 4))

        val output = Output(2048)
        val kryo = Kryo(MapReferenceResolver())
        DefaultKryoCustomizer.customize(
            kryo,
            emptyMap(),
            CordaClassResolver(mock()),
            ClassSerializer(mock())
        )
        val compatibleFieldSerializer: CompatibleFieldSerializer<Iterator<*>> = mock()
        `when`(compatibleFieldSerializer.read(any(), any(), any())).thenReturn(oldIt)

        val serializer = IteratorSerializer(
            oldIt::class.java,
            compatibleFieldSerializer
        )

        serializer.write(kryo, output, list.iterator())
        val tested = serializer.read(kryo, Input(output.buffer), Iterator::class.java)

        assertThat(tested).isInstanceOf(oldIt::class.java)
        // If we failed our deserialization we should get ConcurrentModificationException here
        assertThat(tested.next()).isEqualTo(1)
        assertThat(tested.next()).isEqualTo(2)
        assertThat(tested.next()).isEqualTo(3)
    }
}
