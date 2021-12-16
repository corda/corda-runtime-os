package net.corda.libs.configuration.write.persistent.impl

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Version

/**
 * The entity for configuration entries in the cluster database.
 *
 * @param section The section of the configuration.
 * @param configuration The configuration in JSON or HOCON format.
 * @param version The version of the configuration. Used for optimistic locking.
 */
@Entity
@Table(name = DB_TABLE_CONFIG)
data class ConfigEntity(
    @Id
    @Column
    val section: String,
    @Column
    val configuration: String,
    @Version
    @Column
    val version: Int
)
