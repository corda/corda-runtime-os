package net.corda.processors.db.internal.config.writer

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

// TODO - Joel - This needs to be in the corda-api repo, as the DB layout is part of the public API.

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
