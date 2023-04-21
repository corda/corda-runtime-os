package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.base.annotations.CordaSerializable
import java.util.UUID
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.JoinColumns
import javax.persistence.ManyToOne

/** Moderately complex entity using a composite key and many-to-one relationship */
@CordaSerializable
@Entity
@IdClass(FishKey::class)
data class Fish(
    @Id
    @Column
    val id: UUID,
    @Id
    @Column
    val name: String,
    @Column
    val colour: String,

    @ManyToOne(fetch= FetchType.EAGER, cascade = [CascadeType.ALL])
    @JoinColumns(
        JoinColumn(name = "owner_id", referencedColumnName = "id")
    )
    val owner: Owner?
) {
    constructor() : this(id = UUID.randomUUID(), name = "", colour = "sort-of-spotty", owner = null)
}
