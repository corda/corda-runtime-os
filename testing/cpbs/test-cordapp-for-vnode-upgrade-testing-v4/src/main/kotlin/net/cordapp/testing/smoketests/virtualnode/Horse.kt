package net.cordapp.testing.smoketests.virtualnode

import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
@Entity
data class Horse(
    @Id
    @Column
    val id: UUID,
    @Column
    val name: String,
)

