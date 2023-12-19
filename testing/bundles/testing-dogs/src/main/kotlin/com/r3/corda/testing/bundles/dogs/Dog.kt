package com.r3.corda.testing.bundles.dogs

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
    NamedQuery(name = "Dog.count", query = "SELECT COUNT(1) FROM Dog"),
    NamedQuery(name = "Dog.nullableParam", query = "SELECT d FROM Dog d WHERE :nullableParam is null"),
    NamedQuery(name = "Dog.nullableParamOrCondition", query = "SELECT d FROM Dog d WHERE :nullableParam is null OR d.name = :nullableParam")
)
data class Dog(
    @get:Id
    @get:Column
    var id: UUID,

    @Column
    var name: String,

    @Column
    var birthdate: Instant,

    @Column
    var owner: String?
) {
    constructor() : this(id = UUID.randomUUID(), name = "", birthdate = Instant.now(), owner = "")
}
