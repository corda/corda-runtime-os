package net.corda.cpk.write.impl.services.kafka

import net.corda.cpk.write.impl.CpkChunk

interface CpkChunkWriter {
    fun putAll(cpkChunks: List<CpkChunk>)
}