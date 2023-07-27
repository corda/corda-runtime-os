package com.r3.corda.testing.bundles.changelog

import net.corda.v5.base.annotations.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

/** Moderately complex entity using a composite key and many-to-one relationship */
@CordaSerializable
@Entity
class Cart(
    @get:Id
    @get:Column
    var id: String,

    @get:Column
    var name: String,

    @get:Column
    var colour: String,
)
