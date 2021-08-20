package net.corda.sandbox

import net.corda.v5.crypto.SecureHash
import org.osgi.framework.Version
import java.util.NavigableSet

/** The restricted information about a class's bundle that is provided to `ClassInfoService`. */
interface ClassInfo {
    val classBundleName: String
    val classBundleVersion: Version
}

/** The restricted information about a class's bundle that is provided to `ClassInfoService` for a platform bundle. */
data class PlatformClassInfo(override val classBundleName: String, override val classBundleVersion: Version) : ClassInfo

/**
 * The restricted information about a class's bundle that is provided to `ClassInfoService` for a bundle derived from a
 * CPK.
 */
data class CpkClassInfo(
    override val classBundleName: String,
    override val classBundleVersion: Version,
    val cpkHash: SecureHash,
    val cpkPublicKeyHashes: NavigableSet<SecureHash>,
    val cpkDependencies: Set<SecureHash>
) : ClassInfo