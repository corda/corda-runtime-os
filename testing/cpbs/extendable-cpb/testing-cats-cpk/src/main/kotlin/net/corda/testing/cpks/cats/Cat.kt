package net.corda.testing.cpks.cats

import net.corda.v5.base.annotations.CordaSerializable
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.JoinColumns
import javax.persistence.ManyToOne

@CordaSerializable
@Entity
data class Cat(
    @Id
    @Column
    val id: UUID,
    @Column
    val name: String,
    @Column
    val colour: String,

    @ManyToOne
    @JoinColumns(
        JoinColumn(name = "owner_id", referencedColumnName = "id")
    )
    val owner: Owner?
) {
    constructor() : this(id = UUID.randomUUID(), name = "", colour = "sort-of-spotty", owner = null)
}
