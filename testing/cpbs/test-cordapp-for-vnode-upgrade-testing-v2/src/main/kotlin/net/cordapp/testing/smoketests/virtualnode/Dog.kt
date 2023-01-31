package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.base.annotations.CordaSerializable
import java.time.Instant
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.NamedQueries
import javax.persistence.NamedQuery

@CordaSerializable
@Entity
@NamedQueries(
    NamedQuery(name = "Dog.summon", query = "SELECT d FROM Dog d WHERE d.name = :name"),
    NamedQuery(name = "Dog.independent", query = "SELECT d FROM Dog d WHERE d.owner IS NULL"),
    NamedQuery(name = "Dog.summonLike", query = "SELECT d FROM Dog d WHERE d.name LIKE :name ORDER BY d.name"),
    NamedQuery(name = "Dog.all", query = "SELECT d FROM Dog d ORDER BY d.name"),
    NamedQuery(name = "Dog.release", query = "UPDATE Dog SET owner=null"),
    NamedQuery(name = "Dog.count", query = "SELECT COUNT(1) FROM Dog")
)
data class Dog(
    @Id
    @Column
    val id: UUID,
    @Column
    val name: String,
    @Column
    val birthdate: Instant,
    @Column
    val owner: String?
) {
    constructor() : this(id = UUID.randomUUID(), name = "", birthdate = Instant.now(), owner = "")
}

