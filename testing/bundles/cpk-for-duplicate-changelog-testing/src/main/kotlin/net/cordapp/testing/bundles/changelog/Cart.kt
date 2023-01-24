package net.cordapp.testing.bundles.changelog

import net.corda.v5.base.annotations.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

/** Moderately complex entity using a composite key and many-to-one relationship */
@CordaSerializable
@Entity
class Cart(
    @Id
    @Column
    val id: String,
    @Column
    val name: String,
    @Column
    val colour: String,
)
