package net.corda.packaging

import net.corda.packaging.internal.CPIBuilder
import net.corda.packaging.internal.CPIIdentifierImpl
import net.corda.packaging.internal.CPILoader
import net.corda.packaging.internal.CPIMetadataImpl
import net.corda.packaging.internal.jarSignatureVerificationEnabledByDefault
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.nio.file.Path

interface CPI : AutoCloseable {

    companion object {

        /**
         * Known file extensions for a CPI file; the only difference between CPI and CPB files is that CPB files
         * have no [CPI.Metadata.groupPolicy]
         */
        val fileExtensions = listOf(".cpb", ".cpi")

        /**
         * Parses a CPI file and stores its information in a [CPI] instance
         * @param inputStream a stream with the CPI file content
         * @param expansionLocation a filesystem directory where the CPK files
         * contained in the provided CPI will be extracted
         * @param cpiLocation an optional string containing information about the cpi location that will be used
         * in exception messages to ease troubleshooting
         * @param verifySignature whether to verify CPI and CPK files signatures
         * @return a new [CPI] instance containing information about the provided CPI
         */
        @JvmStatic
        @JvmOverloads
        fun from(inputStream : InputStream,
                 expansionLocation : Path,
                 cpiLocation : String? = null,
                 verifySignature : Boolean = jarSignatureVerificationEnabledByDefault()) : CPI =
            CPILoader.loadCPI(inputStream, expansionLocation, cpiLocation, verifySignature)

        /**
         * Creates a new a [CPI] archive containing the CPKs specified in [cpkFiles] and dumps it to [outputStream]
         * (which is guaranteed to be closed when the method returns), this method will also attempt to resolve all
         * the included CPK dependencies and raises [DependencyResolutionException] if any dependency is not met.
         * If [useSignatures] is false, the CPK signatures won't be verified and won't be taken in consideration
         * in the dependency resolution (an unsigned CPK or a CPK signed by a different key will be considered
         * an equally good candidate to meet a dependency).
         * A [metadataReader] can also be specified to include an HOCON configuration file with metadata
         * in the generated archive.
         *
         * @param name the CPI name
         * @param version CPI version
         * @param destination the [OutputStream] where the CPI wil be written
         * @param cpkFiles a collection of [Path]s to CPK files
         * @param cpkArchives a collection of directories that will be treated as a CPK file repositories for
         * the purpose of dependency resolution (missing CPK file dependencies will be looked up there)
         * @param metadataReader an optional [Reader] providing the membership metadata file
         * @param signingParams
         * @param useSignatures whether to verify and use jar signatures on CPK file (it can affect dependency
         * resolution as [CPK.Identifier.signerSummaryHash] won't be parsed if [useSignatures] is false)
         */
        @JvmStatic
        @JvmOverloads
        @Suppress("LongParameterList")
        fun assemble(destination : OutputStream,
                     name : String,
                     version : String,
                     cpkFiles: Iterable<Path>,
                     cpkArchives: Iterable<Path> = emptyList(),
                     metadataReader: Reader? = null,
                     signingParams: SigningParameters? = null,
                     useSignatures: Boolean = jarSignatureVerificationEnabledByDefault(),
                     groupPolicy: String? = null
        ) = CPIBuilder(name, version, cpkFiles, cpkArchives, metadataReader, signingParams, useSignatures, groupPolicy).build(destination)
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
                CPIIdentifierImpl(name, version, signerSummaryHash)

        }
    }

    interface Metadata {
        val id : Identifier
        val hash : SecureHash
        val cpks : Collection<CPK.Metadata>
        val groupPolicy : String?

        /**
         * @return a [CPK.Metadata] instance with containing the information about a single [CPK]
         * @throws [NoSuchElementException] if a [CPK] with the given [CPK.Identifier] doesn't exist in the [CPI]
         */
        fun cpkById(id : CPK.Identifier) : CPK.Metadata

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
                CPILoader.loadMetadata(inputStream, cpiLocation, verifySignature)

            @JvmStatic
            fun newInstance(id : Identifier, hash: SecureHash, cpks: Iterable<CPK.Metadata>, groupPolicy : String?) : Metadata =
                CPIMetadataImpl(id, hash, cpks, groupPolicy)
        }
    }

    val metadata : Metadata
    val cpks : Collection<CPK>

    fun getCPKById(id : CPK.Identifier) : CPK?
}
