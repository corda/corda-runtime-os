package net.corda.sandbox

import net.corda.v5.crypto.SecureHash
import java.util.NavigableSet

/** Identifies a sandboxed class during serialisation and deserialisation. */
sealed class ClassTag

/** Identifies a sandboxed class during Kryo serialisation and deserialisation. */
class KryoClassTag(val cpkFileHash: SecureHash, val classBundleName: String) : ClassTag()

// TODO - Replace public key hashes with summary of hashes.
/** Identifies a sandboxed class during AMQP serialisation and deserialisation. */
class AMQPClassTag(
    val cordappBundleName: String,
    val cpkPublicKeyHashes: NavigableSet<SecureHash>,
    val classBundleName: String
) : ClassTag()