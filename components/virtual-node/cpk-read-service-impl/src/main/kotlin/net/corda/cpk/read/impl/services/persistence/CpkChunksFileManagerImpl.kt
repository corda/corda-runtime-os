package net.corda.cpk.read.impl.services.persistence

import net.corda.crypto.core.toCorda
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.libs.packaging.writeFile
import net.corda.utilities.debug
import net.corda.v5.crypto.SecureHash
import org.slf4j.LoggerFactory
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import java.nio.file.attribute.PosixFilePermissions.asFileAttribute
import java.util.SortedSet

/**
 * Creates resources on disk, that something needs to clear them on shutdown.
 */
class CpkChunksFileManagerImpl(private val cpkCacheDir: Path) : CpkChunksFileManager {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val DELIMITER = ".cpk.part."

        private val CPK_DIRECTORY_PERMISSIONS = asFileAttribute(setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE))
        private val CPK_FILE_PERMISSIONS = asFileAttribute(setOf(OWNER_READ, OWNER_WRITE))
        private val CREATE_OR_REPLACE = setOf(CREATE, WRITE, TRUNCATE_EXISTING)

        internal fun SecureHash.toCpkDirName() = toHexString()

        internal fun SecureHash.toCpkFileName() = "${toHexString()}.cpk"

        internal fun CpkChunkId.toFileName(): String {
            val cpkChecksum = cpkChecksum.toCorda().toHexString()
            val partNumber = cpkChunkPartNumber
            return "$cpkChecksum$DELIMITER$partNumber"
        }
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
            Files.createDirectory(cpkXDir, CPK_DIRECTORY_PERMISSIONS)
        }
        val filePath = cpkXDir.resolve(chunkId.toFileName())
        Files.newByteChannel(filePath, CREATE_OR_REPLACE, CPK_FILE_PERMISSIONS).use { channel ->
            channel.write(chunk.data)
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

        return cpkXDir.resolve(cpkChecksum.toCpkFileName()).also { cpkFilePath ->
            Files.newByteChannel(cpkFilePath, CREATE_OR_REPLACE, CPK_FILE_PERMISSIONS).use { output ->
                chunkParts.forEach { chunkId ->
                    val cpkChunkPath = cpkXDir.resolve(chunkId.toFileName())
                    FileChannel.open(cpkChunkPath, READ).use(output::writeFile)
                }
            }
        }
    }
}
