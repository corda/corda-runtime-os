package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Output
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class AutoCloseableSerialisationDetectorTest {
    @Test
    fun `AutoCloseable serializer detector throws exception on auto closeable`() {
        val kryo = Kryo()
        val output = Output(100)
        Assertions.assertThatExceptionOfType(UnsupportedOperationException::class.java).isThrownBy {
            AutoCloseableSerialisationDetector.write(kryo, output) {}
        }
    }
}
