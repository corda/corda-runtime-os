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
    @Id
    @Column(name="id")
    val id: UUID,
    @Column(name="greeting")
    val greeting: String
)