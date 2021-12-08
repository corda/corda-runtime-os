package net.corda.processors.db.internal.config

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = CONFIG_TABLE_NAME)
data class ConfigEntity(
    @Id
    @Column
    val name: String,
    @Column
    val value: String
    // TODO - Joel - Version number? Or is versioning achieved in another way?
)
