package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class StackTraceSerializerTest {
    @Test
    fun `StackTrace serializer returns nothing`() {
        val serializer = StackTraceSerializer()
        val kryo = Kryo()
        val output = Output(100)
        val stackTrace = Exception().stackTrace
        serializer.write(kryo, output, stackTrace)
        val tested = serializer.read(kryo, Input(output.buffer), Array<StackTraceElement>::class.java)

        assertThat(output.buffer.isEmpty())
        assertThat(tested).isEmpty()
    }
}
