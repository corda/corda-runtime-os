package net.corda.libs.packaging.internal.v2

import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.PackagingConstants.CPK_FORMAT_VERSION2_MAINBUNDLE_PLACEHOLDER
import net.corda.libs.packaging.PackagingConstants.CPK_LIB_FOLDER_V2
import net.corda.libs.packaging.PackagingConstants.CPK_NAME_ATTRIBUTE
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.hash
import net.corda.libs.packaging.internal.CpkImpl
import net.corda.libs.packaging.internal.CpkLoader
import net.corda.libs.packaging.internal.ExternalChannelsConfigLoader
import net.corda.libs.packaging.internal.ExternalChannelsConfigLoaderImpl
import net.corda.libs.packaging.internal.FormatVersionReader
import net.corda.libs.packaging.setReadOnly
import net.corda.libs.packaging.signerSummaryHashForRequiredSigners
import net.corda.utilities.posixOptional
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.crypto.DigestAlgorithmName
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import java.nio.file.attribute.PosixFilePermissions.asFileAttribute
import java.security.cert.Certificate
import java.util.Collections
import java.util.function.Consumer
import java.util.jar.JarInputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

internal const val CPK_TYPE = "Corda-CPK-Type"

class CpkLoaderV2(
    private val clock: Clock = UTCClock(),
    private val externalChannelsConfigLoader: ExternalChannelsConfigLoader = ExternalChannelsConfigLoaderImpl()
) : CpkLoader {
    private companion object {
        private val CPK_DIRECTORY_PERMISSIONS = asFileAttribute(setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE))
        private val CPK_FILE_PERMISSIONS = asFileAttribute(setOf(OWNER_READ, OWNER_WRITE))
        private val UNSIGNED = SecureHashImpl("", ByteArray(8))
        private val VERSION_2 = CpkFormatVersion(2, 0)
    }

    private fun writeAtomically(target: Path, writer: Consumer<SeekableByteChannel>): Path {
        val directory = target.parent
        @Suppress("SpreadOperator")
        return Files.createTempFile(directory, ".cpk-", "", *directory.posixOptional(CPK_FILE_PERMISSIONS)).let { tempFile ->
            try {
                Files.newByteChannel(tempFile, WRITE).use(writer::accept)
                setReadOnly(tempFile)

                // Rename our temporary file as the final step.
                Files.move(tempFile, target, ATOMIC_MOVE)
            } catch (e: Exception) {
                Files.delete(tempFile)
                throw e
            }
        }
    }

    private fun createCpkFile(source: ByteArray, cacheDir: Path?): Path {
        if (cacheDir == null) {
            throw IllegalStateException("cacheDir is null")
        }

        // Calculate file hash
        val hash = calculateFileHash(source)

        // Create cache dir
        @Suppress("SpreadOperator")
        Files.createDirectories(cacheDir, *cacheDir.posixOptional(CPK_DIRECTORY_PERMISSIONS))
        val cpkFile = cacheDir.resolve(hash.toHexString())
        return if (Files.isRegularFile(cpkFile)) {
            cpkFile
        } else {
            writeAtomically(cpkFile) { output ->
                output.write(ByteBuffer.wrap(source))
            }
        }
    }

    fun loadAsSyntheticContract(
        source: ByteArray,
        cacheDir: Path?,
        cpkFileName: String?
    ): Cpk {
        val cpkFile = createCpkFile(source, cacheDir)
        return CpkImpl(
            metadata = generateContractMetadata(source),
            jarFile = cpkFile.toFile(),
            verifySignature = true,
            path = cpkFile,
            originalFileName = cpkFileName
        )
    }

    private fun parseCPK(cpkBytes: ByteArray): Pair<Manifest, List<JarEntryAndBytes>> {
        return JarInputStream(cpkBytes.inputStream(), true).use { jar ->
            val manifest = jar.manifest ?: throw CordappManifestException("manifest must not be null")
            val jarEntries = readJar(jar)
            Pair(manifest, jarEntries)
        }
    }

    private fun generateContractMetadata(cpkBytes: ByteArray): CpkMetadata {
        val (manifest, cpkEntries) = parseCPK(cpkBytes)
        val cordappManifest = CordappManifest.generateContractManifest(manifest)

        // Calculate file hash
        val fileChecksum = calculateFileHash(cpkBytes)

        // Get code signers
        val cordappCertificates = readCodeSigners(cpkEntries)
        val signerSummaryHash = if (cordappCertificates.isEmpty()) {
            UNSIGNED
        } else {
            cordappCertificates.signerSummaryHashForRequiredSigners()
        }

        return CpkMetadata(
            cpkId = CpkIdentifier(
                cordappManifest.bundleSymbolicName,
                cordappManifest.bundleVersion,
                signerSummaryHash
            ),
            manifest = CpkManifest(VERSION_2),
            mainBundle = CPK_FORMAT_VERSION2_MAINBUNDLE_PLACEHOLDER,
            fileChecksum = fileChecksum,
            type = CpkType.SYNTHETIC,
            cordappManifest = cordappManifest,
            cordappCertificates = cordappCertificates,
            libraries = emptyList(),
            timestamp = clock.instant(),
            externalChannelsConfig = null
        )
    }

    override fun loadCPK(
        source: ByteArray,
        cacheDir: Path?,
        cpkLocation: String?,
        verifySignature: Boolean,
        cpkFileName: String?
    ): Cpk {
        val finalCpkFile = createCpkFile(source, cacheDir)
        return CpkImpl(
            metadata = readCpkMetadata(source),
            jarFile = finalCpkFile.toFile(),
            verifySignature = false,
            path = finalCpkFile,
            originalFileName = cpkFileName
        )
    }

    override fun loadMetadata(source: ByteArray, cpkLocation: String?, verifySignature: Boolean): CpkMetadata =
        readCpkMetadata(source)

    private fun readCpkMetadata(cpkBytes: ByteArray): CpkMetadata {
        val (manifest, cpkEntries) = parseCPK(cpkBytes)

        // Read manifest
        val cordappManifest = CordappManifest.fromManifest(manifest)
        val cpkManifest = CpkManifest(FormatVersionReader.readCpkFormatVersion(Manifest(manifest)))
        val cpkType = manifest.mainAttributes.getValue(CPK_TYPE)?.let(CpkType::parse) ?: CpkType.UNKNOWN

        // Calculate file hash
        val fileChecksum = calculateFileHash(cpkBytes)

        // Get code signers
        val cordappCertificates = readCodeSigners(cpkEntries)
        val signerSummaryHash = cordappCertificates.signerSummaryHashForRequiredSigners()

        // List all libraries
        val libNames = readLibNames(cpkEntries)

        // Read the configuration for the external channels
        val externalChannelsConfig = externalChannelsConfigLoader.read(cpkEntries)

        // Corda-CPK-Cordapp-Name
        val cordaCpkName = cordappManifest.attributes[CPK_NAME_ATTRIBUTE] ?: cordappManifest.bundleSymbolicName

        return CpkMetadata(
            cpkId = CpkIdentifier(
                cordaCpkName,
                cordappManifest.bundleVersion,
                signerSummaryHash
            ),
            type = cpkType,
            manifest = cpkManifest,
            mainBundle = CPK_FORMAT_VERSION2_MAINBUNDLE_PLACEHOLDER,
            fileChecksum = fileChecksum,
            cordappManifest = cordappManifest,
            cordappCertificates = cordappCertificates,
            libraries = Collections.unmodifiableList(libNames),
            timestamp = clock.instant(),
            externalChannelsConfig = externalChannelsConfig
        )
    }

    private fun calculateFileHash(bytes: ByteArray) = bytes.hash(DigestAlgorithmName.SHA2_256)

    private fun readLibNames(jarEntryAndBytes: List<JarEntryAndBytes>) =
        jarEntryAndBytes
            .asSequence()
            .map(JarEntryAndBytes::entry)
            .filter { it.name.startsWith(CPK_LIB_FOLDER_V2) }
            .map(ZipEntry::getName)
            .toList()

    private fun readCodeSigners(jarEntryAndBytes: List<JarEntryAndBytes>): Set<Certificate> =
        jarEntryAndBytes
            .asSequence()
            .map(JarEntryAndBytes::entry)
            .first(SignatureCollector::isSignable)
            .codeSigners
            ?.mapTo(linkedSetOf()) { it.signerCertPath.certificates.first() }
            ?: emptySet()
}
