package net.corda.components.examples.persistence.config.admin

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "cluster_config")
data class ConfigState(
    @Id
    @Column
    val name: String,
    @Column
    val value: String,
    @Column
    val version: Int,
)
