package net.corda.serializers

import net.corda.serialization.CheckpointInput
import net.corda.serialization.CheckpointInternalCustomSerializer
import net.corda.serialization.CheckpointOutput
import net.corda.v5.crypto.Crypto
import java.security.PublicKey

class PublicKeySerializer : CheckpointInternalCustomSerializer<PublicKey> {
    override fun write(output: CheckpointOutput, obj: PublicKey) {
        output.writeBytesWithLength(obj.encoded)
    }

    override fun read(input: CheckpointInput, type: Class<PublicKey>): PublicKey {
        return Crypto.decodePublicKey(input.readBytesWithLength())
    }
}