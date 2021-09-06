package net.corda.sandbox

import net.corda.v5.crypto.SecureHash
import org.osgi.framework.Version
import java.util.NavigableSet

/** The restricted information about a class's bundle and CPK that is provided to `ClassInfoService`. */
sealed class ClassInfo {
    // The symbolic name of the bundle the class was loaded from.
    abstract val classBundleName: String

    // The version of the bundle the class was loaded from.
    abstract val classBundleVersion: Version
}

/** A [ClassInfo] for a class not loaded from a CPK (e.g. a platform or JDK class). */
data class NonCpkClassInfo(override val classBundleName: String, override val classBundleVersion: Version) : ClassInfo()

/**
 * A [ClassInfo] for a class from a CPK.
 *
 * @param cordappBundleName The symbolic name of the CorDapp bundle of the CPK the class was loaded from
 * @param cordappBundleVersion The version of the CorDapp bundle of the CPK the class was loaded from
 * @param cpkFileHash The hash of the CPK the class was loaded from.
 * @param cpkPublicKeyHashes The public key hashes of the CPK the class was loaded from.
 * @param cpkDependencies The hashes of the CPK's dependencies.
 */
data class CpkClassInfo(
    override val classBundleName: String,
    override val classBundleVersion: Version,
    val cordappBundleName: String,
    val cordappBundleVersion: Version,
    val cpkFileHash: SecureHash,
    val cpkPublicKeyHashes: NavigableSet<SecureHash>,
    val cpkDependencies: Set<SecureHash>
) : ClassInfo()