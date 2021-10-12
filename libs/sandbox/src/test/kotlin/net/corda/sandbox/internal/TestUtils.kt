package net.corda.sandbox.internal

import net.corda.packaging.Cpk
import net.corda.v5.crypto.SecureHash
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.Version
import java.security.MessageDigest
import kotlin.random.Random.Default.nextInt
import kotlin.random.Random.Default.nextLong
import kotlin.math.abs

const val HASH_ALGORITHM = "SHA-256"
const val HASH_LENGTH = 32
const val PUBLIC_BUNDLE_NAME = "public_bundle_symbolic_name"
const val CPK_BUNDLE_NAME = "cpk_bundle_symbolic_name"
const val CORDAPP_BUNDLE_NAME = "cordapp_bundle_symbolic_name"

/** Generates a random [SecureHash]. */
fun randomSecureHash(): SecureHash {
    val allowedChars = '0'..'9'
    val randomBytes = (1..16).map { allowedChars.random() }.joinToString("").toByteArray()
    val digest = MessageDigest.getInstance(HASH_ALGORITHM)
    return SecureHash(digest.algorithm, digest.digest(randomBytes))
}

/** Generates a mock [Bundle] with [bundleSymbolicName] that contains the given [classes]. */
fun mockBundle(
    bundleSymbolicName: String? = nextInt().toString(),
    classes: Collection<Class<*>> = emptySet()
) = mock<Bundle>().apply {
        whenever(bundleId).thenReturn(nextLong())
        whenever(symbolicName).thenReturn(bundleSymbolicName)
        whenever(version).thenReturn(Version.parseVersion("${abs(nextInt())}.${abs(nextInt())}"))
        whenever(loadClass(any())).then { answer ->
            val className = answer.arguments.single()
            classes.find { klass -> klass.name == className } ?: throw ClassNotFoundException()
        }
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