package net.corda.libs.packaging

import net.corda.libs.packaging.internal.CpiIdentifierImpl
import net.corda.libs.packaging.internal.CpiLoader
import net.corda.libs.packaging.internal.CpiMetadataImpl
import net.corda.libs.packaging.internal.jarSignatureVerificationEnabledByDefault
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.nio.file.Path

interface Cpi : AutoCloseable {

    companion object {

        /**
         * Known file extensions for a CPI file; the only difference between CPI and CPB files is that CPB files
         * have no [Cpi.Metadata.groupPolicy]
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

    /**
     * A set of data that uniquely identifies a CPI file
     */
    interface Identifier : Comparable<Identifier> {
        /**
         * The name of the CPI, read from its manifest
         */
        val name : String

        /**
         * The version number of the CPI, read from its manifest
         */
        val version : String

        /**
         * The hash of the concatenation of hashes of the public keys of the signers
         * of the CPI, null if the CPI is unsigned
         */
        val signerSummaryHash : SecureHash?

        companion object {
            @JvmStatic
            @JvmOverloads
            fun newInstance(name : String, version : String, signerSummaryHash : SecureHash? = null): Identifier =
                CpiIdentifierImpl(name, version, signerSummaryHash)

        }
    }

    interface Metadata {
        val id : Identifier
        val hash : SecureHash
        val cpks : Collection<Cpk.Metadata>
        val groupPolicy : String?

        /**
         * @return a [Cpk.Metadata] instance with containing the information about a single [Cpk]
         * @throws [NoSuchElementException] if a [Cpk] with the given [Cpk.Identifier] doesn't exist in the [Cpi]
         */
        fun cpkById(id : Cpk.Identifier) : Cpk.Metadata

        companion object {
            /**
             * Parses a CPI file and stores its information in a [Metadata] instance
             * @param inputStream a stream with the CPI file content
             * @param cpiLocation an optional string containing information about the cpi location that will be used
             * in exception messages to ease troubleshooting
             * @param verifySignature whether to verify CPI and CPK files signatures
             * @return a new [Metadata] instance containing information about the provided CPI
             */
            @JvmStatic
            @JvmOverloads
            fun from(inputStream : InputStream,
                     cpiLocation : String? = null,
                     verifySignature : Boolean = jarSignatureVerificationEnabledByDefault()) : Metadata =
                CpiLoader.loadMetadata(inputStream, cpiLocation, verifySignature)

            @JvmStatic
            fun newInstance(id : Identifier, hash: SecureHash, cpks: Iterable<Cpk.Metadata>, groupPolicy : String?) : Metadata =
                CpiMetadataImpl(id, hash, cpks, groupPolicy)
        }
    }

    val metadata : Metadata
    val cpks : Collection<Cpk>

    fun getCpkById(id : Cpk.Identifier) : Cpk?
}
