package net.corda.processors.db.internal.config

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "config")
data class ConfigEntity(
    @Id
    @Column
    val name: String,
    @Column
    val value: String
)
