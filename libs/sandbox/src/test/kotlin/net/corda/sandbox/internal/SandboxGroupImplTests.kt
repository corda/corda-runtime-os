package net.corda.sandbox.internal

import net.corda.packaging.Cpk
import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.classtag.ClassTag
import net.corda.sandbox.internal.classtag.ClassTagFactory
import net.corda.sandbox.internal.classtag.EvolvableTag
import net.corda.sandbox.internal.classtag.StaticTag
import net.corda.sandbox.internal.sandbox.CpkSandboxInternal
import net.corda.sandbox.internal.sandbox.SandboxInternal
import net.corda.sandbox.internal.utilities.BundleUtils
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle

// Various dummy serialised class tags.
private const val NON_PLATFORM_STATIC_TAG = "serialised_static_non_platform_class"
private const val PLATFORM_STATIC_TAG = "serialised_static_platform_class"
private const val BAD_CPK_FILE_HASH_STATIC_TAG = "serialised_static_bad_cpk_file_hash"
private const val NON_PLATFORM_EVOLVABLE_TAG = "serialised_evolvable_non_platform_class"
private const val PLATFORM_EVOLVABLE_TAG = "serialised_evolvable_platform_class"
private const val BAD_CORDAPP_BUNDLE_NAME_EVOLVABLE_TAG = "serialised_evolvable_bad_cordapp_bundle_name"
private const val BAD_SIGNERS_EVOLVABLE_TAG = "serialised_evolvable_bad_signers"

/**
 * Tests of [SandboxGroupImpl].
 *
 * There are no tests of the sandbox-retrieval and class-loading functionality, since this is likely to be deprecated.
 */
class SandboxGroupImplTests {
    private val nonPlatformClass = String::class.java
    private val platformClass = Int::class.java
    private val nonBundleClass = Boolean::class.java
    private val nonSandboxClass = Float::class.java

    private val mockNonPlatformBundle = mockBundle(NON_PLATFORM_BUNDLE_NAME)
    private val mockPlatformBundle = mockBundle(PLATFORM_BUNDLE_NAME)
    private val mockNonSandboxBundle = mock<Bundle>()
    private val mockCordappBundle = mockBundle(CORDAPP_BUNDLE_NAME)

    private val mockCpk = mockCpk()

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

    private val sandboxesById = mapOf(mockCpk.shortId to mockNonPlatformSandbox)
    private val classTagFactory = DummyClassTagFactory(mockCpk)
    private val sandboxGroupImpl =
        SandboxGroupImpl(mockBundleUtils, sandboxesById, mockPlatformSandbox, classTagFactory)

    @Test
    fun `creates valid static tag for a non-platform class`() {
        val expectedTag = "true;false;$mockNonPlatformBundle;$mockNonPlatformSandbox"
        assertEquals(expectedTag, sandboxGroupImpl.getStaticTag(nonPlatformClass))
    }

    @Test
    fun `creates valid static tag for a platform class`() {
        val expectedTag = "true;true;$mockPlatformBundle;$mockPlatformSandbox"
        assertEquals(expectedTag, sandboxGroupImpl.getStaticTag(platformClass))
    }

    @Test
    fun `returns null if asked to create static tag for a class outside any bundle`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getStaticTag(nonBundleClass)
        }
    }

    @Test
    fun `returns null if asked to create static tag for a class in a bundle not in the sandbox group`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getStaticTag(nonSandboxClass)
        }
    }

    @Test
    fun `creates valid evolvable tag for a non-platform class`() {
        val expectedTag = "false;false;$mockNonPlatformBundle;$mockNonPlatformSandbox"
        assertEquals(expectedTag, sandboxGroupImpl.getEvolvableTag(nonPlatformClass))
    }

    @Test
    fun `creates valid evolvable tag for a platform class`() {
        val expectedTag = "false;true;$mockPlatformBundle;$mockPlatformSandbox"
        assertEquals(expectedTag, sandboxGroupImpl.getEvolvableTag(platformClass))
    }

    @Test
    fun `throws if asked to create evolvable tag for a class outside any bundle`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getEvolvableTag(nonBundleClass)
        }
    }

    @Test
    fun `throws if asked to create evolvable tag for a class in a bundle not in the sandbox group`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getEvolvableTag(nonSandboxClass)
        }
    }

    @Test
    fun `returns non-platform class identified by a static tag`() {
        assertEquals(nonPlatformClass, sandboxGroupImpl.getClass(nonPlatformClass.name, NON_PLATFORM_STATIC_TAG))
    }

    @Test
    fun `returns platform class identified by a static tag`() {
        assertEquals(platformClass, sandboxGroupImpl.getClass(platformClass.name, PLATFORM_STATIC_TAG))
    }

    @Test
    fun `returns non-platform class identified by an evolvable tag`() {
        assertEquals(nonPlatformClass, sandboxGroupImpl.getClass(nonPlatformClass.name, NON_PLATFORM_EVOLVABLE_TAG))
    }

    @Test
    fun `returns platform class identified by an evolvable tag`() {
        assertEquals(platformClass, sandboxGroupImpl.getClass(platformClass.name, PLATFORM_EVOLVABLE_TAG))
    }

    @Test
    fun `throws if asked to return class but cannot find matching sandbox for a static tag`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(nonPlatformClass.name, BAD_CPK_FILE_HASH_STATIC_TAG)
        }
    }

    @Test
    fun `throws if asked to return class but cannot find matching sandbox for an evolvable tag`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(nonPlatformClass.name, BAD_CORDAPP_BUNDLE_NAME_EVOLVABLE_TAG)
        }
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(nonPlatformClass.name, BAD_SIGNERS_EVOLVABLE_TAG)
        }
    }

    @Test
    fun `throws if asked to return class but cannot find class in matching sandbox`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(nonSandboxClass.name, NON_PLATFORM_STATIC_TAG)
        }
    }
}

/** A dummy [StaticTag] implementation. */
private class StaticTagImpl(isPlatformClass: Boolean, classBundleName: String, cpkHash: SecureHash) :
    StaticTag(1, isPlatformClass, classBundleName, cpkHash) {
    override fun serialise() = ""
}

/** A dummy [EvolvableTag] implementation. */
private class EvolvableTagImpl(
    isPlatformClass: Boolean,
    classBundleName: String,
    cordappBundleName: String,
    cpkSignerSummaryHash: SecureHash
) :
    EvolvableTag(1, isPlatformClass, classBundleName, cordappBundleName, cpkSignerSummaryHash) {
    override fun serialise() = ""
}

/** A dummy [ClassTagFactory] implementation that returns pre-defined tags. */
private class DummyClassTagFactory(cpk: Cpk.Expanded) : ClassTagFactory {
    // Used for platform classes, where the CorDapp bundle name, CPK file hash and CPK signer summary hash are ignored.
    val dummyCordappBundleName = "dummyCordappBundleName"
    val dummyHash = SecureHash.create("SHA-256:0000000000000000")

    private val nonPlatformStaticTag =
        StaticTagImpl(false, NON_PLATFORM_BUNDLE_NAME, cpk.cpkHash)

    private val platformStaticTag =
        StaticTagImpl(true, PLATFORM_BUNDLE_NAME, dummyHash)

    private val invalidCpkFileHashStaticTag =
        StaticTagImpl(false, NON_PLATFORM_BUNDLE_NAME, randomSecureHash())

    private val nonPlatformEvolvableTag =
        EvolvableTagImpl(false, NON_PLATFORM_BUNDLE_NAME, CORDAPP_BUNDLE_NAME, cpk.shortId.signerSummaryHash)

    private val platformEvolvableTag =
        EvolvableTagImpl(
            true,
            PLATFORM_BUNDLE_NAME,
            dummyCordappBundleName,
            dummyHash
        )

    private val invalidCordappBundleNameEvolvableTag =
        EvolvableTagImpl(
            false,
            NON_PLATFORM_BUNDLE_NAME,
            "invalid_cordapp_bundle_name",
            cpk.shortId.signerSummaryHash
        )

    private val invalidSignersEvolvableTag =
        EvolvableTagImpl(false, NON_PLATFORM_BUNDLE_NAME, CORDAPP_BUNDLE_NAME, randomSecureHash())

    override fun createSerialised(
        isStaticClassTag: Boolean,
        isPlatformBundle: Boolean,
        bundle: Bundle,
        sandbox: Sandbox
    ) = "$isStaticClassTag;$isPlatformBundle;$bundle;$sandbox"

    override fun deserialise(serialisedClassTag: String): ClassTag {
        return when (serialisedClassTag) {
            NON_PLATFORM_STATIC_TAG -> nonPlatformStaticTag
            PLATFORM_STATIC_TAG -> platformStaticTag
            BAD_CPK_FILE_HASH_STATIC_TAG -> invalidCpkFileHashStaticTag
            NON_PLATFORM_EVOLVABLE_TAG -> nonPlatformEvolvableTag
            PLATFORM_EVOLVABLE_TAG -> platformEvolvableTag
            BAD_CORDAPP_BUNDLE_NAME_EVOLVABLE_TAG -> invalidCordappBundleNameEvolvableTag
            BAD_SIGNERS_EVOLVABLE_TAG -> invalidSignersEvolvableTag
            else -> throw IllegalArgumentException("Could not deserialise tag.")
        }
    }
}