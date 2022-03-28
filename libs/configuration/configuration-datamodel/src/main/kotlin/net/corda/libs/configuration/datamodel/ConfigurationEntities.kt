package net.corda.libs.configuration.datamodel

object ConfigurationEntities {
    val classes = setOf(
        ConfigAuditEntity::class.java,
        ConfigEntity::class.java,
        DbConnectionAudit::class.java,
        DbConnectionConfig::class.java,
    )
}
