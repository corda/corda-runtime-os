package net.corda.serializers

import net.corda.kryoserialization.CheckpointInput
import net.corda.kryoserialization.CheckpointInternalCustomSerializer
import net.corda.kryoserialization.CheckpointOutput
import net.corda.v5.crypto.Crypto
import java.security.PrivateKey

class PrivateKeySerializer : CheckpointInternalCustomSerializer<PrivateKey> {
    override fun write(output: CheckpointOutput, obj: PrivateKey) {
        output.writeBytesWithLength(obj.encoded)
    }

    override fun read(input: CheckpointInput, type: Class<PrivateKey>): PrivateKey {
        return Crypto.decodePrivateKey(input.readBytesWithLength())
    }
}