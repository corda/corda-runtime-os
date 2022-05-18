package net.corda.testing.bundles.dogs

import net.corda.v5.base.annotations.CordaSerializable
import java.time.Instant
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@CordaSerializable
@Entity
data class Dog(
    @Id
    @Column
    val id: UUID,
    @Column
    val name: String,
    @Column
    val birthdate: Instant,
    @Column
    val owner: String
) {
    constructor() : this(id = UUID.randomUUID(), name = "", birthdate = Instant.now(), owner = "")
}
