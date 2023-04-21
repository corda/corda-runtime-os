package net.corda.chunking

import java.nio.file.Path

interface ChunkReaderFactory {
    fun create(destDir: Path): ChunkReader
}
