package net.corda.cpi.upload.endpoints.common

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.RPCSender
import java.time.Duration

// I need CpiUploadManager to not have createAndStartRpcSender and setRpcRequestTimeout
interface CpiUploadManager : Lifecycle {

    fun setRpcSender(rpcSender: RPCSender<Chunk, ChunkAck>)

    fun setRpcRequestTimeout(timeoutMillis: Int)

    fun sendCpiChunk(chunk: Chunk): ChunkAck
}