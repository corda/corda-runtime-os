package net.corda.chunking

import net.corda.chunking.impl.ChunkReaderImpl
import java.nio.file.Path

object ChunkReaderFactoryImpl : ChunkReaderFactory {
    override fun create(destDir: Path): ChunkReader = ChunkReaderImpl(destDir)
}
