package net.cordapp.flowworker.development.messages

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class InitiatedSmokeTestMessage(
    val message: String
)