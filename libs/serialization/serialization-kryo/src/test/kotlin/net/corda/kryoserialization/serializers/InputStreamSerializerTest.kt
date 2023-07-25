package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.io.InputStream

internal class InputStreamSerializerTest {
    @Test
    fun `InputStreamSerializer serializer returns the correct object back`() {
        val kryo = Kryo()
        val output = Output(100)
        val data = "test"
        InputStreamSerializer.write(kryo, output, data.byteInputStream())

        val deserialized = InputStreamSerializer.read(kryo, Input(output.buffer), InputStream::class.java)

        Assertions.assertThat(deserialized).isInstanceOf(InputStream::class.java)
        Assertions.assertThat(deserialized).hasContent(data)
    }
}
