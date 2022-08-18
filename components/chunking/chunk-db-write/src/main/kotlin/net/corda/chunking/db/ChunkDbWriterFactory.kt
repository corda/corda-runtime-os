package net.corda.chunking.db

import javax.persistence.EntityManagerFactory
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.libs.configuration.SmartConfig
import net.corda.virtualnode.rpcops.common.VirtualNodeSender

interface ChunkDbWriterFactory {
    fun create(
        messagingConfig: SmartConfig,
        bootConfig: SmartConfig,
        entityManagerFactory: EntityManagerFactory,
        cpiInfoWriteService: CpiInfoWriteService,
        virtualNodeSender: VirtualNodeSender
    ): ChunkDbWriter
}
