package net.cordapp.testing.chat

import net.corda.v5.base.annotations.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.NamedQuery

@NamedQuery(
    name = "IncomingChatMessage.all",
    query = "select * from IncomingChatMessage"
)

/**
 * An incoming chat message, one received by this member sent from another.
 */
@CordaSerializable
@Entity
data class IncomingChatMessage(
    @Column
    val name: String,
    @Column
    val message: String
) {
    constructor() : this(name = "", message = "")
}
