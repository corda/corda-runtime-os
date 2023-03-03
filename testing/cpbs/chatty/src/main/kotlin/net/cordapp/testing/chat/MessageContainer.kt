package net.cordapp.testing.chat

import net.corda.v5.base.annotations.CordaSerializable

/**
 * A simple container which holds a message and can be serialized by Corda.
 */
@CordaSerializable
data class MessageContainer(val message:String)
