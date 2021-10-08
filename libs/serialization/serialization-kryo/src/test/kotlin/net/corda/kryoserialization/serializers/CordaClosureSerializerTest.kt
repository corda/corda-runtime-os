package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Output
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

internal class CordaClosureSerializerTest {
    @Test
    fun `CordaClosure serializer throws exception on bad closure`() {
        val kryo = Kryo()
        val output = Output(100)
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            CordaClosureSerializer.write(kryo, output, Runnable {})
        }
    }
}
