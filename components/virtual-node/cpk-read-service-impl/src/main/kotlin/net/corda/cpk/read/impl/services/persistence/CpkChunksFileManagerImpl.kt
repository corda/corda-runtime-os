package net.corda.cpk.read.impl.services.persistence

import net.corda.chunking.toCorda
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.utilities.inputStream
import net.corda.utilities.outputStream
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.SecureHash
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.SortedSet

/**
 * Creates resources on disk, that something needs to clear them on shutdown.
 */
class CpkChunksFileManagerImpl(private val cpkCacheDir: Path) : CpkChunksFileManager {
    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

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
        val cpkXDir = cpkCacheDir.resolve(chunkId.cpkChecksum.toCorda().toCpkDirName())
        val filePath = cpkXDir.resolve(chunkId.toFileName())
        return CpkChunkFileLookUp(Files.exists(filePath), filePath)
    }

    override fun writeChunkFile(chunkId: CpkChunkId, chunk: Chunk) {
        logger.debug { "Writing CPK chunk file ${chunkId.toFileName()}" }
        val cpkXDir = cpkCacheDir.resolve(chunkId.cpkChecksum.toCorda().toCpkDirName())
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

    // TODO need to take care of incomplete CPK assemble as per https://r3-cev.atlassian.net/browse/CORE-4155
    override fun assembleCpk(cpkChecksum: SecureHash, chunkParts: SortedSet<CpkChunkId>): Path? {
        val cpkXDir = cpkCacheDir.resolve(cpkChecksum.toCpkDirName())
        logger.info("Assembling CPK on disk: $cpkXDir")
        if (!Files.exists(cpkXDir)) {
            logger.warn("CPK directory should exist but it does not: $cpkXDir")
            return null
        }

        val cpkFilePath = cpkXDir.resolve(cpkChecksum.toCpkFileName())
        cpkFilePath.outputStream(StandardOpenOption.WRITE, StandardOpenOption.CREATE).use { outStream ->
            chunkParts.forEach {
                val cpkChunkPath = cpkXDir.resolve(it.toFileName())
                cpkChunkPath.inputStream(StandardOpenOption.READ).use { inStream ->
                    inStream.copyTo(outStream)
                }
            }
        }
        return cpkFilePath
    }
}