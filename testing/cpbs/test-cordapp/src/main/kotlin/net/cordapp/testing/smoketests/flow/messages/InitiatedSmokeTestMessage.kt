package net.cordapp.testing.smoketests.flow.messages

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class InitiatedSmokeTestMessage(
    val message: String
)