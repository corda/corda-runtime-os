package net.corda.components.examples.persistence.config.admin

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@Entity
data class ConfigState(
    @Id
    @Column
    val key: String,
    @Column
    val value: String,
)
