package net.corda.chunking.db.impl.validation

import net.corda.v5.crypto.SecureHash
import java.nio.file.Path

/** Simple class containing information about the file produced from combining [Chunk] objects */
data class FileInfo(val name: String, val path: Path, val checksum: SecureHash)
