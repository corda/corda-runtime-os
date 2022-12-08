package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.kryoserialization.readBytesWithLength
import net.corda.kryoserialization.writeBytesWithLength
import java.security.PublicKey

class PublicKeySerializer(
    private val keyEncodingService: KeyEncodingService
) : Serializer<PublicKey>() {
    override fun write(kryo: Kryo, output: Output, obj: PublicKey) {
        output.writeBytesWithLength(keyEncodingService.encodeAsByteArray(obj))
    }

    override fun read(kryo: Kryo, input: Input, type: Class<PublicKey>): PublicKey {
        return keyEncodingService.decodePublicKey(input.readBytesWithLength())
    }
}
