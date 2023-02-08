package net.corda.libs.cpi.datamodel

import net.corda.libs.cpi.datamodel.entities.*

object CpiEntities {
    val classes = setOf(
        CpiMetadataEntity::class.java,
        CpkFileEntity::class.java,
        CpiCpkEntity::class.java,
        CpkMetadataEntity::class.java,
        CpkDbChangeLogEntity::class.java,
        CpkDbChangeLogAuditEntity::class.java
    )
}