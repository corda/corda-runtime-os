package net.corda.packaging

import net.corda.packaging.internal.CpkFormatVersionImpl
import net.corda.packaging.internal.CpkIdentifierImpl
import net.corda.packaging.internal.CpkLoader
import net.corda.packaging.internal.CpkManifestImpl
import net.corda.packaging.internal.CpkMetadataImpl
import net.corda.packaging.internal.jarSignatureVerificationEnabledByDefault
import net.corda.v5.crypto.SecureHash
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.security.cert.Certificate
import java.util.NavigableSet

/** Represents a [Cpk] file in the filesystem */
interface Cpk : AutoCloseable {

    companion object {
        const val fileExtension = ".cpk"

        @JvmStatic
        @JvmOverloads
        fun from(inputStream : InputStream,
                 cacheDir : Path,
                 cpkLocation : String? = null,
                 verifySignature : Boolean = jarSignatureVerificationEnabledByDefault(),
                 cpkFileName: String? = null
        ) : Cpk =
            CpkLoader.loadCPK(inputStream, cacheDir, cpkLocation, verifySignature, cpkFileName)
    }

    /** Uniquely identifies a CPK archive */
    interface Identifier : Comparable<Identifier> {
        /**
         * The Bundle-SymbolicName of the main bundle inside the [Cpk]
         */
        val name : String

        /**
         * The Bundle-Version of the main bundle inside the [Cpk]
         */
        val version : String

        /**
         * The hash of concatenation of the sorted hashes of the public keys of the signers of the [Cpk],
         * null if the CPK isn't signed
         */
        val signerSummaryHash : SecureHash?

        companion object {
            @JvmStatic
            fun newInstance(name : String, version : String, signerSummaryHash : SecureHash?) : Identifier =
                CpkIdentifierImpl(name, version, signerSummaryHash)
        }
    }

    /**
     * [Type] is used to distinguish between different types of [Cpk],
     * its main purpose is currently to distinguish between [Cpk]s that are part
     * of the Corda runtime itself (and don't need to be resolved during dependency resolution)
     * and those that are not
     */
    enum class Type constructor(private val text : String?) : Comparable<Type> {
        CORDA_API("corda-api"), UNKNOWN(null);

        companion object{
            private val map = values().associateBy(Cpk.Type::text)

            /**
             * Parses [Type] from a [String], if the [String] cannot be parsed this function returns [Type.UNKNOWN]
             * @param text the [String] to be parsed
             * @return a [Type] instance
             */
            @JvmStatic
            fun parse(text : String) = map[text.lowercase()] ?: UNKNOWN
        }
    }

    interface FormatVersion : Comparable<FormatVersion> {
        val major: Int
        val minor: Int

        companion object {
            // A regex matching CPK format strings.
            private val CPK_VERSION_PATTERN = "(\\d++)\\.(\\d++)".toRegex()

            /**
             * Parses the [cpkFormatVersion] into a [FormatVersion].
             *
             * Throws [PackagingException] if the CPK format is missing or incorrectly specified.
             */
            @JvmStatic
            fun parse(cpkFormatVersion: String): FormatVersion {
                val matches = CPK_VERSION_PATTERN.matchEntire(cpkFormatVersion)
                    ?: throw PackagingException("Does not match 'majorVersion.minorVersion': '$cpkFormatVersion'")
                return CpkFormatVersionImpl(matches.groupValues[1].toInt(), matches.groupValues[2].toInt())
            }

            @JvmStatic
            fun newInstance(major : Int, minor : Int): FormatVersion = CpkFormatVersionImpl(major, minor)
        }
    }

    interface Manifest {
        val cpkFormatVersion: FormatVersion

        companion object {
            const val CPK_FORMAT = "Corda-CPK-Format"

            /**
             * Parses the [manifest] to extract CPK-specific information.
             *
             * Throws [PackagingException] if the CPK format is missing or incorrectly specified.
             */
            @JvmStatic
            fun fromJarManifest(manifest: java.util.jar.Manifest) : Manifest =
                CpkManifestImpl(cpkFormatVersion = parseFormatVersion(manifest))

            /**
             * Parses the [manifest] to extract the CPK format.
             *
             * Throws [PackagingException] if the CPK format is missing or incorrectly specified.
             */
            @JvmStatic
            @Suppress("ThrowsCount")
            fun parseFormatVersion(manifest: java.util.jar.Manifest): FormatVersion =
                CpkManifestImpl.parseFormatVersion(manifest)

            @JvmStatic
            fun newInstance(cpkFormatVersion: FormatVersion) : Manifest = CpkManifestImpl(cpkFormatVersion)
        }
    }

    /**
     * Contains the data associated to a [Cpk] file
     */
    interface Metadata {
        /**
         * The [Identifier] of the [Cpk] archive
         */
        val id : Identifier

        /**
         * The file name of the main bundle inside the [Cpk]
         */
        val manifest : Manifest
        val mainBundle : String
        val libraries : List<String>
        val dependencies : NavigableSet<Identifier>
        val cordappManifest : CordappManifest
        val type : Type
        val hash: SecureHash
        val cordappCertificates : Set<Certificate>

        companion object {
            @JvmStatic
            @JvmOverloads
            fun from(inputStream : InputStream,
                     cpkLocation : String? = null,
                     verifySignature : Boolean = jarSignatureVerificationEnabledByDefault()) : Metadata =
                CpkLoader.loadMetadata(inputStream, cpkLocation, verifySignature)

            @JvmStatic
            @Suppress("LongParameterList")
            fun newInstance(manifest: Manifest,
                            mainBundle : String,
                            libraries : List<String>,
                            dependencies : NavigableSet<Identifier>,
                            cordappManifest: CordappManifest,
                            type : Type,
                            hash: SecureHash,
                            cordappCertificates : Set<Certificate>
            ) : Metadata = CpkMetadataImpl(
                mainBundle,
                manifest,
                libraries,
                dependencies,
                cordappManifest,
                type,
                hash,
                cordappCertificates
            )
        }
    }

    /**
     * Stores the metadata associated with this [Cpk] file
     */
    val metadata : Metadata

    /**
     * Path to cpk if it has been extracted or unpacked.  The filename
     * part of the path should not be depended on and probably won't be the
     * original name of the cpk.
     */
    val path : Path? get() = null

    /** File name of cpk, if known */
    val originalFileName : String? get() = null

    /**
     * Returns an [InputStream] with the content of the associated resource inside the [Cpk] archive
     * with the provided [resourceName] or null if a resource with that name doesn't exist.
     *
     * @param resourceName the name of the [Cpk] resource to be opened
     * @return an [InputStream] reading from the named resource
     * @throws [IOException] if a resource with the provided name is not found
     */
    @Throws(IOException::class)
    fun getResourceAsStream(resourceName : String) : InputStream
}
