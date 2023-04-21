package net.corda.chunking.db

import javax.persistence.EntityManagerFactory
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.libs.configuration.SmartConfig

interface ChunkDbWriterFactory {
    fun create(
        messagingConfig: SmartConfig,
        bootConfig: SmartConfig,
        entityManagerFactory: EntityManagerFactory,
        cpiInfoWriteService: CpiInfoWriteService
    ): ChunkDbWriter
}
