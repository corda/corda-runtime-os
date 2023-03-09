package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.MapReferenceResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class LoggerSerializerTest {
    @Test
    fun `Logger serializer returns correct logger`() {
        val kryo = Kryo(MapReferenceResolver())
        val output = Output(100)
        val log = LoggerFactory.getLogger("Log")
        LoggerSerializer.write(kryo, output, log)
        val tested = LoggerSerializer.read(kryo, Input(output.buffer), Logger::class.java)

        assertThat(Input(output.buffer).readString()).isEqualTo(log.name)
        assertThat(tested).isInstanceOf(Logger::class.java)
        assertThat(tested.name).isEqualTo(log.name)
    }
}
