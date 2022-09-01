package net.corda.chunking.db

import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.libs.configuration.SmartConfig
import net.corda.virtualnode.rpcops.common.VirtualNodeSender
import javax.persistence.EntityManagerFactory

interface ChunkDbWriterFactory {
    fun create(
        messagingConfig: SmartConfig,
        bootConfig: SmartConfig,
        entityManagerFactory: EntityManagerFactory,
        cpiInfoWriteService: CpiInfoWriteService,
        virtualNodeSender: VirtualNodeSender
    ): ChunkDbWriter
}
