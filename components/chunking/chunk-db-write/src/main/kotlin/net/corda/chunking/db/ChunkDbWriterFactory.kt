package net.corda.chunking.db

import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.libs.configuration.SmartConfig
import net.corda.virtualnode.rpcops.virtualNodeManagementSender.VirtualNodeManagementSender
import javax.persistence.EntityManagerFactory

interface ChunkDbWriterFactory {
    fun create(
        messagingConfig: SmartConfig,
        bootConfig: SmartConfig,
        entityManagerFactory: EntityManagerFactory,
        cpiInfoWriteService: CpiInfoWriteService,
        virtualNodeManagementSender: VirtualNodeManagementSender
    ): ChunkDbWriter
}
