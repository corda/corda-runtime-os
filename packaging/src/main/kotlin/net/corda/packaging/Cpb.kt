package net.corda.packaging

import net.corda.packaging.internal.CpbBuilder
import net.corda.packaging.internal.CpbLoader
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path

/** Contains information on a CPB. */
sealed class Cpb(val identifier: Identifier, val metadata: MetaData) {

    companion object {
        const val fileExtension = ".cpb"
        const val DEFAULT_VERIFY_JAR_SIGNATURES_KEY = "net.corda.packaging.jarSignatureVerification"

        /**
         * Method used to compute the default value for the 'verifySignature' parameter in
         * [Cpk.Expanded.from], [Cpk.Archived.from], [Cpb.Expanded.from], [Cpb.Archived.from]
         */
        internal fun jarSignatureVerificationEnabledByDefault() = System.getProperty(DEFAULT_VERIFY_JAR_SIGNATURES_KEY)?.toBoolean() ?: true


        /**
         * Creates a new a [Cpb] archive containing the CPKs specified in [cpkFiles] and dumps it to [outputStream],
         * this method will also attempt to resolve the all of the included Cpk dependencies and raises
         * [DependencyResolutionException] if any dependency is not met.
         * If [useSignatures] is false, the CPK signatures won't be verified and won't not be taken in consideration
         * in the dependency resolution (an unsigned CPK or a CPK signed by a different key will be considered
         * an equally good candidate to meet a dependency).
         * A [metadataReader] can also be specified to include an HOCON configuration file with metadata
         * in the generated archive.
         */
        @JvmStatic
        @JvmOverloads
        @Suppress("LongParameterList")
        fun assemble(destination : OutputStream,
                     cpkFiles: Iterable<Path>,
                     cpkArchives: Iterable<Path> = emptyList(),
                     metadataReader: Reader? = null,
                     signingParams: SigningParameters? = null,
                     useSignatures: Boolean = jarSignatureVerificationEnabledByDefault()) =
                CpbBuilder(cpkFiles, cpkArchives, metadataReader, signingParams, useSignatures).build(destination)
    }

    class Expanded(identifier: Identifier, metadata: MetaData, val cpks : Iterable<Cpk.Expanded>) : Cpb(identifier, metadata) {
        companion object {

            private fun temporaryDir() = Files.createTempDirectory("cpb").also { tmpDir ->
                Runtime.getRuntime().addShutdownHook (Thread {
                    if(Files.exists(tmpDir)) Files.walk(tmpDir).sorted(Comparator.reverseOrder()).forEach(Files::delete)
                })
            }

            /**
             * Create an [Cpb.Expanded] instance reading the file the from [inputStream], the CPK will also be extracted in
             * [expansionLocation] if that is non-null, otherwise a temporary directory wil be created for purpose of extracting
             * the [Cpb] content
             */
            @JvmStatic
            @JvmOverloads
            fun from(inputStream : InputStream,
                     expansionLocation : Path?,
                     cpbLocation : String? = null,
                     verifySignature : Boolean = jarSignatureVerificationEnabledByDefault()) =
                    CpbLoader.from(inputStream, expansionLocation ?: temporaryDir(), cpbLocation, verifySignature) as Expanded
        }
    }

    class Archived(identifier: Identifier, metadata: MetaData, val cpks : Iterable<Cpk>) : Cpb(identifier, metadata) {
        companion object {

            /**
             * Create an [Cpb.Archived] instance reading the file the from [inputStream], neither the [Cpb] archive, nor its contained CPKs
             * will be extracted, but their content will be scanned to extract their metadata.
             */
            @JvmStatic
            @JvmOverloads
            fun from(inputStream : InputStream,
                     verifySignature : Boolean = false,
                     cpbLocation : String? = null) = CpbLoader.from(inputStream, null, cpbLocation, verifySignature) as Archived
        }
    }

    data class MetaData(val metadata: Map<String, String>)

    /**
     * An opaque object used to uniquely identify a [Cpb] archive, currently equal to its hash.
     */
    data class Identifier(val hash : SecureHash)
}