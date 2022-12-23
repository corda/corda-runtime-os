package net.corda.chunking.db

import net.corda.cpi.persistence.CpiPersistence
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.libs.configuration.SmartConfig
import javax.persistence.EntityManagerFactory

interface ChunkDbWriterFactory {
    fun create(
        messagingConfig: SmartConfig,
        bootConfig: SmartConfig,
        entityManagerFactory: EntityManagerFactory,
        cpiPersistence: CpiPersistence,
        cpiInfoWriteService: CpiInfoWriteService
    ): ChunkDbWriter
}
