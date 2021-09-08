package net.corda.sandbox.internal

import net.corda.packaging.Cpk
import net.corda.sandbox.AMQPClassTag
import net.corda.sandbox.ClassTag
import net.corda.sandbox.KryoClassTag
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.sandbox.CpkSandboxInternal
import net.corda.sandbox.internal.sandbox.SandboxInternal
import net.corda.sandbox.internal.utilities.BundleUtils
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import java.util.Collections
import java.util.TreeSet

/**
 * Tests of [SandboxGroupImpl].
 *
 * There are no tests of the sandbox-retrieval and class-loading functionality, since this is likely to be deprecated.
 */
class SandboxGroupImplTests {
    companion object {
        private const val NON_PLATFORM_BUNDLE_NAME = "bundle_symbolic_name"
        private const val PLATFORM_BUNDLE_NAME = "platform_bundle_symbolic_name"
        private const val CORDAPP_BUNDLE_NAME = "cordapp_bundle_symbolic_name"
        private const val PLACEHOLDER_CORDAPP_BUNDLE_NAME = "PLATFORM_BUNDLE"
        private val PLACEHOLDER_CPK_FILE_HASH = SecureHash.create("SHA-256:0000000000000000")
        private val PLACEHOLDER_CPK_PUBLIC_KEY_HASHES = Collections.emptyNavigableSet<SecureHash>()
    }

    private val nonPlatformClass = String::class.java
    private val platformClass = Int::class.java
    private val nonBundleClass = Boolean::class.java
    private val nonSandboxClass = Float::class.java

    private val mockNonPlatformBundle = mock<Bundle>().apply {
        whenever(symbolicName).thenReturn(NON_PLATFORM_BUNDLE_NAME)
    }
    private val mockPlatformBundle = mock<Bundle>().apply {
        whenever(symbolicName).thenReturn(PLATFORM_BUNDLE_NAME)
    }
    private val mockNonSandboxBundle = mock<Bundle>()
    private val mockCordappBundle = mock<Bundle>().apply {
        whenever(symbolicName).thenReturn(CORDAPP_BUNDLE_NAME)
    }

    private val mockCpk = createMockCpk()

    private val mockNonPlatformSandbox = mock<CpkSandboxInternal>().apply {
        whenever(cpk).thenReturn(mockCpk)
        whenever(containsBundle(mockNonPlatformBundle)).thenReturn(true)
        whenever(cordappBundle).thenReturn(mockCordappBundle)
        whenever(loadClass(nonPlatformClass.name, NON_PLATFORM_BUNDLE_NAME)).thenReturn(nonPlatformClass)
    }
    private val mockPlatformSandbox = mock<SandboxInternal>().apply {
        whenever(containsBundle(mockPlatformBundle)).thenReturn(true)
        whenever(loadClass(platformClass.name, PLATFORM_BUNDLE_NAME)).thenReturn(platformClass)
    }

    private val mockBundleUtils = mock<BundleUtils>().apply {
        whenever(getBundle(nonPlatformClass)).thenReturn(mockNonPlatformBundle)
        whenever(getBundle(platformClass)).thenReturn(mockPlatformBundle)
        whenever(getBundle(nonSandboxClass)).thenReturn(mockNonSandboxBundle)
    }

    private val sandboxesById = mapOf(mockCpk.id to mockNonPlatformSandbox)
    private val sandboxGroupImpl = SandboxGroupImpl(mockBundleUtils, sandboxesById, mockPlatformSandbox)

    private val kryoNonPlatformClassTag = KryoClassTag(mockCpk.cpkHash, false, NON_PLATFORM_BUNDLE_NAME)
    private val kryoPlatformClassTag = KryoClassTag(PLACEHOLDER_CPK_FILE_HASH, true, PLATFORM_BUNDLE_NAME)

    private val amqpNonPlatformClassTag = AMQPClassTag(
        mockNonPlatformSandbox.cordappBundle.symbolicName,
        mockCpk.id.signers,
        false,
        NON_PLATFORM_BUNDLE_NAME)
    private val amqpPlatformClassTag = AMQPClassTag(
        PLACEHOLDER_CORDAPP_BUNDLE_NAME,
        PLACEHOLDER_CPK_PUBLIC_KEY_HASHES,
        true,
        PLATFORM_BUNDLE_NAME)

    /** Generates a random [SecureHash]. */
    private fun randomSecureHash(): SecureHash {
        val allowedChars = '0'..'9'
        val hash = (1..16).map { allowedChars.random() }.joinToString("")
        return SecureHash.create("SHA-256:$hash")
    }

    /** Generates a mock [Cpk.Expanded]. */
    private fun createMockCpk(): Cpk.Expanded {
        val mockCpkSigners = TreeSet(setOf(randomSecureHash()))
        val mockCpkIdentifier = mock<Cpk.Identifier>().apply {
            whenever(signers).thenReturn(mockCpkSigners)
        }
        val mockCpkFileHash = randomSecureHash()
        return mock<Cpk.Expanded>().apply {
            whenever(id).thenReturn(mockCpkIdentifier)
            whenever(cpkHash).thenReturn(mockCpkFileHash)
        }
    }

    @Test
    fun `creates valid Kryo class tag for a non-platform class`() {
        assertEquals(kryoNonPlatformClassTag, sandboxGroupImpl.getKryoClassTag(nonPlatformClass))
    }

    @Test
    fun `creates valid Kryo class tag for a platform class`() {
        assertEquals(kryoPlatformClassTag, sandboxGroupImpl.getKryoClassTag(platformClass))
    }

    @Test
    fun `returns null if asked to create Kryo class tag for a class outside any bundle`() {
        assertNull(sandboxGroupImpl.getKryoClassTag(nonBundleClass))
    }

    @Test
    fun `returns null if asked to create Kryo class tag for a class in a bundle not in the sandbox group`() {
        assertNull(sandboxGroupImpl.getKryoClassTag(nonSandboxClass))
    }

    @Test
    fun `creates valid AMQP class tag for a non-platform class`() {
        assertEquals(amqpNonPlatformClassTag, sandboxGroupImpl.getAMQPClassTag(nonPlatformClass))
    }

    @Test
    fun `creates valid AMQP class tag for a platform class`() {
        assertEquals(amqpPlatformClassTag, sandboxGroupImpl.getAMQPClassTag(platformClass))
    }

    @Test
    fun `returns null if asked to create AMQP class tag for a class outside any bundle`() {
        assertNull(sandboxGroupImpl.getAMQPClassTag(nonBundleClass))
    }

    @Test
    fun `returns null if asked to create AMQP class tag for a class in a bundle not in the sandbox group`() {
        assertNull(sandboxGroupImpl.getAMQPClassTag(nonSandboxClass))
    }

    @Test
    fun `returns non-platform class identified by a Kryo class tag`() {
        assertEquals(nonPlatformClass, sandboxGroupImpl.getClass(nonPlatformClass.name, kryoNonPlatformClassTag))
    }

    @Test
    fun `returns platform class identified by a Kryo class tag`() {
        assertEquals(platformClass, sandboxGroupImpl.getClass(platformClass.name, kryoPlatformClassTag))
    }

    @Test
    fun `returns non-platform class identified by an AMQP class tag`() {
        assertEquals(nonPlatformClass, sandboxGroupImpl.getClass(nonPlatformClass.name, amqpNonPlatformClassTag))
    }

    @Test
    fun `returns platform class identified by an AMQP class tag`() {
        assertEquals(platformClass, sandboxGroupImpl.getClass(platformClass.name, amqpPlatformClassTag))
    }

    @Test
    fun `returns null if asked to return class but cannot find matching sandbox for the Kryo class tag`() {
        val invalidCpkFileHash = randomSecureHash()
        val kryoClassTag = KryoClassTag(invalidCpkFileHash, false, NON_PLATFORM_BUNDLE_NAME)
        assertNull(sandboxGroupImpl.getClass(nonPlatformClass.name, kryoClassTag))
    }

    @Test
    fun `returns null if asked to return class but cannot find matching sandbox for the AMQP class tag`() {
        val invalidCordappBundleName = "invalid_cordapp_bundle_name"
        val invalidCordappBundleNameAmqpClassTag = AMQPClassTag(
            invalidCordappBundleName,
            mockCpk.id.signers,
            false,
            NON_PLATFORM_BUNDLE_NAME)
        assertNull(sandboxGroupImpl.getClass(nonPlatformClass.name, invalidCordappBundleNameAmqpClassTag))

        val invalidSigners = TreeSet(setOf(randomSecureHash()))
        val invalidSignersAmqpClassTag = AMQPClassTag(
            mockNonPlatformSandbox.cordappBundle.symbolicName,
            invalidSigners,
            false,
            NON_PLATFORM_BUNDLE_NAME)
        assertNull(sandboxGroupImpl.getClass(nonPlatformClass.name, invalidSignersAmqpClassTag))
    }

    @Test
    fun `returns null if asked to return class but cannot find class in matching sandbox`() {
        assertNull(sandboxGroupImpl.getClass(nonSandboxClass.name, kryoNonPlatformClassTag))
    }

    @Test
    fun `throws if asked to return class identified by an unrecognised tag type`() {
        val unrecognisedClassTag = object : ClassTag {
            override val isPlatformClass: Boolean = false
            override val classBundleName: String = ""
        }
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(platformClass.name, unrecognisedClassTag)
        }
    }
}