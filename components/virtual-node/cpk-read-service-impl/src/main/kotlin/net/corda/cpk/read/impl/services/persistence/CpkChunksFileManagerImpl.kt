package net.corda.cpk.read.impl.services.persistence

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.libs.packaging.setReadOnly
import net.corda.libs.packaging.writeFile
import net.corda.utilities.debug
import net.corda.utilities.posixOptional
import net.corda.v5.crypto.SecureHash
import org.slf4j.LoggerFactory
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import java.nio.file.attribute.PosixFilePermissions.asFileAttribute
import java.util.SortedSet
import java.util.function.Consumer
import net.corda.crypto.core.SecureHashImpl
import net.corda.data.crypto.SecureHash as AvroSecureHash

/**
 * Creates resources on disk, that something needs to remove on shutdown.
 */
class CpkChunksFileManagerImpl(private val cpkCacheDir: Path) : CpkChunksFileManager {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val DELIMITER = ".cpk.part."

        private val CPK_DIRECTORY_PERMISSIONS = asFileAttribute(setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE))
        private val CPK_FILE_PERMISSIONS = asFileAttribute(setOf(OWNER_READ, OWNER_WRITE))

        internal fun SecureHash.toCpkDirName() = toHexString()

        internal fun SecureHash.toCpkFileName() = "${toHexString()}.cpk"

        internal fun CpkChunkId.toFileName(): String {
            val cpkChecksum = cpkChecksum.toCorda().toHexString()
            val partNumber = cpkChunkPartNumber
            return "$cpkChecksum$DELIMITER$partNumber"
        }
        fun AvroSecureHash.toCorda(): SecureHash =
            SecureHashImpl(this.algorithm, this.bytes.array())
    }

    override fun chunkFileExists(chunkId: CpkChunkId): CpkChunkFileLookUp {
        val cpkXDir = cpkCacheDir.resolve(chunkId.cpkChecksum.toCorda().toCpkDirName())
        val filePath = cpkXDir.resolve(chunkId.toFileName())
        return CpkChunkFileLookUp(Files.isRegularFile(filePath), filePath)
    }

    override fun writeChunkFile(chunkId: CpkChunkId, chunk: Chunk) {
        logger.debug { "Writing CPK chunk file ${chunkId.toFileName()}" }
        val cpkXDir = cpkCacheDir.resolve(chunkId.cpkChecksum.toCorda().toCpkDirName())
        if (!Files.isDirectory(cpkXDir)) {
            // Try to create the directory. Will fail if this path
            // already exists as something other than a directory.
            logger.debug { "Creating CPK directory: $cpkXDir" }
            @Suppress("SpreadOperator")
            Files.createDirectory(cpkXDir, *cpkXDir.posixOptional(CPK_DIRECTORY_PERMISSIONS))
        }

        cpkXDir.resolve(chunkId.toFileName()).also { chunkFile ->
            if (!Files.isRegularFile(chunkFile)) {
                // Write the chunk's contents to an invisible temporary file, and only
                // move this file to its proper name once writing is complete.
                writeAtomically(chunkFile, ".chunk-") { output ->
                    output.write(chunk.data)
                }
            }
        }
    }

    // TODO need to take care of incomplete CPK assemble as per https://r3-cev.atlassian.net/browse/CORE-4155
    override fun assembleCpk(cpkChecksum: SecureHash, chunkParts: SortedSet<CpkChunkId>): Path? {
        val cpkXDir = cpkCacheDir.resolve(cpkChecksum.toCpkDirName())
        logger.info("Assembling CPK on disk: {}", cpkXDir)
        if (!Files.isDirectory(cpkXDir)) {
            logger.warn("CPK directory should exist but it does not: {}", cpkXDir)
            return null
        }

        return cpkXDir.resolve(cpkChecksum.toCpkFileName()).also { cpkFile ->
            if (!Files.isRegularFile(cpkFile)) {
                // Assemble the chunks into an invisible temporary file, and only
                // move this file to its proper name once writing is complete.
                writeAtomically(cpkFile, ".cpk-") { output ->
                    chunkParts.forEach { chunkId ->
                        val cpkChunkPath = cpkXDir.resolve(chunkId.toFileName())
                        FileChannel.open(cpkChunkPath, READ).use(output::writeFile)
                    }
                }
            }
        }
    }

    private fun writeAtomically(target: Path, tempPrefix: String, writer: Consumer<SeekableByteChannel>) {
        val directory = target.parent
        @Suppress("SpreadOperator")
        Files.createTempFile(directory, tempPrefix, "", *directory.posixOptional(CPK_FILE_PERMISSIONS)).also { tempFile ->
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
}
