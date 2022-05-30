package net.corda.libs.packaging.internal

import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.exception.PackagingException
import java.util.jar.Manifest

internal class FormatVersionReader {
    companion object {
        private const val CPK_FORMAT = "Corda-CPK-Format"

        // A regex matching CPK format strings.
        private val CPK_VERSION_PATTERN = "(\\d++)\\.(\\d++)".toRegex()

        fun readFormatVersion(manifest: Manifest): CpkFormatVersion {
            val formatAttribute = manifest.mainAttributes.getValue(CPK_FORMAT)
                ?: throw PackagingException("CPK manifest does not specify a `${CPK_FORMAT}` attribute.")
            return parse(formatAttribute)
        }

        /**
         * Parses the [formatAttribute] into a [CpkFormatVersion].
         *
         * Throws [PackagingException] if the CPK format is missing or incorrectly specified.
         */
        private fun parse(formatAttribute: String): CpkFormatVersion {
            val matches = CPK_VERSION_PATTERN.matchEntire(formatAttribute)
                ?: throw PackagingException("Does not match 'majorVersion.minorVersion': '$formatAttribute'")
            return CpkFormatVersion(matches.groupValues[1].toInt(), matches.groupValues[2].toInt())
        }
    }
}