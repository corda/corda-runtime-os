package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.MapReferenceResolver
import net.corda.kryoserialization.testkit.mockSandboxGroup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ClassSerializerTest {
    @Test
    fun `class serializer returns the correct class back`() {
        val sandboxGroup = mockSandboxGroup(setOf(Class::class.java))
        val serializer = ClassSerializer(sandboxGroup)

        val kryo = Kryo(MapReferenceResolver())
        val output = Output(100)
        serializer.write(kryo, output, Class::class.java)
        val tested = serializer.read(kryo, Input(output.buffer), Class::class.java)

        assertThat(tested::class.java).isEqualTo(Class::class.java)
    }
}
