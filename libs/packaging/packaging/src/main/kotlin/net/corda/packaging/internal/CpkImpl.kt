package net.corda.packaging.internal

import net.corda.packaging.Cpk
import net.corda.packaging.CordappManifest
import net.corda.packaging.PackagingException
import net.corda.packaging.VersionComparator
import net.corda.v5.crypto.SecureHash
import java.io.IOException
import java.nio.file.Path
import java.security.cert.Certificate
import java.util.NavigableSet
import java.util.jar.JarFile

internal data class CpkIdentifierImpl(
    override val name: String,
    override val version: String,
    override val signerSummaryHash: SecureHash?) : Cpk.Identifier {

    companion object {
        private val identifierComparator = Comparator.comparing(Cpk.Identifier::name)
            .thenComparing(Cpk.Identifier::version, VersionComparator())
            .thenComparing(Cpk.Identifier::signerSummaryHash, secureHashComparator)
    }

    override fun compareTo(other: Cpk.Identifier) = identifierComparator.compare(this, other)
}

@Suppress("LongParameterList")
internal data class CpkMetadataImpl(
    override val mainBundle: String,
    override val manifest : Cpk.Manifest,
    override val libraries: List<String>,
    override val dependencies: NavigableSet<Cpk.Identifier>,
    override val cordappManifest: CordappManifest,
    override val type: Cpk.Type,
    override val hash: SecureHash,
    override val cordappCertificates : Set<Certificate>
) : Cpk.Metadata {
    override val id: Cpk.Identifier = CpkIdentifierImpl(
        name = cordappManifest.bundleSymbolicName,
        version = cordappManifest.bundleVersion,
        signerSummaryHash = cordappCertificates
            .asSequence()
            .certSummaryHash()
    )
}

internal class CpkImpl(
    override val metadata: Cpk.Metadata,
    private val jarFile: JarFile,
    private val cpkPath: Path,
    private val cpkFileName: String?
) : Cpk {
    override fun getResourceAsStream(resourceName: String) = jarFile.getJarEntry(resourceName)
        ?.let(jarFile::getInputStream)
        ?: throw IOException("Unknown resource $resourceName")

    override fun close() = jarFile.close()

    override val path: Path?
        get() = cpkPath

    override val originalFileName: String?
        get() = cpkFileName
}

internal data class CpkFormatVersionImpl(override val major: Int, override val minor: Int) : Cpk.FormatVersion {
    override fun compareTo(other: Cpk.FormatVersion) = when (val majorDiff = major - other.major) {
        0 -> minor - other.minor
        else -> majorDiff
    }

    override fun toString() = "$major.$minor"
}

internal data class CpkManifestImpl(override val cpkFormatVersion: Cpk.FormatVersion) : Cpk.Manifest {
    companion object {
        const val CPK_FORMAT = "Corda-CPK-Format"
        const val CPK_TYPE = "Corda-CPK-Type"

        fun parseFormatVersion(manifest: java.util.jar.Manifest): Cpk.FormatVersion {
            val formatEntry = manifest.mainAttributes.getValue(Cpk.Manifest.CPK_FORMAT)
                ?: throw PackagingException("CPK manifest does not specify a `${Cpk.Manifest.CPK_FORMAT}` attribute.")
            return Cpk.FormatVersion.parse(formatEntry)
        }
    }
}
