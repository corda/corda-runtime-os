package net.corda.sandbox

import net.corda.v5.crypto.SecureHash
import java.util.NavigableSet

/**
 * Identifies a sandboxed class during serialisation and deserialisation.
 *
 * @param classBundleName The symbolic name of the bundle that the class is from.
 */
sealed class ClassTag(val classBundleName: String)

/**
 * Identifies a sandboxed class during Kryo serialisation and deserialisation.
 *
 * @param cpkFileHash The hash of the CPK that the class is from.
 */
class KryoClassTag(val cpkFileHash: SecureHash, classBundleName: String) : ClassTag(classBundleName)

// TODO - Replace public key hashes with summary of hashes.
/**
 * Identifies a sandboxed class during AMQP serialisation and deserialisation.
 *
 * @param cordappBundleName The symbolic name of the CorDapp bundle of the CPK that the class if from.
 * @param cpkPublicKeyHashes The hashes of the public keys that signed the CPK the class was loaded from.
 */
class AMQPClassTag(
    val cordappBundleName: String,
    val cpkPublicKeyHashes: NavigableSet<SecureHash>,
    classBundleName: String
) : ClassTag(classBundleName)