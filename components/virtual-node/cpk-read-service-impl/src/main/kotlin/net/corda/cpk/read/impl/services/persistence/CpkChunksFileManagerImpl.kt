package net.corda.cpk.read.impl.services.persistence

import net.corda.chunking.toCorda
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Creates resources on disk, that something needs to clear them on shutdown.
 */
class CpkChunksFileManagerImpl(private val commonCpkCacheDir: Path) : CpkChunksFileManager {
    init {
        Files.createDirectories(commonCpkCacheDir)
    }

    companion object {
        val logger = contextLogger()

        private const val DELIMITER = ".cpk.part."

        internal fun CpkChunkId.toCpkDirName() =
            cpkChecksum.toCorda().toHexString()

        internal fun CpkChunkId.toFileName(): String {
            val cpkChecksum = cpkChecksum.toCorda().toHexString()
            val partNumber = cpkChunkPartNumber
            return "$cpkChecksum$DELIMITER$partNumber"
        }
    }

    override fun chunkFileExists(chunkId: CpkChunkId): Boolean {
        val cpkXDir = commonCpkCacheDir.resolve(chunkId.toCpkDirName())
        val filePath = cpkXDir.resolve(chunkId.toFileName())
        return Files.exists(filePath)
    }

    override fun writeChunkFile(chunkId: CpkChunkId, chunk: Chunk) {
        logger.debug { "Writing CPK chunk file ${chunkId.toFileName()}" }
        val cpkXDir = commonCpkCacheDir.resolve(chunkId.toCpkDirName())
        if (!Files.exists(cpkXDir)) {
            logger.debug { "Creating CPK directory: $cpkXDir" }
            Files.createDirectory(cpkXDir)
        }
        val filePath = cpkXDir.resolve(chunkId.toFileName())
        Files.newByteChannel(filePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use {
            it.position(0)
            it.write(chunk.data)
        }
    }

    override fun close() {
        // As Barry pointed out we need to make sure commonCpkCacheDir and contents get cleaned on shutdown.
        // However which component should do that is not clear to me at the moment.
    }
}