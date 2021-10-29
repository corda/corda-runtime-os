package net.corda.sandbox.internal.classtag

import net.corda.v5.crypto.SecureHash

// TODO - Introduce third class type.
/** Represents the source of the class in a [ClassTag]. */
enum class ClassType { CpkSandboxClass, PublicSandboxClass }

/**
 * Identifies a sandboxed class during serialisation and deserialisation.
 *
 * @property version The version of the class tag.
 * @property classType Indicates whether the class is a CPK sandbox class or a public sandbox class.
 * @property classBundleName The symbolic name of the bundle that the class was loaded from.
 */
sealed class ClassTag {
    abstract val version: Int
    abstract val classType: ClassType
    abstract val classBundleName: String

    /**
     * Serializes the class tag.
     *
     * Regardless of [version], serialized class tags must obey the format "classTagIdentifier$version$<otherEntries>".
     * For example, "armAllowance$99$feedback" and "strike$1$premium$leaf" are valid serialized class tags.
     */
    abstract fun serialise(): String
}

/**
 * Identifies a sandboxed class based on the exact CPK version it was loaded from.
 *
 * @property cpkFileHash The hash of the CPK the class was loaded from.
 */
abstract class StaticTag : ClassTag() {
    abstract val cpkFileHash: SecureHash
}

/**
 * Identifies a sandboxed class based on the CPK's main bundle name and signers.
 *
 * @property mainBundleName The symbolic name of the main bundle of the CPK that the class if from.
 * @property cpkSignerSummaryHash A summary hash of the hashes of the public keys that signed the CPK the class is from.
 */
abstract class EvolvableTag : ClassTag() {
    abstract val mainBundleName: String
    abstract val cpkSignerSummaryHash: SecureHash?
}