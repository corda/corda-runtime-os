package com.r3.corda.testing.bundles.fish

import net.corda.v5.base.annotations.CordaSerializable
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@CordaSerializable
@Entity
data class Owner(
    @get:Id
    @get:Column
    var id: UUID,

    @get:Column
    var name: String,

    @get:Column
    var age: Int
) {
    constructor() : this(id = UUID.randomUUID(), name = "anonymous", age = Int.MAX_VALUE)
}
