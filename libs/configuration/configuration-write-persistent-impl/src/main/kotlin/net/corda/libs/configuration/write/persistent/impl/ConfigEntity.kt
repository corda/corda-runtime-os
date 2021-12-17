package net.corda.libs.configuration.write.persistent.impl

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Version

// TODO - Joel - Move liquibase scripts to corda-api repo.

/**
 * The entity for configuration entries in the cluster database.
 *
 * @param section The section of the configuration.
 * @param configuration The configuration in JSON or HOCON format.
 * @param version The version of the configuration. Used for optimistic locking.
 * @param updateTimestamp The last time this section of the configuration was changed.
 * @param updateActor The ID of the user that last updated this section of the configuration.
 */
@Entity
@Table(name = DB_TABLE_CONFIG)
data class ConfigEntity(
    @Id
    @Column(name = "section", nullable = false)
    val section: String,
    @Column(name = "config", nullable = false)
    val configuration: String,
    @Version
    @Column(name = "version", nullable = false)
    val version: Int,
    @Column(name = "update_ts", nullable = false)
    val updateTimestamp: Instant,
    @Column(name = "update_actor", nullable = false)
    val updateActor: String
)
