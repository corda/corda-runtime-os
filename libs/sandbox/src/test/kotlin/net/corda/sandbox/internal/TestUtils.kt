package net.corda.sandbox.internal

import net.corda.packaging.Cpk
import net.corda.v5.crypto.SecureHash
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.Version
import java.util.TreeSet
import kotlin.random.Random

const val NON_PLATFORM_BUNDLE_NAME = "non_platform_bundle_symbolic_name"
const val PLATFORM_BUNDLE_NAME = "platform_bundle_symbolic_name"
const val CORDAPP_BUNDLE_NAME = "cordapp_bundle_symbolic_name"

/** Generates a random [SecureHash]. */
fun randomSecureHash(): SecureHash {
    val allowedChars = '0'..'9'
    val hash = (1..16).map { allowedChars.random() }.joinToString("")
    return SecureHash.create("SHA-256:$hash")
}

/** Generates a random set of CPK signers. */
fun randomSigners() = TreeSet(setOf(randomSecureHash()))

/** Generates a mock [Bundle] with the given [bundleSymbolicName] and [bundleVersion]. */
fun mockBundle(bundleSymbolicName: String = Random.nextInt().toString(), bundleVersion: String = "0.0") =
    mock<Bundle>().apply {
        whenever(symbolicName).thenReturn(bundleSymbolicName)
        whenever(version).thenReturn(Version.parseVersion(bundleVersion))
    }

/** Generates a mock [Cpk.Expanded]. */
fun mockCpk(): Cpk.Expanded {
    val mockCpkSigners = randomSigners()
    val mockCpkIdentifier = mock<Cpk.Identifier>().apply {
        whenever(signers).thenReturn(mockCpkSigners)
    }
    val mockCpkFileHash = randomSecureHash()
    return mock<Cpk.Expanded>().apply {
        whenever(id).thenReturn(mockCpkIdentifier)
        whenever(cpkHash).thenReturn(mockCpkFileHash)
    }
}