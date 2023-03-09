package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.MapReferenceResolver
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class AutoCloseableSerializerTest {
    @Test
    fun `AutoCloseable serializer detector throws exception on auto closeable`() {
        val kryo = Kryo(MapReferenceResolver())
        val output = Output(100)
        val input = Input(100)
        Assertions.assertThatExceptionOfType(UnsupportedOperationException::class.java).isThrownBy {
            AutoCloseableSerializer.write(kryo, output) {}
        }
        Assertions.assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
            AutoCloseableSerializer.read(kryo, input, AutoCloseable::class.java)
        }
    }
}
