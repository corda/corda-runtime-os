package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.kryoserialization.DefaultKryoCustomizer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.io.InputStream

class InputStreamSerializerTest {
    @Test
    fun `InputStreamSerializer serializer returns the correct object back`() {
        val kryo = Kryo()
        DefaultKryoCustomizer.customize(
            kryo,
            emptyMap(),
            ClassSerializer(mock())
        )
        val output = Output(100)
        val data = "test"
        kryo.writeClassAndObject(output, data.byteInputStream())

        val deserialized = kryo.readClassAndObject(Input(output.buffer))

        assertThat(deserialized).isInstanceOf(InputStream::class.java)
        assertThat(deserialized as InputStream).hasContent(data)
    }
}
