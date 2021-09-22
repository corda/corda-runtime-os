package net.corda.sandbox.internal

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
import java.util.NavigableSet

// Various dummy serialised class tags.
private const val CPK_STATIC_TAG = "serialised_static_cpk_class"
private const val PLATFORM_STATIC_TAG = "serialised_static_platform_class"
private const val BAD_CPK_FILE_HASH_STATIC_TAG = "serialised_static_bad_cpk_file_hash"
private const val CPK_EVOLVABLE_TAG = "serialised_evolvable_cpk_class"
private const val PLATFORM_EVOLVABLE_TAG = "serialised_evolvable_platform_class"
private const val BAD_CORDAPP_BUNDLE_NAME_EVOLVABLE_TAG = "serialised_evolvable_bad_cordapp_bundle_name"
private const val BAD_SIGNERS_EVOLVABLE_TAG = "serialised_evolvable_bad_signers"

/**
 * Tests of [SandboxGroupImpl].
 *
 * There are no tests of the sandbox-retrieval and class-loading functionality, since this is likely to be deprecated.
 */
class SandboxGroupImplTests {
    private val cpkClass = String::class.java
    private val platformClass = Int::class.java
    private val nonBundleClass = Boolean::class.java
    private val nonSandboxClass = Float::class.java

    private val mockCpkBundle = mockBundle(CPK_BUNDLE_NAME)
    private val mockPlatformBundle = mockBundle(PLATFORM_BUNDLE_NAME)
    private val mockNonSandboxBundle = mock<Bundle>()
    private val mockCordappBundle = mockBundle(CORDAPP_BUNDLE_NAME)

    private val mockCpk = mockCpk()

    private val mockCpkSandbox = mock<CpkSandboxInternal>().apply {
        whenever(cpk).thenReturn(mockCpk)
        whenever(containsBundle(mockCpkBundle)).thenReturn(true)
        whenever(cordappBundle).thenReturn(mockCordappBundle)
        whenever(loadClass(cpkClass.name, CPK_BUNDLE_NAME)).thenReturn(cpkClass)
    }
    private val mockPlatformSandbox = mock<SandboxInternal>().apply {
        whenever(containsBundle(mockPlatformBundle)).thenReturn(true)
        whenever(loadClass(platformClass.name, PLATFORM_BUNDLE_NAME)).thenReturn(platformClass)
    }

    private val mockBundleUtils = mock<BundleUtils>().apply {
        whenever(getBundle(cpkClass)).thenReturn(mockCpkBundle)
        whenever(getBundle(platformClass)).thenReturn(mockPlatformBundle)
        whenever(getBundle(nonSandboxClass)).thenReturn(mockNonSandboxBundle)
    }

    private val sandboxesById = mapOf(mockCpk.id to mockCpkSandbox)
    private val classTagFactory = DummyClassTagFactory(mockCpk.cpkHash, mockCpk.id.signers)
    private val sandboxGroupImpl =
        SandboxGroupImpl(mockBundleUtils, sandboxesById, mockPlatformSandbox, classTagFactory)

    @Test
    fun `creates valid static tag for a CPK class`() {
        val expectedTag = "true;false;$mockCpkBundle;$mockCpkSandbox"
        assertEquals(expectedTag, sandboxGroupImpl.getStaticTag(cpkClass))
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
    fun `creates valid evolvable tag for a CPK class`() {
        val expectedTag = "false;false;$mockCpkBundle;$mockCpkSandbox"
        assertEquals(expectedTag, sandboxGroupImpl.getEvolvableTag(cpkClass))
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
    fun `returns CPK class identified by a static tag`() {
        assertEquals(cpkClass, sandboxGroupImpl.getClass(cpkClass.name, CPK_STATIC_TAG))
    }

    @Test
    fun `returns platform class identified by a static tag`() {
        assertEquals(platformClass, sandboxGroupImpl.getClass(platformClass.name, PLATFORM_STATIC_TAG))
    }

    @Test
    fun `returns CPK class identified by an evolvable tag`() {
        assertEquals(cpkClass, sandboxGroupImpl.getClass(cpkClass.name, CPK_EVOLVABLE_TAG))
    }

    @Test
    fun `returns platform class identified by an evolvable tag`() {
        assertEquals(platformClass, sandboxGroupImpl.getClass(platformClass.name, PLATFORM_EVOLVABLE_TAG))
    }

    @Test
    fun `throws if asked to return class but cannot find matching sandbox for a static tag`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(cpkClass.name, BAD_CPK_FILE_HASH_STATIC_TAG)
        }
    }

    @Test
    fun `throws if asked to return class but cannot find matching sandbox for an evolvable tag`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(cpkClass.name, BAD_CORDAPP_BUNDLE_NAME_EVOLVABLE_TAG)
        }
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(cpkClass.name, BAD_SIGNERS_EVOLVABLE_TAG)
        }
    }

    @Test
    fun `throws if asked to return class but cannot find class in matching sandbox`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(nonSandboxClass.name, CPK_STATIC_TAG)
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
    cpkSigners: NavigableSet<SecureHash>
) :
    EvolvableTag(1, isPlatformClass, classBundleName, cordappBundleName, cpkSigners) {
    override fun serialise() = ""
}

/** A dummy [ClassTagFactory] implementation that returns */
private class DummyClassTagFactory(cpkHash: SecureHash, cpkSigners: NavigableSet<SecureHash>) : ClassTagFactory {
    private val cpkStaticTag =
        StaticTagImpl(false, CPK_BUNDLE_NAME, cpkHash)

    private val platformStaticTag =
        StaticTagImpl(true, PLATFORM_BUNDLE_NAME, ClassTagV1.PLACEHOLDER_CPK_FILE_HASH)

    private val invalidCpkFileHashStaticTag =
        StaticTagImpl(false, CPK_BUNDLE_NAME, randomSecureHash())


    private val cpkEvolvableTag =
        EvolvableTagImpl(false, CPK_BUNDLE_NAME, CORDAPP_BUNDLE_NAME, cpkSigners)

    private val platformEvolvableTag =
        EvolvableTagImpl(true,
        PLATFORM_BUNDLE_NAME,
        ClassTagV1.PLACEHOLDER_CORDAPP_BUNDLE_NAME,
        ClassTagV1.PLACEHOLDER_CPK_PUBLIC_KEY_HASHES
    )

    private val invalidCordappBundleNameEvolvableTag =
        EvolvableTagImpl(
        false,
        CPK_BUNDLE_NAME,
        "invalid_cordapp_bundle_name",
        cpkSigners
    )

    private val invalidSignersEvolvableTag =
        EvolvableTagImpl(false, CPK_BUNDLE_NAME, CORDAPP_BUNDLE_NAME, randomSigners())

    override fun createSerialised(
        isStaticClassTag: Boolean,
        isPlatformBundle: Boolean,
        bundle: Bundle,
        sandbox: Sandbox
    ) = "$isStaticClassTag;$isPlatformBundle;$bundle;$sandbox"

    override fun deserialise(serialisedClassTag: String): ClassTag {
        return when (serialisedClassTag) {
            CPK_STATIC_TAG -> cpkStaticTag
            PLATFORM_STATIC_TAG -> platformStaticTag
            BAD_CPK_FILE_HASH_STATIC_TAG -> invalidCpkFileHashStaticTag
            CPK_EVOLVABLE_TAG -> cpkEvolvableTag
            PLATFORM_EVOLVABLE_TAG -> platformEvolvableTag
            BAD_CORDAPP_BUNDLE_NAME_EVOLVABLE_TAG -> invalidCordappBundleNameEvolvableTag
            BAD_SIGNERS_EVOLVABLE_TAG -> invalidSignersEvolvableTag
            else -> throw IllegalArgumentException("Could not deserialise tag.")
        }
    }
}