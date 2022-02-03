package net.corda.libs.cpiupload

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.lifecycle.Lifecycle

interface CpiUploadManager : Lifecycle {
    fun sendCpiChunk(chunk: Chunk): ChunkAck
}