package net.corda.libs.configuration.write.persistent.impl.entities

import net.corda.libs.configuration.write.persistent.impl.DB_TABLE_CONFIG
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.PrePersist
import javax.persistence.PreUpdate
import javax.persistence.Table
import javax.persistence.Version

// TODO - Joel - Move liquibase scripts to corda-api repo.

/**
 * The entity for the current cluster configuration in the cluster database.
 *
 * @param section The section of the configuration.
 * @param version The version of the configuration. Used for optimistic locking.
 * @param configuration The configuration in JSON or HOCON format.
 * @param updateTimestamp The last time this section of the configuration was updated.
 * @param updateActor The ID of the user that last updated this section of the configuration.
 */
@Entity
@Table(name = DB_TABLE_CONFIG)
internal class ConfigEntity(
    @Id
    @Column(name = "section", nullable = false)
    val section: String,
    @Version
    @Column(name = "version", nullable = false)
    val version: Int = -1,
    @Column(name = "config", nullable = false)
    val configuration: String,
    @Column(name = "update_ts", nullable = false)
    var updateTimestamp: Instant,
    @Column(name = "update_actor", nullable = false)
    val updateActor: String
) {
    constructor(section: String, version: Int, configuration: String, updateActor: String) :
            this(section, version, configuration, Instant.MIN, updateActor)

    /** Sets [updateTimestamp] to the current time. */
    @Suppress("Unused")
    @PrePersist
    @PreUpdate
    private fun onCreate() {
        updateTimestamp = Instant.now()
    }
}
