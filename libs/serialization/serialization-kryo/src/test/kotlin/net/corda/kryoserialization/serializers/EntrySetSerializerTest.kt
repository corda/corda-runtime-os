package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.kryoserialization.DefaultKryoCustomizer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class EntrySetSerializerTest {

    @Test
    fun `EntrySet serializer returns correct value`() {
        val map = mapOf("1" to "a", "2" to null)
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
}