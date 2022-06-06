package net.corda.processors.db.internal.reconcile.db

import java.util.stream.Stream
import javax.persistence.EntityManager
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.libs.configuration.datamodel.findAllConfig
import net.corda.reconciliation.VersionedRecord

val getAllConfigDBVersionedRecords: (EntityManager) -> Stream<VersionedRecord<String, Configuration>> = { em ->
    em.findAllConfig().map { configEntity ->
        val config = Configuration(
            configEntity.config,
            configEntity.version.toString(),
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