package net.corda.sandbox.internal

import net.corda.packaging.Cpk
import net.corda.v5.crypto.SecureHash
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.Version
import java.security.MessageDigest
import kotlin.random.Random

const val PLATFORM_BUNDLE_NAME = "platform_bundle_symbolic_name"
const val CPK_BUNDLE_NAME = "cpk_bundle_symbolic_name"
const val CORDAPP_BUNDLE_NAME = "cordapp_bundle_symbolic_name"

/** Generates a random [SecureHash]. */
fun randomSecureHash(): SecureHash {
    val allowedChars = '0'..'9'
    val randomBytes = (1..16).map { allowedChars.random() }.joinToString("").toByteArray()
    val digest = MessageDigest.getInstance(HASH_ALGORITHM)
    return SecureHash(digest.algorithm, digest.digest(randomBytes))
}

/** Generates a mock [Bundle] with the given [bundleSymbolicName] and [bundleVersion]. */
fun mockBundle(bundleSymbolicName: String = Random.nextInt().toString(), bundleVersion: String = "0.0") =
    mock<Bundle>().apply {
        whenever(symbolicName).thenReturn(bundleSymbolicName)
        whenever(version).thenReturn(Version.parseVersion(bundleVersion))
    }

/** Generates a mock [Cpk.Expanded]. */
fun mockCpk(): Cpk.Expanded {
    val dummyCpkIdentifier = Cpk.Identifier("", "", randomSecureHash())
    val mockCpkFileHash = randomSecureHash()

    return mock<Cpk.Expanded>().apply {
        whenever(id).thenReturn(dummyCpkIdentifier)
        whenever(cpkHash).thenReturn(mockCpkFileHash)
    }
}