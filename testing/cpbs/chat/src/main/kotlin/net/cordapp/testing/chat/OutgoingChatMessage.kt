package net.cordapp.testing.chat

import net.corda.v5.base.annotations.CordaSerializable
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

/**
 * An incoming chat message, one received by this member sent from another.
 */
@CordaSerializable
@Entity
data class OutgoingChatMessage(
    @Id
    @Column
    val id: UUID,
    @Column
    val recipient: String,
    @Column
    val message: String,
    @Column
    val timestamp: String
)
