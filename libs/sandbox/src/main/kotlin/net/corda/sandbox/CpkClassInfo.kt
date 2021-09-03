package net.corda.sandbox

import net.corda.v5.crypto.SecureHash
import org.osgi.framework.Version
import java.util.NavigableSet

/** The restricted information about a class's bundle and CPK that is provided to `ClassInfoService`. */
sealed class ClassInfo {
    // The symbolic name of the bundle the class was loaded from.
    abstract val bundleName: String
    // The version of the bundle the class was loaded from.
    abstract val bundleVersion: Version
}

/** A [ClassInfo] for a class from a platform bundle (i.e. one not loaded from a CPK). */
data class PlatformClassInfo(override val bundleName: String, override val bundleVersion: Version) : ClassInfo()

/**
 * A [ClassInfo] for a class from a CPK.
 *
 * @param cpkHash The hash of the CPK the class was loaded from.
 * @param cpkPublicKeyHashes The public key hashes of the CPK the class was loaded from.
 * @param cpkDependencies The hashes of the CPK's dependencies.
 */
data class CpkClassInfo(
    override val bundleName: String,
    override val bundleVersion: Version,
    val cpkHash: SecureHash,
    val cpkPublicKeyHashes: NavigableSet<SecureHash>,
    val cpkDependencies: Set<SecureHash>
) : ClassInfo()