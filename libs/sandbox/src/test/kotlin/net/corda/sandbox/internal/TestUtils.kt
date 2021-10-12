package net.corda.sandbox.internal

import net.corda.packaging.CPK
import net.corda.v5.base.util.toHex
import net.corda.v5.crypto.SecureHash
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.Version
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.random.Random.Default.nextBytes
import kotlin.random.Random.Default.nextInt
import kotlin.random.Random.Default.nextLong

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
    bundleSymbolicName: String = nextInt().toString(),
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

/** Generates a mock [CPK]. */
fun mockCpk(): CPK {
    val dummyCpkIdentifier = CPK.Identifier.newInstance(nextBytes(ByteArray(8)).toHex(), "1.0", randomSecureHash())
    val mockCpkFileHash = randomSecureHash()

    val metadataMock = mock<CPK.Metadata>().apply {
        whenever(id).thenReturn(dummyCpkIdentifier)
        whenever(hash).thenReturn(mockCpkFileHash)
    }

    return mock<CPK>().apply {
        whenever(metadata).thenReturn(metadataMock)
    }
}