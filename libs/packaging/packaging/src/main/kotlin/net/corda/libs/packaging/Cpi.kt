package net.corda.libs.packaging

import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.internal.CpiLoader
import net.corda.libs.packaging.internal.jarSignatureVerificationEnabledByDefault
import java.io.InputStream
import java.nio.file.Path

interface Cpi : AutoCloseable {

    companion object {

        /**
         * Known file extensions for a CPI file; the only difference between CPI and CPB files is that CPB files
         * have no groupPolicy
         */
        val fileExtensions = listOf(".cpb", ".cpi")

        /**
         * Parses a CPI file and stores its information in a [Cpi] instance
         * @param inputStream a stream with the CPI file content
         * @param expansionLocation a filesystem directory where the CPK files
         * contained in the provided CPI will be extracted
         * @param cpiLocation an optional string containing information about the cpi location that will be used
         * in exception messages to ease troubleshooting
         * @param verifySignature whether to verify CPI and CPK files signatures
         * @return a new [Cpi] instance containing information about the provided CPI
         */
        @JvmStatic
        @JvmOverloads
        fun from(inputStream : InputStream,
                 expansionLocation : Path,
                 cpiLocation : String? = null,
                 verifySignature : Boolean = jarSignatureVerificationEnabledByDefault()) : Cpi =
            CpiLoader.loadCpi(inputStream, expansionLocation, cpiLocation, verifySignature)

    }

    val metadata : CpiMetadata
    val cpks : Collection<Cpk>

    fun getCpkById(id : CpkIdentifier) : Cpk?
}
