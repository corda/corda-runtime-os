package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.MapReferenceResolver
import net.corda.crypto.cipher.suite.KeyEncodingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.security.PublicKey

internal class PublicKeySerializerTest {
    @Test
    fun `PublicKey serializer returns the correct class back`() {
        // Most of the work in this serializer is actually done by the KeyEncodingService.
        // We're just passing through to that service
        val publicKey = KeyPairGenerator.getInstance("RSA").genKeyPair().public
        val keyEncodingService: KeyEncodingService = mock<KeyEncodingService>().also {
            whenever(it.encodeAsByteArray(eq(publicKey))).thenReturn("1".encodeToByteArray())
            whenever(it.decodePublicKey(eq("1".encodeToByteArray()))).thenReturn(publicKey)
        }
        val publicKeySerializer = PublicKeySerializer(keyEncodingService)

        val kryo = Kryo(MapReferenceResolver())
        val output = Output(100)
        publicKeySerializer.write(kryo, output, publicKey)
        val tested = publicKeySerializer.read(kryo, Input(output.buffer), PublicKey::class.java)

        assertThat(tested).isEqualTo(publicKey)
    }
}
