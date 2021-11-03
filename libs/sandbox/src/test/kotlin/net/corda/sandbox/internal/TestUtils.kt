package net.corda.sandbox.internal

import net.corda.packaging.CPK
import net.corda.v5.crypto.SecureHash
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.Version
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.random.Random
import kotlin.random.Random.Default.nextLong

const val HASH_ALGORITHM = "SHA-256"
const val HASH_LENGTH = 32
const val PUBLIC_BUNDLE_NAME = "public_bundle_symbolic_name"
const val CPK_BUNDLE_NAME = "cpk_bundle_symbolic_name"
const val MAIN_BUNDLE_NAME = "main_bundle_symbolic_name"

val random = Random(0)

/** Generates a random [SecureHash]. */
fun randomSecureHash(): SecureHash {
    val allowedChars = '0'..'9'
    val randomBytes = (1..16).map { allowedChars.random() }.joinToString("").toByteArray()
    val digest = MessageDigest.getInstance(HASH_ALGORITHM)
    return SecureHash(digest.algorithm, digest.digest(randomBytes))
}

/** Generates a mock [Bundle] with [bundleSymbolicName] and [bundleLocation] that contains the given [klass]. */
fun mockBundle(
    bundleSymbolicName: String? = random.nextInt().toString(),
    klass: Class<*>? = null,
    bundleLocation: String = random.nextInt().toString()
) = mock<Bundle>().apply {
    whenever(bundleId).thenReturn(nextLong())
    whenever(symbolicName).thenReturn(bundleSymbolicName)
    whenever(version).thenReturn(Version.parseVersion("${abs(random.nextInt())}.${abs(random.nextInt())}"))
    whenever(loadClass(any())).then { answer ->
        val requestedClass = answer.arguments.single()
        if (klass?.name == requestedClass) klass else throw ClassNotFoundException()
    }
    whenever(location).thenReturn(bundleLocation)
}

/** Generates a mock [CPK]. */
fun mockCpk(): CPK {
    val dummyCpkIdentifier = CPK.Identifier.newInstance(random.nextInt().toString(), "1.0", randomSecureHash())
    val mockCpkFileHash = randomSecureHash()

    val metadataMock = mock<CPK.Metadata>().apply {
        whenever(id).thenReturn(dummyCpkIdentifier)
        whenever(hash).thenReturn(mockCpkFileHash)
    }

    return mock<CPK>().apply {
        whenever(metadata).thenReturn(metadataMock)
    }
}