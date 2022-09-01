package net.corda.libs.packaging.internal

import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.exception.PackagingException
import java.util.jar.Manifest

class FormatVersionReader {
    companion object {
        private const val CPK_FORMAT = "Corda-CPK-Format"
        private const val CPB_FORMAT = "Corda-CPB-Format"
        private const val CPI_FORMAT = "Corda-CPI-Format"

        // A regex matching CPK format strings.
        private val VERSION_PATTERN = "(\\d++)\\.(\\d++)".toRegex()

        private val version1 = CpkFormatVersion(1, 0)

        fun readCpkFormatVersion(manifest: Manifest): CpkFormatVersion {
            val formatAttribute = manifest.mainAttributes.getValue(CPK_FORMAT)
                ?: return version1 // Version 1 files sometimes lacked the FormatVersion
            return parse(formatAttribute)
        }

        fun readCpbFormatVersion(manifest: Manifest): CpkFormatVersion {
            val formatAttribute = manifest.mainAttributes.getValue(CPB_FORMAT)
                ?: return version1 // Version 1 files sometimes lacked the FormatVersion
            return parse(formatAttribute)
        }

        fun readCpiFormatVersion(manifest: Manifest): CpkFormatVersion {
            val formatAttribute = manifest.mainAttributes.getValue(CPI_FORMAT)
                ?: return version1 // Version 1 files sometimes lacked the FormatVersion
            return parse(formatAttribute)
        }

        /**
         * Parses the [formatAttribute] into a [CpkFormatVersion].
         *
         * Throws [PackagingException] if the CPK format is missing or incorrectly specified.
         */
        private fun parse(formatAttribute: String): CpkFormatVersion {
            val matches = VERSION_PATTERN.matchEntire(formatAttribute)
                ?: throw PackagingException("Does not match 'majorVersion.minorVersion': '$formatAttribute'")
            return CpkFormatVersion(matches.groupValues[1].toInt(), matches.groupValues[2].toInt())
        }
    }
}