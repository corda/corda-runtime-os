package net.corda.cpk.read.impl.services.persistence

import java.nio.file.Path

data class CpkChunkFileLookUp(
    val exists: Boolean,
    val path: Path
)