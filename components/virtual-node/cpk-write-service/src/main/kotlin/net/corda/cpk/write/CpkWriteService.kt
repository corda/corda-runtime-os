package net.corda.cpk.write

import net.corda.cpk.write.types.CpkChunk
import net.corda.lifecycle.Lifecycle

interface CpkWriteService : Lifecycle {
    /**
     * Puts CPK chunks to kafka.
      */
    fun putAll(cpkChunks: List<CpkChunk>)
}
