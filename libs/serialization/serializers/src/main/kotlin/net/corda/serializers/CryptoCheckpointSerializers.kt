package net.corda.serializers

import net.corda.kryoserialization.CheckpointInput
import net.corda.kryoserialization.CheckpointInternalCustomSerializer
import net.corda.kryoserialization.CheckpointOutput
import net.corda.v5.crypto.Crypto
import java.security.PrivateKey
import java.security.PublicKey

/* This file contains Serializers for types which are defined in the Crypto module.
 * We do this here as we don't want crypto to depend on serialization (or serialization-kryo
 * to depend on Crypto).
*/
class PrivateKeySerializer : CheckpointInternalCustomSerializer<PrivateKey> {
    override fun write(output: CheckpointOutput, obj: PrivateKey) {
        output.writeBytesWithLength(obj.encoded)
    }

    override fun read(input: CheckpointInput, type: Class<PrivateKey>): PrivateKey {
        return Crypto.decodePrivateKey(input.readBytesWithLength())
    }
}

/** For serialising a public key */
class PublicKeySerializer : CheckpointInternalCustomSerializer<PublicKey> {
    override fun write(output: CheckpointOutput, obj: PublicKey) {
        output.writeBytesWithLength(obj.encoded)
    }

    override fun read(input: CheckpointInput, type: Class<PublicKey>): PublicKey {
        return Crypto.decodePublicKey(input.readBytesWithLength())
    }
}