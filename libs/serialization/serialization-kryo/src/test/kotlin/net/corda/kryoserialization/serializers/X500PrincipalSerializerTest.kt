package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.MapReferenceResolver
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import javax.security.auth.x500.X500Principal

internal class X500PrincipalSerializerTest {
    @Test
    fun `X500Principal serializer returns the correct object back`() {
        val output = Output(500)
        val input = Input(output.buffer)

        val kryo = Kryo(MapReferenceResolver()).apply {
            register(X500Principal::class.java, X500PrincipalSerializer())
        }
        val principal = X500Principal("CN=Bob, OU=Corda, O=R3, L=London, C=GB, S=England")

        kryo.writeClassAndObject(output, principal)
        val deserialized = kryo.readClassAndObject(input)

        input.close()
        output.close()

        Assertions.assertThat(deserialized).isEqualTo(principal)
    }
}
