package net.corda.libs.configuration.write.persistent.impl

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * The entity for configuration entries in the cluster database.
 *
 * @param section The section of the configuration.
 * @param configuration The configuration in JSON or HOCON format.
 * @param version The version of the configuration.
 */
@Entity
@Table(name = DB_TABLE_CONFIG)
data class ConfigEntity(
    @Id
    @Column
    val section: String,
    // TODO - Joel - Currently defined as a VARCHAR(255) which makes no sense.
    @Column
    val configuration: String,
    // TODO - Joel - Mark this with the `@Version` annotation to enable optimistic locking.
    @Column
    val version: Int
)
