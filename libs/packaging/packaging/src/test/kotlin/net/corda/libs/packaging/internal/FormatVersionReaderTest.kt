package net.corda.libs.packaging.internal

import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.exception.PackagingException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.jar.Manifest

class FormatVersionReaderTest {

    private fun String.toManifest() = "$this\n".byteInputStream().use { Manifest(it) }

    @ParameterizedTest
    @MethodSource("invalidVersionsToTest")
    fun testInvalidVersion(testCase: String) {
        val manifest = testCase.toManifest()

        assertThrows(PackagingException::class.java) {
            FormatVersionReader.readFormatVersion(manifest)
        }
    }

    @ParameterizedTest
    @MethodSource("validVersionsToTest")
    fun testValidVersion(testCaseAndExpectedResult: Pair<String, CpkFormatVersion>) {
        val (testCase, expected) = testCaseAndExpectedResult

        val formatVersion = FormatVersionReader.readFormatVersion(testCase.toManifest())

        assertEquals(expected, formatVersion)
    }

    companion object {
        @JvmStatic
        fun invalidVersionsToTest(): List<String> {
            return listOf(
                "Corda-CPK-Format: ",      // Missing version
                "",                        // Missing attribute
                "Corda-CPK-Format: 1",     // Missing minor version
                "Corda-CPK-Format: .1",    // Missing major version
                "Corda-CPK-Format: 1.1.1", // Three version number components
            )
        }
        @JvmStatic
        fun validVersionsToTest(): List<Pair<String, CpkFormatVersion>> {
            return listOf(
                "Corda-CPK-Format: 0.0"  to CpkFormatVersion(0, 0),
                "Corda-CPK-Format: 0.1"  to CpkFormatVersion(0, 1),
                "Corda-CPK-Format: 0.01" to CpkFormatVersion(0, 1),
                "Corda-CPK-Format: 1.0"  to CpkFormatVersion(1, 0),
                "Corda-CPK-Format: 1.1"  to CpkFormatVersion(1, 1),
                "Corda-CPK-Format: 1.10" to CpkFormatVersion(1, 10),
                "Corda-CPK-Format: 2.0"  to CpkFormatVersion(2, 0),
                "Corda-CPK-Format: 2.1"  to CpkFormatVersion(2, 1),
                "Corda-CPK-Format: 2.10" to CpkFormatVersion(2, 10),
                "Corda-CPK-Format: 1000.2000" to CpkFormatVersion(1000, 2000),
                "Corda-CPK-Format: ${Int.MAX_VALUE}.${Int.MAX_VALUE}" to CpkFormatVersion(Int.MAX_VALUE, Int.MAX_VALUE),
            )
        }
    }
}
