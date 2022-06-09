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
    @MethodSource("invalidCpkVersionsToTest")
    fun testInvalidCpkVersion(testCase: String) {
        val manifest = testCase.toManifest()

        assertThrows(PackagingException::class.java) {
            FormatVersionReader.readCpkFormatVersion(manifest)
        }
    }

    @ParameterizedTest
    @MethodSource("validCpkVersionsToTest")
    fun testValidCpkVersion(testCaseAndExpectedResult: Pair<String, CpkFormatVersion>) {
        val (testCase, expected) = testCaseAndExpectedResult

        val formatVersion = FormatVersionReader.readCpkFormatVersion(testCase.toManifest())

        assertEquals(expected, formatVersion)
    }

    @ParameterizedTest
    @MethodSource("invalidCpbVersionsToTest")
    fun testInvalidCpbVersion(testCase: String) {
        val manifest = testCase.toManifest()

        assertThrows(PackagingException::class.java) {
            FormatVersionReader.readCpbFormatVersion(manifest)
        }
    }

    @ParameterizedTest
    @MethodSource("validCpbVersionsToTest")
    fun testValidCpbVersion(testCaseAndExpectedResult: Pair<String, CpkFormatVersion>) {
        val (testCase, expected) = testCaseAndExpectedResult

        val formatVersion = FormatVersionReader.readCpbFormatVersion(testCase.toManifest())

        assertEquals(expected, formatVersion)
    }

    @ParameterizedTest
    @MethodSource("invalidCpiVersionsToTest")
    fun testInvalidCpiVersion(testCase: String) {
        val manifest = testCase.toManifest()

        assertThrows(PackagingException::class.java) {
            FormatVersionReader.readCpiFormatVersion(manifest)
        }
    }

    @ParameterizedTest
    @MethodSource("validCpiVersionsToTest")
    fun testValidCpiVersion(testCaseAndExpectedResult: Pair<String, CpkFormatVersion>) {
        val (testCase, expected) = testCaseAndExpectedResult

        val formatVersion = FormatVersionReader.readCpiFormatVersion(testCase.toManifest())

        assertEquals(expected, formatVersion)
    }

    companion object {
        @JvmStatic
        fun invalidCpkVersionsToTest() = listOf(
            "Corda-CPK-Format: ",      // Missing version
            "Corda-CPK-Format: 1",     // Missing minor version
            "Corda-CPK-Format: .1",    // Missing major version
            "Corda-CPK-Format: 1.1.1", // Three version number components
        )

        @JvmStatic
        fun validCpkVersionsToTest() = listOf(
            "" to CpkFormatVersion(1, 0), // Missing attribute maps to 1.0
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
        @JvmStatic
        fun invalidCpbVersionsToTest() = listOf(
            "Corda-CPB-Format: ",      // Missing version
            "Corda-CPB-Format: 1",     // Missing minor version
            "Corda-CPB-Format: .1",    // Missing major version
            "Corda-CPB-Format: 1.1.1", // Three version number components
        )

        @JvmStatic
        fun validCpbVersionsToTest() = listOf(
            "" to CpkFormatVersion(1, 0), // Missing attribute maps to 1.0
            "Corda-CPB-Format: 0.0"  to CpkFormatVersion(0, 0),
            "Corda-CPB-Format: 0.1"  to CpkFormatVersion(0, 1),
            "Corda-CPB-Format: 0.01" to CpkFormatVersion(0, 1),
            "Corda-CPB-Format: 1.0"  to CpkFormatVersion(1, 0),
            "Corda-CPB-Format: 1.1"  to CpkFormatVersion(1, 1),
            "Corda-CPB-Format: 1.10" to CpkFormatVersion(1, 10),
            "Corda-CPB-Format: 2.0"  to CpkFormatVersion(2, 0),
            "Corda-CPB-Format: 2.1"  to CpkFormatVersion(2, 1),
            "Corda-CPB-Format: 2.10" to CpkFormatVersion(2, 10),
            "Corda-CPB-Format: 1000.2000" to CpkFormatVersion(1000, 2000),
            "Corda-CPB-Format: ${Int.MAX_VALUE}.${Int.MAX_VALUE}" to CpkFormatVersion(Int.MAX_VALUE, Int.MAX_VALUE),
        )
        @JvmStatic
        fun invalidCpiVersionsToTest() = listOf(
            "Corda-CPI-Format: ",      // Missing version
            "Corda-CPI-Format: 1",     // Missing minor version
            "Corda-CPI-Format: .1",    // Missing major version
            "Corda-CPI-Format: 1.1.1", // Three version number components
        )

        @JvmStatic
        fun validCpiVersionsToTest() = listOf(
            "" to CpkFormatVersion(1, 0), // Missing attribute maps to 1.0
            "Corda-CPI-Format: 0.0"  to CpkFormatVersion(0, 0),
            "Corda-CPI-Format: 0.1"  to CpkFormatVersion(0, 1),
            "Corda-CPI-Format: 0.01" to CpkFormatVersion(0, 1),
            "Corda-CPI-Format: 1.0"  to CpkFormatVersion(1, 0),
            "Corda-CPI-Format: 1.1"  to CpkFormatVersion(1, 1),
            "Corda-CPI-Format: 1.10" to CpkFormatVersion(1, 10),
            "Corda-CPI-Format: 2.0"  to CpkFormatVersion(2, 0),
            "Corda-CPI-Format: 2.1"  to CpkFormatVersion(2, 1),
            "Corda-CPI-Format: 2.10" to CpkFormatVersion(2, 10),
            "Corda-CPI-Format: 1000.2000" to CpkFormatVersion(1000, 2000),
            "Corda-CPI-Format: ${Int.MAX_VALUE}.${Int.MAX_VALUE}" to CpkFormatVersion(Int.MAX_VALUE, Int.MAX_VALUE),
        )
    }
}
