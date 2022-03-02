package net.corda.packaging.internal

import net.corda.packaging.CPK
import net.corda.packaging.CordappManifest
import net.corda.packaging.PackagingException
import net.corda.packaging.VersionComparator
import net.corda.v5.crypto.SecureHash
import java.io.IOException
import java.nio.file.Path
import java.security.cert.Certificate
import java.util.NavigableSet
import java.util.jar.JarFile

internal data class CPKIdentifierImpl(
    override val name: String,
    override val version: String,
    override val signerSummaryHash: SecureHash?) : CPK.Identifier {

    companion object {
        private val identifierComparator = Comparator.comparing(CPK.Identifier::name)
            .thenComparing(CPK.Identifier::version, VersionComparator())
            .thenComparing(CPK.Identifier::signerSummaryHash, secureHashComparator)
    }

    override fun compareTo(other: CPK.Identifier) = identifierComparator.compare(this, other)
}

@Suppress("LongParameterList")
internal data class CPKMetadataImpl(
    override val mainBundle: String,
    override val manifest : CPK.Manifest,
    override val libraries: List<String>,
    override val dependencies: NavigableSet<CPK.Identifier>,
    override val cordappManifest: CordappManifest,
    override val type: CPK.Type,
    override val hash: SecureHash,
    override val cordappCertificates : Set<Certificate>
) : CPK.Metadata {
    override val id: CPK.Identifier = CPKIdentifierImpl(
        name = cordappManifest.bundleSymbolicName,
        version = cordappManifest.bundleVersion,
        signerSummaryHash = cordappCertificates
            .asSequence()
            .certSummaryHash()
    )
}

internal class CPKImpl(
    override val metadata: CPK.Metadata,
    private val jarFile: JarFile,
    private val cpkPath: Path,
    private val cpkFileName: String?
) : CPK {
    override fun getResourceAsStream(resourceName: String) = jarFile.getJarEntry(resourceName)
        ?.let(jarFile::getInputStream)
        ?: throw IOException("Unknown resource $resourceName")

    override fun close() = jarFile.close()

    override val path: Path?
        get() = cpkPath

    override val originalFileName: String?
        get() = cpkFileName
}

internal data class CPKFormatVersionImpl(override val major: Int, override val minor: Int) : CPK.FormatVersion {
    override fun compareTo(other: CPK.FormatVersion) = when (val majorDiff = major - other.major) {
        0 -> minor - other.minor
        else -> majorDiff
    }

    override fun toString() = "$major.$minor"
}

internal data class CPKManifestImpl(override val cpkFormatVersion: CPK.FormatVersion) : CPK.Manifest {
    companion object {
        const val CPK_FORMAT = "Corda-CPK-Format"
        const val CPK_TYPE = "Corda-CPK-Type"

        fun parseFormatVersion(manifest: java.util.jar.Manifest): CPK.FormatVersion {
            val formatEntry = manifest.mainAttributes.getValue(CPK.Manifest.CPK_FORMAT)
                ?: throw PackagingException("CPK manifest does not specify a `${CPK.Manifest.CPK_FORMAT}` attribute.")
            return CPK.FormatVersion.parse(formatEntry)
        }
    }
}
