package net.corda.sdk.packaging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.zip.ZipInputStream
import kotlin.math.min

internal object TestUtils {
    fun getManifestMainAttributesAndEntries(cpxFile: Path) =
        JarInputStream(Files.newInputStream(cpxFile, StandardOpenOption.READ)).use {
            val manifest = it.manifest
            manifest.mainAttributes to manifest.entries
        }

    private fun getJarEntriesByEntryNamePredicate(cpbFile: Path, predicate: (String) -> Boolean): Set<JarEntry> {
        val hashedJarEntries = mutableSetOf<JarEntry>()
        JarInputStream(Files.newInputStream(cpbFile, StandardOpenOption.READ)).use {
            var jarEntry = it.nextJarEntry
            while (jarEntry != null) {
                val jarEntryName = jarEntry.name
                if (predicate(jarEntryName)) {
                    hashedJarEntries.add(jarEntry)
                }
                jarEntry = it.nextJarEntry
            }
        }
        return hashedJarEntries
    }

    private fun isSignatureFile(entryName: String): Boolean =
        entryName.endsWith(".SF")

    private fun isSignatureBlockFile(entryName: String): Boolean =
        entryName.endsWith(".RSA") ||
            entryName.endsWith(".DSA") ||
            entryName.endsWith(".EC")

    fun getNonSignatureJarEntries(cpbFile: Path): Set<JarEntry> =
        getJarEntriesByEntryNamePredicate(cpbFile) { entryName ->
            !isSignatureBlockFile(entryName) && !isSignatureFile(entryName)
        }

    fun getSignatureJarEntries(cpbFile: Path): Set<JarEntry> =
        getJarEntriesByEntryNamePredicate(cpbFile) { entryName ->
            entryName.uppercase() != "META-INF/MANIFEST.MF" && isSignatureFile(entryName)
        }

    fun getSignatureBlockJarEntries(cpbFile: Path): Set<JarEntry> =
        getJarEntriesByEntryNamePredicate(cpbFile) { entryName ->
            entryName.uppercase() != "META-INF/MANIFEST.MF" && isSignatureBlockFile(entryName)
        }

    // After Cpx entries are found in Cpxs, checks if their contents are equal.
    fun jarEntriesContentIsEqualInCpxs(
        firstEntryInCpx: Pair<String, Path>,
        secondEntryInCpx: Pair<String, Path>
    ): Boolean {
        val firstCpx = firstEntryInCpx.second
        val secondCpx = secondEntryInCpx.second
        val stream0 = JarInputStream(Files.newInputStream(firstCpx, StandardOpenOption.READ))
        val stream1 = JarInputStream(Files.newInputStream(secondCpx, StandardOpenOption.READ))

        val expectedJarEntryName0 = firstEntryInCpx.first
        val expectedJarEntryName1 = secondEntryInCpx.first

        var actualJarEntry0: JarEntry?
        var actualJarEntry1: JarEntry?

        stream0.use {
            actualJarEntry0 = findJarEntryInCpx(expectedJarEntryName0, stream0)
            require(actualJarEntry0 != null)

            stream1.use {
                actualJarEntry1 = findJarEntryInCpx(expectedJarEntryName1, stream1)
                require(actualJarEntry1 != null)

                return jarInputStreamsAreEqual(stream0, stream1)
            }
        }
    }

    private fun findJarEntryInCpx(expectedJarEntryName: String, inStream: JarInputStream): JarEntry? {
        var actualJarEntry: JarEntry? = null
        var nextEntry = inStream.nextJarEntry
        while (nextEntry != null) {
            if (expectedJarEntryName == nextEntry.name) {
                actualJarEntry = nextEntry
                break
            }
            nextEntry = inStream.nextJarEntry
        }
        return actualJarEntry
    }

    private fun jarInputStreamsAreEqual(inStream0: JarInputStream, inStream1: JarInputStream): Boolean {
        // check entry contents are equal
        var read0: Int
        var read1: Int
        while (true) {
            val buf0 = ByteArray(1024)
            val buf1 = ByteArray(1024)
            read0 = inStream0.read(buf0)
            read1 = inStream1.read(buf1)

            if (read0 == -1 || read1 == -1) return read0 == read1

            var i = 0
            val minRead = min(read0, read1)
            while (i < minRead) {
                if (buf0[i] != buf1[i]) return false
                i++
            }
        }
    }

    fun jarEntriesExistInCpx(cpxPath: Path, expectedEntries: List<String>): Boolean {
        ZipInputStream(Files.newInputStream(cpxPath)).use { zipStream ->
            val actualEntries = generateSequence {
                zipStream.nextEntry?.name
            }.toMutableList()

            return expectedEntries.all {
                actualEntries.contains(it)
            }
        }
    }

    fun assertContainsAllManifestAttributes(
        cpxFile: Path,
        expectedManifestAttributes: Map<Attributes.Name, String>
    ) {
        JarInputStream(Files.newInputStream(cpxFile)).use { stream ->
            val actualManifestAttributes = stream.manifest.mainAttributes
            expectedManifestAttributes.forEach {
                val attributeName = it.key
                val attributeValue = it.value
                val actualAttributeValue = actualManifestAttributes[attributeName]
                assertNotNull(actualAttributeValue) {
                    "Missing attribute $attributeName"
                }
                assertEquals(attributeValue, actualAttributeValue) {
                    "Value mismatch for attribute $attributeName"
                }
            }
        }
    }
}
