package net.corda.libs.cpi.datamodel

object CpiEntities {
    val classes = setOf(
        CpiMetadataEntity::class.java,
        CpkDataEntity::class.java,
        CpkMetadataEntity::class.java,
        CpkDependencyEntity::class.java,
        CpkCordappManifestEntity::class.java
    )
}