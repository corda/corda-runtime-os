package net.corda.packaging

import net.corda.packaging.Cpb.Companion.jarSignatureVerificationEnabledByDefault
import net.corda.packaging.internal.CpkDependencyResolver
import net.corda.packaging.internal.CpkLoader
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.nio.file.Path
import java.security.cert.Certificate
import java.util.Arrays
import java.util.NavigableMap
import java.util.NavigableSet
import java.util.SortedSet
import java.util.TreeMap
import java.util.TreeSet

/** Contains information on a [Cpk]. */
@Suppress("LongParameterList")
sealed class Cpk(
    val type: Type,
    val cpkHash: SecureHash,
    val cpkManifest: Manifest,
    val cordappJarFileName: String,
    val cordappHash: SecureHash,
    val cordappCertificates: Set<Certificate>,
    val cordappManifest: CordappManifest,
    val libraryDependencies: NavigableMap<String, SecureHash>,
    val dependencies: NavigableSet<Identifier>
) {

    val id = let {
        val signers: NavigableSet<SecureHash> =
            cordappCertificates.mapNotNullTo(TreeSet(Identifier.secureHashComparator)) { certificate ->
                certificate.publicKey?.encoded?.sha256()
            }
        Identifier(cordappManifest.bundleSymbolicName, cordappManifest.bundleVersion, signers)
    }

    companion object {
        const val fileExtension = ".cpk"

        @JvmStatic
        fun resolveDependencies(
            cpks: Iterable<Cpk>,
            useSignatures: Boolean = true
        ): NavigableSet<Identifier> {
            return CpkDependencyResolver.resolveDependencies(
                cpks.map { it.id },
                cpks.associateByTo(TreeMap(), Cpk::id, Cpk::dependencies), useSignatures
            )
        }
    }

    @Suppress("LongParameterList")
    class Archived(
        type: Type,
        cpkHash: SecureHash,
        cpkManifest: Manifest,
        cordappJarFileName: String,
        cordappHash: SecureHash,
        cordappCertificates: Set<Certificate>,
        cordappManifest: CordappManifest,
        libraryDependencies: NavigableMap<String, SecureHash>,
        dependencies: NavigableSet<Identifier>
    ) : Cpk(
        type,
        cpkHash,
        cpkManifest,
        cordappJarFileName,
        cordappHash,
        cordappCertificates,
        cordappManifest,
        libraryDependencies,
        dependencies
    ) {
        companion object {
            /**
             * Unpacks the CPK resource from [inputStream], and parses it as a [Cpk.Archived].
             * An optional [cpkLocation] parameter can be provided to improve error reporting
             * (the location will be eventually included in exception messages).
             * Throws [PackagingException] if a CPK doesn't contain exactly one CorDapp JAR at its root, doesn't contain
             * a manifest, or its dependencies cannot be found or are invalid. If the [verifySignature] parameter isn't
             * provided, its value is inferred according to the [Cpb.DEFAULT_VERIFY_JAR_SIGNATURES_KEY] system property
             */
            @JvmStatic
            @JvmOverloads
            fun from(
                source: InputStream,
                cpkLocation: String? = null,
                verifySignature: Boolean = jarSignatureVerificationEnabledByDefault()
            ) =
                CpkLoader.from(source, null, cpkLocation, verifySignature) as Archived
        }
    }

    @Suppress("LongParameterList")
    class Expanded(
        val mainJar: Path,
        val libraries: Set<Path>,
        type: Type,
        cpkHash: SecureHash,
        cpkManifest: Manifest,
        cordappJarFileName: String,
        cordappHash: SecureHash,
        cordappCertificates: Set<Certificate>,
        cordappManifest: CordappManifest,
        libraryDependencies: NavigableMap<String, SecureHash>,
        dependencies: NavigableSet<Identifier>
    ) : Cpk(
        type,
        cpkHash,
        cpkManifest,
        cordappJarFileName,
        cordappHash,
        cordappCertificates,
        cordappManifest,
        libraryDependencies,
        dependencies
    ) {
        companion object {
            /**
             * Unpacks the CPK resource from [inputStream], and parses it as a [Cpk.Expanded].
             * The archive is also expanded in [expansionLocation].
             * An optional [cpkLocation] parameter can be provided to improve error reporting
             * (the location will be eventually included in exception messages)
             * Throws [PackagingException] if a CPK doesn't contain exactly one CorDapp JAR at its root, doesn't contain
             * a manifest, or its dependencies cannot be found or are invalid. If the [verifySignature] parameter isn't
             * provided, its value is inferred according to the [Cpb.DEFAULT_VERIFY_JAR_SIGNATURES_KEY] system property
             */
            @JvmStatic
            @JvmOverloads
            fun from(
                source: InputStream,
                expansionLocation: Path,
                cpkLocation: String? = null,
                verifySignature: Boolean = jarSignatureVerificationEnabledByDefault()
            ) =
                CpkLoader.from(source, expansionLocation, cpkLocation, verifySignature) as Expanded
        }
    }

    /**
     * Identifies a CPK whose CorDapp JAR has the matching [symbolicName] and [version], and that is signed by the public
     * keys with the hashes [signers].
     */
    data class Identifier(
        val symbolicName: String,
        val version: String,
        val signers: NavigableSet<SecureHash>
    ) : Comparable<Identifier> {
        companion object {
            val secureHashComparator = Comparator<SecureHash?> { h1, h2 -> Arrays.compare(h1?.bytes, h2?.bytes) }
            private val signersComparator = Comparator<SortedSet<SecureHash>> { s1, s2 ->
                Arrays.compare(s1.toTypedArray(), s2.toTypedArray(), secureHashComparator)
            }
            private val identifierComparator = Comparator.comparing(Identifier::symbolicName)
                .thenComparing(Identifier::version, VersionComparator())
                .thenComparing(Identifier::signers, signersComparator)
        }

        override fun compareTo(other: Identifier): Int = identifierComparator.compare(this, other)
    }

    enum class Type constructor(private val text: String?) : Comparable<Type> {
        CORDA_API("corda-api"), UNKNOWN(null);

        companion object {
            private val map = values().associateBy(Type::text)

            @JvmStatic
            fun parse(text: String) = map[text.toLowerCase()] ?: UNKNOWN
        }
    }

    data class Manifest(val cpkFormatVersion: CpkFormatVersion) {

        /** The major and minor version of a CPK. */
        data class CpkFormatVersion(val major: Int, val minor: Int) : Comparable<CpkFormatVersion> {
            override fun toString() = "$major.$minor"
            override fun compareTo(other: CpkFormatVersion) = when (val majorDiff = major - other.major) {
                0 -> minor - other.minor
                else -> majorDiff
            }

            companion object {
                // A regex matching CPK format strings.
                private val CPK_VERSION_PATTERN = "(\\d++)\\.(\\d++)".toRegex()

                /**
                 * Parses the [cpkFormatVersion] into a [CpkFormatVersion].
                 *
                 * Throws [PackagingException] if the CPK format is missing or incorrectly specified.
                 */
                @JvmStatic
                fun parse(cpkFormatVersion: String): CpkFormatVersion {
                    val matches = CPK_VERSION_PATTERN.matchEntire(cpkFormatVersion)
                        ?: throw PackagingException("Does not match 'majorVersion.minorVersion': '$cpkFormatVersion'")
                    return CpkFormatVersion(matches.groupValues[1].toInt(), matches.groupValues[2].toInt())
                }
            }
        }

        companion object {
            const val CPK_FORMAT = "Corda-CPK-Format"
            const val CPK_TYPE = "Corda-CPK-Type"

            /**
             * Parses the [manifest] to extract CPK-specific information.
             *
             * Throws [PackagingException] if the CPK format is missing or incorrectly specified.
             */
            @JvmStatic
            fun fromManifest(manifest: java.util.jar.Manifest) = Manifest(
                cpkFormatVersion = parseFormatVersion(manifest)
            )

            /**
             * Parses the [manifest] to extract the CPK format.
             *
             * Throws [PackagingException] if the CPK format is missing or incorrectly specified.
             */
            @JvmStatic
            @Suppress("ThrowsCount")
            fun parseFormatVersion(manifest: java.util.jar.Manifest): CpkFormatVersion {
                val formatEntry = manifest.mainAttributes.getValue(CPK_FORMAT)
                    ?: throw PackagingException("CPK manifest does not specify a `$CPK_FORMAT` attribute.")
                return CpkFormatVersion.parse(formatEntry)
            }
        }
    }
}
