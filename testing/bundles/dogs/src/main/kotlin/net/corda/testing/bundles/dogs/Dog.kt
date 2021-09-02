package net.corda.testing.bundles.dogs

import java.time.LocalDate
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@Entity
data class Dog(
    @Id
    @Column
    val id: UUID,
    @Column
    val name: String,
    @Column
    val birthdate: LocalDate
) {
    // empty constructor for hibernate
    constructor() : this(id = UUID.randomUUID(), name = "", birthdate = LocalDate.now())
}
