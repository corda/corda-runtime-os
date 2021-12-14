package net.corda.configuration.write.impl

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = DB_TABLE_CONFIG)
data class ConfigEntity(
    @Id
    @Column
    val name: String,
    @Column
    val value: String,
    // TODO - Joel - Mark this with the `@Version` annotation to enable optimistic locking.
    @Column
    val version: Int
)
