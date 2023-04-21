package net.corda.libs.cpi.datamodel

import net.corda.libs.cpi.datamodel.entities.CpiCpkEntity
import net.corda.libs.cpi.datamodel.entities.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.entities.internal.CpkDbChangeLogAuditEntity
import net.corda.libs.cpi.datamodel.entities.internal.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.entities.internal.CpkFileEntity
import net.corda.libs.cpi.datamodel.entities.CpkMetadataEntity

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