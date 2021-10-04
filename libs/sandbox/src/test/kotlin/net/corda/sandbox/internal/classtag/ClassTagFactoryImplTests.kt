package net.corda.sandbox.internal.classtag

import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.CLASS_TAG_DELIMITER
import net.corda.sandbox.internal.CORDAPP_BUNDLE_NAME
import net.corda.sandbox.internal.ClassTagV1
import net.corda.sandbox.internal.CPK_BUNDLE_NAME
import net.corda.sandbox.internal.mockBundle
import net.corda.sandbox.internal.mockCpk
import net.corda.sandbox.internal.sandbox.CpkSandboxInternal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ClassTagFactoryImplTests {
    private val classTagFactory = ClassTagFactoryImpl()

    private val mockBundle = mockBundle(CPK_BUNDLE_NAME)
    private val mockCordappBundle = mockBundle(CORDAPP_BUNDLE_NAME)
    private val mockCpk = mockCpk()
    private val mockSandbox = mock<CpkSandboxInternal>().apply {
        whenever(cpk).thenReturn(mockCpk)
        whenever(cordappBundle).thenReturn(mockCordappBundle)
    }

    /**
     * Returns a serialised class tag.
     *
     * @param classTag The class tag identifier to use.
     * @param version The version to use.
     * @param length The number of entries.
     * @param hash If non-null, the fourth entry of a static tag (the CPK file hash) or the fifth entry of an evolvable
     *  tag (the CPK signers) is set to [hash].
     */
    private fun generateSerialisedTag(classTag: String, version: String, length: Int, hash: String? = null): String {
        val additionalEntries = List(length - 2) { "0" }
        val entries = (listOf(classTag, version) + additionalEntries).toMutableList()

        if (hash != null) {
            if (classTag == ClassTagV1.STATIC_IDENTIFIER) {
                entries[4] = hash
            } else if (classTag == ClassTagV1.EVOLVABLE_IDENTIFIER) {
                entries[5] = hash
            }
        }

        return entries.joinToString(CLASS_TAG_DELIMITER)
    }

    @Test
    fun `can serialise and deserialise a static class tag for a CPK class`() {
        val serialisedTag = classTagFactory.createSerialised(
            isStaticClassTag = true,
            isPublicBundle = false,
            mockBundle,
            mockSandbox
        )

        val classTag = classTagFactory.deserialise(serialisedTag) as StaticTag

        assertEquals(1, classTag.version)
        assertFalse(classTag.isPublicClass)
        assertEquals(mockBundle.symbolicName, classTag.classBundleName)
        assertEquals(mockCpk.cpkHash, classTag.cpkFileHash)
    }

    @Test
    fun `can serialise and deserialise a static class tag for a public class`() {
        val serialisedTag = classTagFactory.createSerialised(
            isStaticClassTag = true,
            isPublicBundle = true,
            mockBundle,
            mockSandbox
        )

        val classTag = classTagFactory.deserialise(serialisedTag) as StaticTag

        assertEquals(1, classTag.version)
        assertTrue(classTag.isPublicClass)
        assertEquals(mockBundle.symbolicName, classTag.classBundleName)
        // We do not check the class tag's CPK file hash, since a placeholder is used for public classes.
    }

    @Test
    fun `can serialise and deserialise an evolvable class tag for a CPK class`() {
        val serialisedTag = classTagFactory.createSerialised(
            isStaticClassTag = false,
            isPublicBundle = false,
            mockBundle,
            mockSandbox
        )

        val classTag = classTagFactory.deserialise(serialisedTag) as EvolvableTag

        assertEquals(1, classTag.version)
        assertFalse(classTag.isPublicClass)
        assertEquals(mockBundle.symbolicName, classTag.classBundleName)
        assertEquals(mockSandbox.cordappBundle.symbolicName, classTag.cordappBundleName)
        assertEquals(mockCpk.id.signerSummaryHash, classTag.cpkSignerSummaryHash)
    }

    @Test
    fun `can serialise and deserialise an evolvable class tag for a public class`() {
        val serialisedTag = classTagFactory.createSerialised(
            isStaticClassTag = false,
            isPublicBundle = true,
            mockBundle,
            mockSandbox
        )

        val classTag = classTagFactory.deserialise(serialisedTag) as EvolvableTag

        assertEquals(1, classTag.version)
        assertTrue(classTag.isPublicClass)
        assertEquals(mockBundle.symbolicName, classTag.classBundleName)
        // We do not check the class tag's CorDapp bundle name or signer summary hash, since placeholders are used for
        // public classes.
    }

    @Test
    fun `throws if asked to create a class tag for a bundle with no symbolic name`() {
        assertThrows<SandboxException> {
            classTagFactory.createSerialised(
                isStaticClassTag = false,
                isPublicBundle = true,
                mock(),
                mockSandbox
            )
        }
    }

    @Test
    fun `throws if asked to deserialise less than two entries`() {
        val insufficientEntriesPattern = Regex(
            "Serialised class tag only contained .* entries, whereas the minimum length is 2. The entries " +
                    "were .*\\."
        )

        val zeroEntries = ""
        val zeroEntriesException = assertThrows<SandboxException> { classTagFactory.deserialise(zeroEntries) }
        assertTrue(insufficientEntriesPattern.matches(zeroEntriesException.message!!))

        val oneEntry = ClassTagV1.STATIC_IDENTIFIER
        val oneEntryException = assertThrows<SandboxException> { classTagFactory.deserialise(oneEntry) }
        assertTrue(insufficientEntriesPattern.matches(oneEntryException.message!!))
    }

    @Test
    fun `throws if asked to deserialise a version that cannot be converted to an integer`() {
        val invalidVersionPattern = Regex(
            "Serialised class tag's version .* could not be converted to an integer."
        )

        val invalidVersion = generateSerialisedTag(ClassTagV1.STATIC_IDENTIFIER, "Z", 2)
        val invalidVersionException = assertThrows<SandboxException> { classTagFactory.deserialise(invalidVersion) }
        assertTrue(invalidVersionPattern.matches(invalidVersionException.message!!))
    }

    @Test
    fun `throws if asked to deserialise an unknown tag type`() {
        val unknownTagExceptionPattern = Regex(
            "Serialised class tag had type .* and version .*\\. This combination is unknown."
        )

        val unknownTag = generateSerialisedTag("Z", "1", 2)
        val unknownTagException = assertThrows<SandboxException> { classTagFactory.deserialise(unknownTag) }
        assertTrue(unknownTagExceptionPattern.matches(unknownTagException.message!!))
    }

    @Test
    fun `throws if asked to deserialise an unknown version`() {
        val unknownTagPattern = Regex(
            "Serialised class tag had type .* and version .*\\. This combination is unknown."
        )

        val unknownVersion = generateSerialisedTag(ClassTagV1.STATIC_IDENTIFIER, "2", 2)
        val unknownTagException = assertThrows<SandboxException> { classTagFactory.deserialise(unknownVersion) }
        assertTrue(unknownTagPattern.matches(unknownTagException.message!!))
    }

    @Test
    fun `throws if asked to deserialise a static tag with not exactly five entries`() {
        val insufficientEntriesPattern = Regex(
            "Serialised static class tag contained .* entries, whereas .* entries were expected. The entries " +
                    "were .*\\."
        )

        val fourEntries = generateSerialisedTag(ClassTagV1.STATIC_IDENTIFIER, "1", length = 4)
        val fourEntriesException = assertThrows<SandboxException> { classTagFactory.deserialise(fourEntries) }
        assertTrue(insufficientEntriesPattern.matches(fourEntriesException.message!!))

        val sixEntries = generateSerialisedTag(ClassTagV1.STATIC_IDENTIFIER, "1", length = 6)
        val sixEntriesException = assertThrows<SandboxException> { classTagFactory.deserialise(sixEntries) }
        assertTrue(insufficientEntriesPattern.matches(sixEntriesException.message!!))
    }

    @Test
    fun `throws if asked to deserialise an evolvable tag with not exactly six entries`() {
        val insufficientEntriesPattern = Regex(
            "Serialised evolvable class tag contained .* entries, whereas .* entries were expected. The " +
                    "entries were .*\\."
        )

        val fiveEntries = generateSerialisedTag(ClassTagV1.EVOLVABLE_IDENTIFIER, "1", length = 5)
        val fiveEntriesException = assertThrows<SandboxException> { classTagFactory.deserialise(fiveEntries) }
        assertTrue(insufficientEntriesPattern.matches(fiveEntriesException.message!!))

        val sevenEntries = generateSerialisedTag(ClassTagV1.EVOLVABLE_IDENTIFIER, "1", length = 7)
        val sevenEntriesException = assertThrows<SandboxException> { classTagFactory.deserialise(sevenEntries) }
        assertTrue(insufficientEntriesPattern.matches(sevenEntriesException.message!!))
    }

    @Test
    fun `throws if asked to deserialise a static tag with an invalid CPK file hash`() {
        val invalidHashPattern = Regex("Couldn't parse hash .* in serialised static class tag\\.")

        val invalidHash = generateSerialisedTag(ClassTagV1.STATIC_IDENTIFIER, "1", 5, "BAD_HASH")
        val invalidHashException = assertThrows<SandboxException> { classTagFactory.deserialise(invalidHash) }
        assertTrue(invalidHashPattern.matches(invalidHashException.message!!))
    }

    @Test
    fun `throws if asked to deserialise an evolvable tag with an invalid public key hash`() {
        val invalidHashPattern = Regex("Couldn't parse hash .* in serialised evolvable class tag\\.")

        val invalidHash = generateSerialisedTag(ClassTagV1.EVOLVABLE_IDENTIFIER, "1", 6, "BAD_HASH")
        val invalidHashException = assertThrows<SandboxException> { classTagFactory.deserialise(invalidHash) }
        assertTrue(invalidHashPattern.matches(invalidHashException.message!!))
    }
}