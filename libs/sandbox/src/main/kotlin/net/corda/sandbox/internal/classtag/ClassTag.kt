package net.corda.sandbox.internal.classtag

import net.corda.v5.crypto.SecureHash

/**
 * Identifies a sandboxed class during serialisation and deserialisation.
 *
 * @param version The version of the class tag.
 * @param isPublicClass Indicates whether the class is a class in a public sandbox.
 * @param classBundleName The symbolic name of the bundle that the class was loaded from.
 */
sealed class ClassTag(val version: Int, val isPublicClass: Boolean, val classBundleName: String) {
    /**
     * Serializes the class tag.
     *
     * Regardless of [version], serialised class tags must obey the format "classTagIdentifier$version$<otherEntries>".
     * For example, "armAllowance$99$feedback" and "strike$1$premium$leaf" are valid serialised class tags.
     */
    abstract fun serialise(): String
}

/**
 * Identifies a sandboxed class based on the exact CPK version it comes from.
 *
 * @param cpkFileHash The hash of the CPK the class was loaded from.
 */
abstract class StaticTag(version: Int, isPublicClass: Boolean, classBundleName: String, val cpkFileHash: SecureHash) :
    ClassTag(version, isPublicClass, classBundleName)

/**
 * Identifies a sandboxed class in an evolvable way.
 *
 * @param cordappBundleName The symbolic name of the CorDapp bundle of the CPK that the class if from.
 * @param cpkSignerSummaryHash A summary hash of the hashes of the public keys that signed the CPK the class is from.
 */
abstract class EvolvableTag(
    version: Int,
    isPublicClass: Boolean,
    classBundleName: String,
    val cordappBundleName: String,
    val cpkSignerSummaryHash: SecureHash?
) : ClassTag(version, isPublicClass, classBundleName)