package net.corda.simulator.runtime.persistence

import net.corda.v5.base.annotations.CordaSerializable
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.NamedQuery
import javax.persistence.Table

@NamedQuery(name="Greetings.findAll", query="SELECT g FROM GreetingEntity g")

@CordaSerializable
@Entity
@Table(name="greetingentity")
data class GreetingEntity (
    @get:Id
    @get:Column(name="id")
    var id: UUID,

    @get:Column(name="greeting")
    var greeting: String
)