package net.corda.cpk.read.impl.services.persistence

import net.corda.chunking.toCorda
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.TreeSet

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

        internal fun SecureHash.toCpkDirName() = toHexString()

        internal fun SecureHash.toCpkFileName() = "${toHexString()}.cpk"

        internal fun CpkChunkId.toFileName(): String {
            val cpkChecksum = cpkChecksum.toCorda().toHexString()
            val partNumber = cpkChunkPartNumber
            return "$cpkChecksum$DELIMITER$partNumber"
        }
    }

    override fun chunkFileExists(chunkId: CpkChunkId): CpkChunkFileLookUp {
        val cpkXDir = commonCpkCacheDir.resolve(chunkId.cpkChecksum.toCorda().toCpkDirName())
        val filePath = cpkXDir.resolve(chunkId.toFileName())
        return CpkChunkFileLookUp(Files.exists(filePath), filePath)
    }

    override fun writeChunkFile(chunkId: CpkChunkId, chunk: Chunk) {
        logger.debug { "Writing CPK chunk file ${chunkId.toFileName()}" }
        val cpkXDir = commonCpkCacheDir.resolve(chunkId.cpkChecksum.toCorda().toCpkDirName())
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

    override fun assembleCpk(cpkChecksum: SecureHash, chunkParts: TreeSet<CpkChunkId>): Path? {
        val cpkXDir = commonCpkCacheDir.resolve(cpkChecksum.toCpkDirName())
        logger.info("Assembling CPK on disk: $cpkXDir")
        if (!Files.exists(cpkXDir)) {
            logger.warn("CPK directory should exist but it does not: $cpkXDir")
            return null
        }

        val cpkFilePath = cpkXDir.resolve(cpkChecksum.toCpkFileName())
        Files.newByteChannel(cpkFilePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use { sinkChannel ->
            var offset = 0L
            chunkParts.forEach {
                val cpkChunkPath = cpkXDir.resolve(it.toFileName())
                // TODO is there a better way here to avoid loading all bytes to memory first?
                val bytes = Files.readAllBytes(cpkChunkPath)
                sinkChannel.position(offset)
                sinkChannel.write(ByteBuffer.wrap(bytes))
                offset += bytes.size
            }
        }
        return cpkFilePath
    }

    override fun close() {
        // As Barry pointed out we need to make sure commonCpkCacheDir and contents get cleaned on shutdown.
        // However which component should do that is not clear to me at the moment.
    }
}