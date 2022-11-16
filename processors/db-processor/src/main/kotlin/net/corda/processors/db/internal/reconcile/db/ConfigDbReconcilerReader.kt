package net.corda.processors.db.internal.reconcile.db

import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.libs.configuration.datamodel.findAllConfig
import net.corda.reconciliation.VersionedRecord
import javax.persistence.EntityManager

val getAllConfigDBVersionedRecords: (EntityManager) -> List<VersionedRecord<String, Configuration>> = { em ->
    em.findAllConfig().map { configEntity ->
        val config = Configuration(
            configEntity.config,
            configEntity.config,
            configEntity.version,
            ConfigurationSchemaVersion(configEntity.schemaVersionMajor, configEntity.schemaVersionMinor)
        )
        object : VersionedRecord<String, Configuration> {
            override val version = configEntity.version
            override val isDeleted = configEntity.isDeleted
            override val key = configEntity.section
            override val value = config
        }
    }
}
