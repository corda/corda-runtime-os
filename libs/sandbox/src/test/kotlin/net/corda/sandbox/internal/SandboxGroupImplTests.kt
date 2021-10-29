package net.corda.sandbox.internal

import net.corda.packaging.CPK
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.classtag.ClassTag
import net.corda.sandbox.internal.classtag.ClassTagFactory
import net.corda.sandbox.internal.classtag.EvolvableTag
import net.corda.sandbox.internal.classtag.StaticTag
import net.corda.sandbox.internal.sandbox.CpkSandboxImpl
import net.corda.sandbox.internal.sandbox.CpkSandboxInternal
import net.corda.sandbox.internal.utilities.BundleUtils
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import java.util.UUID.randomUUID

// Various dummy serialised class tags.
private const val CPK_STATIC_TAG = "serialised_static_cpk_class"
private const val NON_CPK_STATIC_TAG = "serialised_static_non_cpk_class"
private const val BAD_CPK_FILE_HASH_STATIC_TAG = "serialised_static_bad_cpk_file_hash"
private const val CPK_EVOLVABLE_TAG = "serialised_evolvable_cpk_class"
private const val NON_CPK_EVOLVABLE_TAG = "serialised_evolvable_non_cpk_class"
private const val BAD_CORDAPP_BUNDLE_NAME_EVOLVABLE_TAG = "serialised_evolvable_bad_cordapp_bundle_name"
private const val BAD_SIGNERS_EVOLVABLE_TAG = "serialised_evolvable_bad_signers"
private const val BAD_BUNDLE_NAME_STATIC_TAG = "serialised_static_bad_bundle_name"

/**
 * Tests of [SandboxGroupImpl].
 *
 * There are no tests of the sandbox-retrieval and class-loading functionality, since this is likely to be deprecated.
 */
class SandboxGroupImplTests {
    private val cpkClass = String::class.java
    private val nonCpkClass = Int::class.java
    private val nonBundleClass = Boolean::class.java

    private val mockCpkBundle = mockBundle(CPK_BUNDLE_NAME, cpkClass)
    private val mockNonCpkBundle = mockBundle(NON_CPK_BUNDLE_NAME, nonCpkClass)
    private val mockCordappBundle = mockBundle(CORDAPP_BUNDLE_NAME)

    private val mockBundleUtils = mock<BundleUtils>().apply {
        whenever(getBundle(cpkClass)).thenReturn(mockCpkBundle)
        whenever(getBundle(nonCpkClass)).thenReturn(mockNonCpkBundle)
        whenever(allBundles).thenReturn(listOf(mockCpkBundle, mockNonCpkBundle))
    }

    private val cpkSandbox =
        CpkSandboxImpl(mockBundleUtils, randomUUID(), mockCpk(), mockCordappBundle, setOf(mockCpkBundle))

    private val sandboxGroupImpl = SandboxGroupImpl(
        mockBundleUtils,
        mapOf(cpkSandbox.cpk.metadata.id to cpkSandbox),
        DummyClassTagFactory(cpkSandbox.cpk)
    )

    @Test
    fun `creates valid static tag for a CPK class`() {
        val expectedTag = "true;$mockCpkBundle;$cpkSandbox"
        assertEquals(expectedTag, sandboxGroupImpl.getStaticTag(cpkClass))
    }

    @Test
    fun `creates valid static tag for a non-CPK class`() {
        val expectedTag = "true;$mockNonCpkBundle;null"
        assertEquals(expectedTag, sandboxGroupImpl.getStaticTag(nonCpkClass))
    }

    @Test
    fun `returns null if asked to create static tag for a class outside any bundle`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getStaticTag(nonBundleClass)
        }
    }

    @Test
    fun `creates valid evolvable tag for a CPK class`() {
        val expectedTag = "false;$mockCpkBundle;$cpkSandbox"
        assertEquals(expectedTag, sandboxGroupImpl.getEvolvableTag(cpkClass))
    }

    @Test
    fun `creates valid evolvable tag for a non-CPK class`() {
        val expectedTag = "false;$mockNonCpkBundle;null"
        assertEquals(expectedTag, sandboxGroupImpl.getEvolvableTag(nonCpkClass))
    }

    @Test
    fun `throws if asked to create evolvable tag for a class outside any bundle`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getEvolvableTag(nonBundleClass)
        }
    }

    @Test
    fun `returns CPK class identified by a static tag`() {
        assertEquals(cpkClass, sandboxGroupImpl.getClass(cpkClass.name, CPK_STATIC_TAG))
    }

    @Test
    fun `returns non-CPK class identified by a static tag`() {
        assertEquals(nonCpkClass, sandboxGroupImpl.getClass(nonCpkClass.name, NON_CPK_STATIC_TAG))
    }

    @Test
    fun `returns CPK class identified by an evolvable tag`() {
        assertEquals(cpkClass, sandboxGroupImpl.getClass(cpkClass.name, CPK_EVOLVABLE_TAG))
    }

    @Test
    fun `returns non-CPK class identified by an evolvable tag`() {
        assertEquals(nonCpkClass, sandboxGroupImpl.getClass(nonCpkClass.name, NON_CPK_EVOLVABLE_TAG))
    }

    @Test
    fun `throws if asked to return CPK class but cannot find matching sandbox for a static tag`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(cpkClass.name, BAD_CPK_FILE_HASH_STATIC_TAG)
        }
    }

    @Test
    fun `throws if asked to return CPK class but cannot find matching sandbox for an evolvable tag`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(cpkClass.name, BAD_CORDAPP_BUNDLE_NAME_EVOLVABLE_TAG)
        }
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(cpkClass.name, BAD_SIGNERS_EVOLVABLE_TAG)
        }
    }

    @Test
    fun `throws if asked to return CPK class but cannot find class in matching sandbox`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(nonCpkClass.name, CPK_STATIC_TAG)
        }
    }

    @Test
    fun `throws if asked to return non-CPK class but cannot find bundle with matching name`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(nonCpkClass.name, BAD_BUNDLE_NAME_STATIC_TAG)
        }
    }

    @Test
    fun `throws if asked to return non-CPK class but cannot find class in bundle with matching name`() {
        assertThrows<SandboxException> {
            sandboxGroupImpl.getClass(cpkClass.name, NON_CPK_STATIC_TAG)
        }
    }
}

/** A dummy [StaticTag] implementation. */
private class StaticTagImpl(isCpkClass: Boolean, classBundleName: String, cpkHash: SecureHash) :
    StaticTag(1, isCpkClass, classBundleName, cpkHash) {
    override fun serialise() = ""
}

/** A dummy [EvolvableTag] implementation. */
private class EvolvableTagImpl(
    isCpkClass: Boolean,
    classBundleName: String,
    cordappBundleName: String,
    cpkSignerSummaryHash: SecureHash?
) :
    EvolvableTag(1, isCpkClass, classBundleName, cordappBundleName, cpkSignerSummaryHash) {
    override fun serialise() = ""
}

/** A dummy [ClassTagFactory] implementation that returns pre-defined tags. */
private class DummyClassTagFactory(cpk: CPK) : ClassTagFactory {
    // Used for non-CPK classes, where the CorDapp bundle name, CPK file hash and CPK signer summary hash are ignored.
    val dummyCordappBundleName = "dummyCordappBundleName"
    val dummyHash = SecureHash.create("SHA-256:0000000000000000")

    private val cpkStaticTag =
        StaticTagImpl(true, CPK_BUNDLE_NAME, cpk.metadata.hash)

    private val nonCpkStaticTag =
        StaticTagImpl(false, NON_CPK_BUNDLE_NAME, dummyHash)

    private val invalidCpkFileHashStaticTag =
        StaticTagImpl(true, CPK_BUNDLE_NAME, randomSecureHash())

    private val cpkEvolvableTag =
        EvolvableTagImpl(true, CPK_BUNDLE_NAME, CORDAPP_BUNDLE_NAME, cpk.metadata.id.signerSummaryHash)

    private val nonCpkEvolvableTag =
        EvolvableTagImpl(
            false,
            NON_CPK_BUNDLE_NAME,
            dummyCordappBundleName,
            dummyHash
        )

    private val invalidCordappBundleNameCpkEvolvableTag =
        EvolvableTagImpl(
            true,
            CPK_BUNDLE_NAME,
            "invalid_cordapp_bundle_name",
            cpk.metadata.id.signerSummaryHash
        )

    private val invalidSignersCpkEvolvableTag =
        EvolvableTagImpl(true, CPK_BUNDLE_NAME, CORDAPP_BUNDLE_NAME, randomSecureHash())

    private val invalidBundleNameNonCpkStaticTag =
        StaticTagImpl(false, "invalid_bundle_name", dummyHash)

    override fun createSerialised(
        isStaticClassTag: Boolean,
        bundle: Bundle,
        sandbox: CpkSandboxInternal?
    ) = "$isStaticClassTag;$bundle;$sandbox"

    override fun deserialise(serialisedClassTag: String): ClassTag {
        return when (serialisedClassTag) {
            CPK_STATIC_TAG -> cpkStaticTag
            NON_CPK_STATIC_TAG -> nonCpkStaticTag
            BAD_CPK_FILE_HASH_STATIC_TAG -> invalidCpkFileHashStaticTag
            CPK_EVOLVABLE_TAG -> cpkEvolvableTag
            NON_CPK_EVOLVABLE_TAG -> nonCpkEvolvableTag
            BAD_CORDAPP_BUNDLE_NAME_EVOLVABLE_TAG -> invalidCordappBundleNameCpkEvolvableTag
            BAD_SIGNERS_EVOLVABLE_TAG -> invalidSignersCpkEvolvableTag
            BAD_BUNDLE_NAME_STATIC_TAG -> invalidBundleNameNonCpkStaticTag
            else -> throw IllegalArgumentException("Could not deserialise tag.")
        }
    }
}