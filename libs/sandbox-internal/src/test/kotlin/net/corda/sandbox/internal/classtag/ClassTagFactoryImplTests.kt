package net.corda.sandbox.internal.classtag

import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.CLASS_TAG_DELIMITER
import net.corda.sandbox.internal.CLASS_TAG_IDENTIFIER_IDX
import net.corda.sandbox.internal.CLASS_TAG_VERSION_IDX
import net.corda.sandbox.internal.CPK_MAIN_BUNDLE_NAME
import net.corda.sandbox.internal.ClassTagV1
import net.corda.sandbox.internal.ClassTagV1.CLASS_TYPE_IDX
import net.corda.sandbox.internal.ClassTagV1.CPK_SANDBOX_CLASS
import net.corda.sandbox.internal.ClassTagV1.PLACEHOLDER_HASH
import net.corda.sandbox.internal.ClassTagV1.PLACEHOLDER_STRING
import net.corda.sandbox.internal.classtag.v1.EvolvableTagImplV1
import net.corda.sandbox.internal.classtag.v1.StaticTagImplV1
import net.corda.sandbox.internal.mockBundle
import net.corda.sandbox.internal.mockCpkMeta
import net.corda.sandbox.internal.sandbox.CpkSandboxImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import java.util.UUID.randomUUID

const val MOCK_BUNDLE_NAME = "mock_bundle_symbolic_name"

class ClassTagFactoryImplTests {
    private val classTagFactory = ClassTagFactoryImpl()

    private val mockBundle = mockBundle(MOCK_BUNDLE_NAME)
    private val mockCpkMainBundle = mockBundle(CPK_MAIN_BUNDLE_NAME)
    private val mockCpk = mockCpkMeta()
    private val mockSandbox = CpkSandboxImpl(randomUUID(), mockCpk, mockCpkMainBundle, emptySet())

    /**
     * Returns a serialised class tag.
     *
     * @param classTag The class tag identifier to use.
     * @param version The version to use.
     * @param classType The tag type to use.
     * @param length The number of entries.
     * @param hash If non-null, the fourth entry of a static tag (the CPK file hash) or the fifth entry of an evolvable
     *  tag (the CPK signers) is set to [hash].
     */
    private fun generateSerialisedTag(
        classTag: String,
        length: Int,
        version: String = ClassTagV1.VERSION.toString(),
        classType: String = CPK_SANDBOX_CLASS,
        hash: String? = null
    ): String {
        // We allocate excess entries, then trim the list later.
        val entries = MutableList(10) { "0" }
        entries[CLASS_TAG_IDENTIFIER_IDX] = classTag
        entries[CLASS_TAG_VERSION_IDX] = version
        entries[CLASS_TYPE_IDX] = classType

        if (hash != null) {
            val hashIdx = if (classTag == ClassTagV1.STATIC_IDENTIFIER) 4 else 5
            entries[hashIdx] = hash
        }

        return entries.subList(0, length).joinToString(CLASS_TAG_DELIMITER)
    }

    @Test
    fun `can serialise and deserialise a static class tag for a non-bundle class`() {
        val serialisedTag = classTagFactory.createSerialisedTag(true, null, null)
        val classTag = classTagFactory.deserialise(serialisedTag)

        val expectedClassTag =
            StaticTagImplV1(ClassType.NonBundleClass, PLACEHOLDER_STRING, PLACEHOLDER_HASH)
        assertEquals(expectedClassTag, classTag)
    }

    @Test
    fun `can serialise and deserialise a static class tag for a CPK class`() {
        val serialisedTag = classTagFactory.createSerialisedTag(true, mockBundle, mockSandbox)
        val classTag = classTagFactory.deserialise(serialisedTag)

        val expectedClassTag =
            StaticTagImplV1(ClassType.CpkSandboxClass, mockBundle.symbolicName, mockCpk.fileChecksum)
        assertEquals(expectedClassTag, classTag)
    }

    @Test
    fun `can serialise and deserialise a static class tag for a public class`() {
        val serialisedTag = classTagFactory.createSerialisedTag(true, mockBundle, null)
        val classTag = classTagFactory.deserialise(serialisedTag)

        val expectedClassTag = StaticTagImplV1(ClassType.PublicSandboxClass, mockBundle.symbolicName, PLACEHOLDER_HASH)
        assertEquals(expectedClassTag, classTag)
    }

    @Test
    fun `can serialise and deserialise an evolvable class tag for a non-bundle class`() {
        val serialisedTag = classTagFactory.createSerialisedTag(false, null, null)
        val classTag = classTagFactory.deserialise(serialisedTag)

        val expectedClassTag = EvolvableTagImplV1(
            ClassType.NonBundleClass, PLACEHOLDER_STRING, PLACEHOLDER_STRING, PLACEHOLDER_HASH
        )
        assertEquals(expectedClassTag, classTag)
    }

    @Test
    fun `can serialise and deserialise an evolvable class tag for a CPK class`() {
        val serialisedTag = classTagFactory.createSerialisedTag(false, mockBundle, mockSandbox)
        val classTag = classTagFactory.deserialise(serialisedTag)

        val expectedClassTag = EvolvableTagImplV1(
            ClassType.CpkSandboxClass,
            mockBundle.symbolicName,
            mockSandbox.mainBundle.symbolicName,
            mockCpk.id.signerSummaryHash
        )
        assertEquals(expectedClassTag, classTag)
    }

    @Test
    fun `can serialise and deserialise an evolvable class tag for a public class`() {
        val serialisedTag = classTagFactory.createSerialisedTag(false, mockBundle, null)
        val classTag = classTagFactory.deserialise(serialisedTag)

        val expectedClassTag = EvolvableTagImplV1(
            ClassType.PublicSandboxClass,
            mockBundle.symbolicName,
            PLACEHOLDER_STRING,
            PLACEHOLDER_HASH
        )
        assertEquals(expectedClassTag, classTag)
    }

    @Test
    fun `throws if asked to create a class tag for a bundle with no symbolic name`() {
        assertThrows<SandboxException> {
            classTagFactory.createSerialisedTag(false, mock(), null)
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
            "Serialised class tag version .* could not be converted to an integer."
        )

        val invalidVersion = generateSerialisedTag(ClassTagV1.STATIC_IDENTIFIER, 2, "Z")
        val invalidVersionException = assertThrows<SandboxException> { classTagFactory.deserialise(invalidVersion) }
        assertTrue(invalidVersionPattern.matches(invalidVersionException.message!!))
    }

    @Test
    fun `throws if asked to deserialise an unknown tag type`() {
        val unknownTagExceptionPattern = Regex(
            "Serialised class tag had type .* and version .*\\. This combination is unknown."
        )

        val unknownTag = generateSerialisedTag("Z", 2, "1")
        val unknownTagException = assertThrows<SandboxException> { classTagFactory.deserialise(unknownTag) }
        assertTrue(unknownTagExceptionPattern.matches(unknownTagException.message!!))
    }

    @Test
    fun `throws if asked to deserialise an unknown version`() {
        val unknownVersionPattern = Regex(
            "Serialised class tag had type .* and version .*\\. This combination is unknown."
        )

        val unknownVersion = generateSerialisedTag(ClassTagV1.STATIC_IDENTIFIER, 2, "2")
        val unknownVersionException = assertThrows<SandboxException> { classTagFactory.deserialise(unknownVersion) }
        assertTrue(unknownVersionPattern.matches(unknownVersionException.message!!))
    }

    @Test
    fun `throws if asked to deserialise an unknown class type`() {
        val unknownClassTypePattern = Regex(
            "Could not deserialise class type from string .*\\."
        )

        val unknownClassType = generateSerialisedTag(ClassTagV1.STATIC_IDENTIFIER, 5, classType = "Z")
        val unknownClassTypeException = assertThrows<SandboxException> { classTagFactory.deserialise(unknownClassType) }
        assertTrue(unknownClassTypePattern.matches(unknownClassTypeException.message!!))
    }

    @Test
    fun `throws if asked to deserialise a static tag with not exactly five entries`() {
        val insufficientEntriesPattern = Regex(
            "Serialised static class tag contained .* entries, whereas .* entries were expected. The entries " +
                    "were .*\\."
        )

        val fourEntries = generateSerialisedTag(ClassTagV1.STATIC_IDENTIFIER, 4)
        val fourEntriesException = assertThrows<SandboxException> { classTagFactory.deserialise(fourEntries) }
        assertTrue(insufficientEntriesPattern.matches(fourEntriesException.message!!))

        val sixEntries = generateSerialisedTag(ClassTagV1.STATIC_IDENTIFIER, 6)
        val sixEntriesException = assertThrows<SandboxException> { classTagFactory.deserialise(sixEntries) }
        assertTrue(insufficientEntriesPattern.matches(sixEntriesException.message!!))
    }

    @Test
    fun `throws if asked to deserialise an evolvable tag with not exactly six entries`() {
        val insufficientEntriesPattern = Regex(
            "Serialised evolvable class tag contained .* entries, whereas .* entries were expected. The " +
                    "entries were .*\\."
        )

        val fiveEntries = generateSerialisedTag(ClassTagV1.EVOLVABLE_IDENTIFIER, 5)
        val fiveEntriesException = assertThrows<SandboxException> { classTagFactory.deserialise(fiveEntries) }
        assertTrue(insufficientEntriesPattern.matches(fiveEntriesException.message!!))

        val sevenEntries = generateSerialisedTag(ClassTagV1.EVOLVABLE_IDENTIFIER, 7)
        val sevenEntriesException = assertThrows<SandboxException> { classTagFactory.deserialise(sevenEntries) }
        assertTrue(insufficientEntriesPattern.matches(sevenEntriesException.message!!))
    }

    @Test
    fun `throws if asked to deserialise a static tag with an invalid CPK file hash`() {
        val invalidHashPattern = Regex("Couldn't parse hash .* in serialised static class tag\\.")

        val invalidHash = generateSerialisedTag(ClassTagV1.STATIC_IDENTIFIER, 5, hash = "BAD_HASH")
        val invalidHashException = assertThrows<SandboxException> { classTagFactory.deserialise(invalidHash) }
        assertTrue(invalidHashPattern.matches(invalidHashException.message!!))
    }

    @Test
    fun `throws if asked to deserialise an evolvable tag with an invalid public key hash`() {
        val invalidHashPattern = Regex("Couldn't parse hash .* in serialised evolvable class tag\\.")

        val invalidHash = generateSerialisedTag(ClassTagV1.EVOLVABLE_IDENTIFIER, 6, hash = "BAD_HASH")
        val invalidHashException = assertThrows<SandboxException> { classTagFactory.deserialise(invalidHash) }
        assertTrue(invalidHashPattern.matches(invalidHashException.message!!))
    }
}