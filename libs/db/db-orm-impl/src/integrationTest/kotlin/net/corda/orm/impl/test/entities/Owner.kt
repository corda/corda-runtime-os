package net.corda.orm.impl.test.entities

import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

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
