package net.corda.sandbox

import net.corda.v5.crypto.SecureHash
import java.util.NavigableSet

/** Identifies a sandboxed class during serialisation and deserialisation. */
interface ClassTag {
    // Indicates whether the class is a platform class. If so, the other values can be ignored.
    val isPlatformClass: Boolean
    // The symbolic name of the bundle that the class is from.
    val classBundleName: String
}

/**
 * Identifies a sandboxed class based on the exact CPK version it comes from.
 *
 * @param cpkFileHash The hash of the CPK that the class is from.
 */
data class StaticTag(
    val cpkFileHash: SecureHash,
    override val isPlatformClass: Boolean,
    override val classBundleName: String
) : ClassTag

// TODO - Replace public key hashes with summary of hashes.
/**
 * Identifies a sandboxed class in an evolvable way.
 *
 * @param cordappBundleName The symbolic name of the CorDapp bundle of the CPK that the class if from.
 * @param cpkPublicKeyHashes The hashes of the public keys that signed the CPK the class was loaded from.
 */
data class EvolvableTag(
    val cordappBundleName: String,
    val cpkPublicKeyHashes: NavigableSet<SecureHash>,
    override val isPlatformClass: Boolean,
    override val classBundleName: String
) : ClassTag