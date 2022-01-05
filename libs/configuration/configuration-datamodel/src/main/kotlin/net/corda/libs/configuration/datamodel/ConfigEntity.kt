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
 * @param version The version number used for optimistic locking.
 * @param config The configuration in JSON or HOCON format.
 * @param configVersion The version of the configuration.
 * @param updateTimestamp The last time this section of the configuration was updated.
 * @param updateActor The ID of the user that last updated this section of the configuration.
 */
@Entity
@Table(name = CONFIG_DB_TABLE, schema = CONFIG)
data class ConfigEntity(
    @Id
    @Column(name = "section", nullable = false)
    val section: String,
    @Version
    @Column(name = "version", nullable = false)
    val version: Int = -1,
    @Column(name = "config", nullable = false)
    val config: String,
    @Column(name = "config_version", nullable = false)
    val configVersion: Int,
    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,
    @Column(name = "update_actor", nullable = false)
    val updateActor: String
) {
    constructor(
        section: String,
        version: Int,
        config: String,
        updateTimestamp: Instant,
        configVersion: Int,
        updateActor: String
    ) : this(section, version, config, configVersion, updateTimestamp, updateActor)
}