package net.corda.sandbox

import net.corda.v5.crypto.SecureHash
import org.osgi.framework.Version

/** The restricted information about a class's bundle and CPK that is provided to `ClassInfoService`. */
sealed class ClassInfo {
    // The symbolic name of the bundle the class was loaded from.
    abstract val classBundleName: String

    // The version of the bundle the class was loaded from.
    abstract val classBundleVersion: Version
}

/** A [ClassInfo] for a class loaded from a public sandbox. */
data class PublicClassInfo(override val classBundleName: String, override val classBundleVersion: Version) : ClassInfo()

/**
 * A [ClassInfo] for a class from a CPK.
 *
 * @param mainBundleName The symbolic name of the main bundle of the CPK the class was loaded from.
 * @param mainBundleVersion The version of the main bundle of the CPK the class was loaded from.
 * @param cpkFileHash The hash of the CPK the class was loaded from.
 * @param cpkSignerSummaryHash A summary hash of the hashes of the public keys that signed the CPK the class is from.
 * @param cpkDependencies The hashes of the CPK's dependencies.
 */
data class CpkClassInfo(
    override val classBundleName: String,
    override val classBundleVersion: Version,
    val mainBundleName: String,
    val mainBundleVersion: Version,
    val cpkFileHash: SecureHash,
    val cpkSignerSummaryHash: SecureHash?,
    val cpkDependencies: Set<SecureHash>
) : ClassInfo()