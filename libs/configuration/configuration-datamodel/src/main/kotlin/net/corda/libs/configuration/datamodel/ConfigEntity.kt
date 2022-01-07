package net.corda.libs.configuration.datamodel

import net.corda.db.schema.DbSchema.CONFIG
import net.corda.db.schema.DbSchema.CONFIG_DB_TABLE
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Version

/**
 * The entity for the current cluster configuration in the cluster database.
 *
 * @param section The section of the configuration.
 * @param config The configuration in JSON or HOCON format.
 * @param schemaVersion The version of the configuration.
 * @param updateTimestamp The last time this section of the configuration was updated.
 * @param updateActor The ID of the user that last updated this section of the configuration.
 *
 * @property version The version number used for optimistic locking.
 */
@Entity
@Table(name = CONFIG_DB_TABLE, schema = CONFIG)
data class ConfigEntity(
    @Id
    @Column(name = "section", nullable = false)
    val section: String,
    @Column(name = "config", nullable = false)
    var config: String,
    @Column(name = "schema_version", nullable = false)
    var schemaVersion: Int,
    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,
    @Column(name = "update_actor", nullable = false)
    var updateActor: String
) {
    @Version
    @Column(name = "version", nullable = false)
    var version: Int = -1

    /** Updates the classes fields to match those of the [configEntity]. */
    fun update(configEntity: ConfigEntity) {
        config = configEntity.config
        schemaVersion = configEntity.schemaVersion
        updateTimestamp = configEntity.updateTimestamp
        updateActor = configEntity.updateActor
    }
}
