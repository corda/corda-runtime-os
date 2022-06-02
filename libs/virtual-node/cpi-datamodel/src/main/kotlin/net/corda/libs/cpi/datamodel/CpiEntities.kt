package net.corda.libs.cpi.datamodel

object CpiEntities {
    val classes = setOf(
        CpiMetadataEntity::class.java,
        CpkFileEntity::class.java,
        CpiCpkEntity::class.java,
        CpkMetadataEntity::class.java,
    )
}