package net.corda.libs.packaging.internal.v2

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.PackagingConstants.CPK_DEPENDENCIES_FILE_ENTRY_V2
import net.corda.libs.packaging.PackagingConstants.CPK_DEPENDENCIES_FORMAT_VERSION2
import net.corda.libs.packaging.certSummaryHash
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.libs.packaging.core.exception.UnknownFormatVersionException
import net.corda.libs.packaging.hash
import net.corda.libs.packaging.internal.CpkImpl
import net.corda.libs.packaging.internal.CpkLoader
import net.corda.libs.packaging.internal.FormatVersionReader
import net.corda.libs.packaging.internal.v1.CpkLoaderV1
import net.corda.libs.packaging.internal.v1.SignatureCollector
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.crypto.DigestAlgorithmName
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.Certificate
import java.util.Collections
import java.util.jar.JarInputStream
import java.util.jar.Manifest

private const val LIB_FOLDER = "META-INF/privatelib/"

class CpkLoaderV2(private val clock: Clock = UTCClock()) : CpkLoader {

    override fun loadCPK(
        source: ByteArray,
        cacheDir: Path?,
        cpkLocation: String?,
        verifySignature: Boolean,
        cpkFileName: String?,
    ): Cpk {

        if (cacheDir == null) throw IllegalStateException("cacheDir is null")

        // Calculate file hash
        val hash = calculateFileHash(source)

        // Create cache dir
        Files.createDirectories(cacheDir)
        val finalCpkFile = cacheDir.parent.resolve(hash.toHexString()).toFile()
        finalCpkFile.writeBytes(source)

        return CpkImpl(
            metadata = readCpkMetadata(source),
            jarFile = finalCpkFile,
            verifySignature = false,
            path = finalCpkFile.toPath(),
            originalFileName = cpkFileName
        )
    }

    override fun loadMetadata(source: ByteArray, cpkLocation: String?, verifySignature: Boolean): CpkMetadata =
        readCpkMetadata(source)

    private fun readCpkMetadata(cpkBytes: ByteArray): CpkMetadata {

        val (manifest, cpkEntries) = JarInputStream(cpkBytes.inputStream(), false).use {
            val manifest = it.manifest
            val jarEntries = readJar(it).toList()
            Pair(manifest, jarEntries)
        }

        // Read manifest
        val cordappManifest = CordappManifest.fromManifest(manifest)
        val cpkManifest = CpkManifest(FormatVersionReader.readCpkFormatVersion(Manifest(manifest)))
        val cpkType = manifest.mainAttributes.getValue(CpkLoaderV1.CPK_TYPE)?.let { CpkType.parse(it) } ?: CpkType.UNKNOWN

        // Calculate file hash
        val fileChecksum = calculateFileHash(cpkBytes)

        // Get code signers
        val cordappCertificates = readCodeSigners(cpkEntries)
        val signerSummaryHash = cordappCertificates.asSequence().certSummaryHash()

        // List all libraries
        val libNames = readLibNames(cpkEntries)

        // Read CPK dependencies
        val cpkDependenciesBytes: ByteArray = readCpkDependencies(cpkEntries)
        val jacksonObjectMapper = jacksonObjectMapper()
        val cpkDependenciesFormatVersion = jacksonObjectMapper.readValue(
            cpkDependenciesBytes,
            CPKDependencyFormatVersion::class.java)
        val cpkDependencies = when (cpkDependenciesFormatVersion.formatVersion) {
            CPK_DEPENDENCIES_FORMAT_VERSION2 -> jacksonObjectMapper.readValue(
                cpkDependenciesBytes,
                CPKDependencyFileV2::class.java
            )
            else -> throw UnknownFormatVersionException("$CPK_DEPENDENCIES_FILE_ENTRY_V2 has an unknown " +
                    "format version \"${cpkDependenciesFormatVersion.formatVersion}\"")
        }

        return CpkMetadata(
            cpkId = CpkIdentifier(
                cordappManifest.bundleSymbolicName,
                cordappManifest.bundleVersion,
                signerSummaryHash
            ),
            type = cpkType,
            manifest = cpkManifest,
            mainBundle = ".",
            fileChecksum = fileChecksum,
            cordappManifest = cordappManifest,
            cordappCertificates = cordappCertificates,
            libraries = Collections.unmodifiableList(libNames),
            dependencies = cpkDependencies.dependencies.map { CpkIdentifier(
                it.name,
                it.version,
                if (it.verifySameSignerAsMe) signerSummaryHash else null
            ) }
                .toList(), // Add file hash option
            timestamp = clock.instant()
        )
    }

    private fun calculateFileHash(bytes: ByteArray) = bytes.hash(DigestAlgorithmName.SHA2_256)

    private fun readCpkDependencies(bytes: List<JarEntryAndBytes>): ByteArray =
        bytes.single {it.entry.name == CPK_DEPENDENCIES_FILE_ENTRY_V2}
            .bytes

    private fun readLibNames(jarEntryAndBytes: List<JarEntryAndBytes>) =
        jarEntryAndBytes
            .asSequence()
            .map { it.entry }
            .filter { it.name.startsWith(LIB_FOLDER) }
            .map { it.name }
            .toList()

    private fun readCodeSigners(jarEntryAndBytes: List<JarEntryAndBytes>): Set<Certificate> =
        jarEntryAndBytes
            .asSequence()
            .map { it.entry }
            .first { SignatureCollector.isSignable(it) }
            .codeSigners
            ?.map { it.signerCertPath.certificates.first() }
            ?.toSet()
            ?: emptySet()

    @JsonIgnoreProperties(ignoreUnknown = true)
    private class CPKDependencyFormatVersion(val formatVersion: String)
    @JsonIgnoreProperties("formatVersion")
    private class CPKDependencyFileV2(val dependencies: Array<CPKDependencyV2>)

    private data class CPKDependencyV2(
        val name: String,
        val version: String,
        val type: String?,
        val verifySameSignerAsMe: Boolean = false,
        val verifyFileHash: VerifyFileHashV2?,
    )
    private data class VerifyFileHashV2(
        val algorithm: String,
        val fileHash: String)

}