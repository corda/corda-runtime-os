package net.corda.libs.cpiupload

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.RPCSender

interface CpiUploadManagerFactory {
    fun create(config: SmartConfig, rpcSender: RPCSender<Chunk, ChunkAck>): CpiUploadManager
}