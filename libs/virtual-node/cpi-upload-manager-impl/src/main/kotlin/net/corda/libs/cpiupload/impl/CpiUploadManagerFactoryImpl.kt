package net.corda.libs.cpiupload.impl

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.libs.cpiupload.CpiUploadManagerFactory
import net.corda.messaging.api.publisher.RPCSender
import org.osgi.service.component.annotations.Component

@Component(service = [CpiUploadManagerFactory::class])
class CpiUploadManagerFactoryImpl : CpiUploadManagerFactory {
    override fun create(config: SmartConfig, rpcSender: RPCSender<Chunk, ChunkAck>): CpiUploadManager {
        return CpiUploadManagerImpl(config, rpcSender)
    }
}