package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.base.annotations.CordaSerializable
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@CordaSerializable
@Entity
data class Egg(
    @Id
    @Column
    val id: UUID,
    @Column
    val shape: String
)

