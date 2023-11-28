package net.corda.orm.impl.test.entities

import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@Entity
data class MutableEntity(
    @get:Id
    @get:Column
    var id: UUID,

    @get:Column
    var tag: String,
)
